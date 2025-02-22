package me.ash.reader.ui.page.home.feeds

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.ash.reader.data.entity.Account
import me.ash.reader.data.entity.Filter
import me.ash.reader.data.entity.GroupWithFeed
import me.ash.reader.data.repository.AccountRepository
import me.ash.reader.data.repository.OpmlRepository
import me.ash.reader.data.repository.RssRepository
import me.ash.reader.ui.page.home.FilterState
import javax.inject.Inject

@HiltViewModel
class FeedsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val rssRepository: RssRepository,
    private val opmlRepository: OpmlRepository,
) : ViewModel() {
    private val _viewState = MutableStateFlow(FeedsViewState())
    val viewState: StateFlow<FeedsViewState> = _viewState.asStateFlow()

    fun dispatch(action: FeedsViewAction) {
        when (action) {
            is FeedsViewAction.FetchAccount -> fetchAccount()
            is FeedsViewAction.FetchData -> fetchData(action.filterState)
            is FeedsViewAction.ExportAsString -> exportAsOpml(action.callback)
            is FeedsViewAction.ScrollToItem -> scrollToItem(action.index)
        }
    }

    private fun fetchAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            _viewState.update {
                it.copy(
                    account = accountRepository.getCurrentAccount()
                )
            }
        }
    }

    private fun exportAsOpml(callback: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                callback(opmlRepository.saveToString())
            } catch (e: Exception) {
                Log.e("FeedsViewModel", "exportAsOpml: ", e)
            }
        }
    }

    private fun fetchData(filterState: FilterState) {
        viewModelScope.launch(Dispatchers.IO) {
            pullFeeds(
                isStarred = filterState.filter.isStarred(),
                isUnread = filterState.filter.isUnread(),
            )
        }
    }

    private suspend fun pullFeeds(isStarred: Boolean, isUnread: Boolean) {
        combine(
            rssRepository.get().pullFeeds(),
            rssRepository.get().pullImportant(isStarred, isUnread),
        ) { groupWithFeedList, importantList ->
            val groupImportantMap = mutableMapOf<String, Int>()
            val feedImportantMap = mutableMapOf<String, Int>()
            importantList.groupBy { it.groupId }.forEach { (i, list) ->
                var groupImportantSum = 0
                list.forEach {
                    feedImportantMap[it.feedId] = it.important
                    groupImportantSum += it.important
                }
                groupImportantMap[i] = groupImportantSum
            }
            val groupsIt = groupWithFeedList.iterator()
            while (groupsIt.hasNext()) {
                val groupWithFeed = groupsIt.next()
                val groupImportant = groupImportantMap[groupWithFeed.group.id]
                if (groupImportant == null && (isStarred || isUnread)) {
                    groupsIt.remove()
                } else {
                    groupWithFeed.group.important = groupImportant
                    val feedsIt = groupWithFeed.feeds.iterator()
                    while (feedsIt.hasNext()) {
                        val feed = feedsIt.next()
                        val feedImportant = feedImportantMap[feed.id]
                        if (feedImportant == null && (isStarred || isUnread)) {
                            feedsIt.remove()
                        } else {
                            feed.important = feedImportant
                        }
                    }
                }
            }
            groupWithFeedList
        }.onStart {

        }.onEach { groupWithFeedList ->
            _viewState.update {
                it.copy(
                    filter = when {
                        isStarred -> Filter.Starred
                        isUnread -> Filter.Unread
                        else -> Filter.All
                    }.apply {
                        important = groupWithFeedList.sumOf { it.group.important ?: 0 }
                    },
                    groupWithFeedList = groupWithFeedList,
                    feedsVisible = List(groupWithFeedList.size, init = { true })
                )
            }
        }.catch {
            Log.e("RLog", "catch in articleRepository.pullFeeds(): $this")
        }.flowOn(Dispatchers.Default).collect()
    }

    private fun scrollToItem(index: Int) {
        viewModelScope.launch {
            _viewState.value.listState.scrollToItem(index)
        }
    }
}

data class FeedsViewState(
    val account: Account? = null,
    val filter: Filter = Filter.All,
    val groupWithFeedList: List<GroupWithFeed> = emptyList(),
    val feedsVisible: List<Boolean> = emptyList(),
    val listState: LazyListState = LazyListState(),
    val groupsVisible: Boolean = true,
)

sealed class FeedsViewAction {
    data class FetchData(
        val filterState: FilterState,
    ) : FeedsViewAction()

    object FetchAccount : FeedsViewAction()

    data class ExportAsString(
        val callback: (String) -> Unit = {}
    ) : FeedsViewAction()

    data class ScrollToItem(
        val index: Int
    ) : FeedsViewAction()
}