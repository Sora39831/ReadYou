package me.ash.reader.ui.page.home.drawer.feed

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.pager.ExperimentalPagerApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.data.entity.Feed
import me.ash.reader.data.entity.Group
import me.ash.reader.data.repository.RssRepository
import javax.inject.Inject

@OptIn(
    ExperimentalPagerApi::class,
    ExperimentalMaterialApi::class
)
@HiltViewModel
class FeedOptionViewModel @Inject constructor(
    private val rssRepository: RssRepository,
) : ViewModel() {
    private val _viewState = MutableStateFlow(FeedOptionViewState())
    val viewState: StateFlow<FeedOptionViewState> = _viewState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            rssRepository.get().pullGroups().collect { groups ->
                _viewState.update {
                    it.copy(
                        groups = groups
                    )
                }
            }
        }
    }

    fun dispatch(action: FeedOptionViewAction) {
        when (action) {
            is FeedOptionViewAction.Show -> show(action.scope, action.feedId)
            is FeedOptionViewAction.Hide -> hide(action.scope)
            is FeedOptionViewAction.SelectedGroup -> selectedGroup(action.groupId)
            is FeedOptionViewAction.InputNewGroup -> inputNewGroup(action.content)
            is FeedOptionViewAction.ChangeAllowNotificationPreset -> changeAllowNotificationPreset()
            is FeedOptionViewAction.ChangeParseFullContentPreset -> changeParseFullContentPreset()
            is FeedOptionViewAction.ShowDeleteDialog -> showDeleteDialog()
            is FeedOptionViewAction.HideDeleteDialog -> hideDeleteDialog()
            is FeedOptionViewAction.Delete -> delete(action.callback)
            is FeedOptionViewAction.AddNewGroup -> addNewGroup()
            is FeedOptionViewAction.ShowNewGroupDialog -> changeNewGroupDialogVisible(true)
            is FeedOptionViewAction.HideNewGroupDialog -> changeNewGroupDialogVisible(false)
            is FeedOptionViewAction.InputNewName -> inputNewName(action.content)
            is FeedOptionViewAction.Rename -> rename()
            is FeedOptionViewAction.ShowRenameDialog -> changeRenameDialogVisible(true)
            is FeedOptionViewAction.HideRenameDialog -> changeRenameDialogVisible(false)
        }
    }

    private suspend fun fetchFeed(feedId: String) {
        val feed = rssRepository.get().findFeedById(feedId)
        _viewState.update {
            it.copy(
                feed = feed,
                selectedGroupId = feed?.groupId ?: "",
            )
        }
    }

    private fun show(scope: CoroutineScope, feedId: String) {
        scope.launch {
            fetchFeed(feedId)
            _viewState.value.drawerState.show()
        }
    }

    private fun hide(scope: CoroutineScope) {
        scope.launch {
            _viewState.value.drawerState.hide()
        }
    }

    private fun changeNewGroupDialogVisible(visible: Boolean) {
        _viewState.update {
            it.copy(
                newGroupDialogVisible = visible,
                newGroupContent = "",
            )
        }
    }

    private fun inputNewGroup(content: String) {
        _viewState.update {
            it.copy(
                newGroupContent = content
            )
        }
    }

    private fun addNewGroup() {
        if (_viewState.value.newGroupContent.isNotBlank()) {
            viewModelScope.launch {
                selectedGroup(rssRepository.get().addGroup(_viewState.value.newGroupContent))
                changeNewGroupDialogVisible(false)
            }
        }
    }

    private fun selectedGroup(groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _viewState.value.feed?.let {
                rssRepository.get().updateFeed(
                    it.copy(
                        groupId = groupId
                    )
                )
                fetchFeed(it.id)
            }
        }
    }

    private fun changeParseFullContentPreset() {
        viewModelScope.launch(Dispatchers.IO) {
            _viewState.value.feed?.let {
                rssRepository.get().updateFeed(
                    it.copy(
                        isFullContent = !it.isFullContent
                    )
                )
                fetchFeed(it.id)
            }
        }
    }

    private fun changeAllowNotificationPreset() {
        viewModelScope.launch(Dispatchers.IO) {
            _viewState.value.feed?.let {
                rssRepository.get().updateFeed(
                    it.copy(
                        isNotification = !it.isNotification
                    )
                )
                fetchFeed(it.id)
            }
        }
    }

    private fun delete(callback: () -> Unit = {}) {
        _viewState.value.feed?.let {
            viewModelScope.launch(Dispatchers.IO) {
                rssRepository.get().deleteFeed(it)
                withContext(Dispatchers.Main) {
                    callback()
                }
            }
        }
    }

    private fun hideDeleteDialog() {
        _viewState.update {
            it.copy(
                deleteDialogVisible = false,
            )
        }
    }

    private fun showDeleteDialog() {
        _viewState.update {
            it.copy(
                deleteDialogVisible = true,
            )
        }
    }

    private fun rename() {
        _viewState.value.feed?.let {
            viewModelScope.launch {
                rssRepository.get().updateFeed(
                    it.copy(
                        name = _viewState.value.newName
                    )
                )
                _viewState.update {
                    it.copy(
                        renameDialogVisible = false,
                    )
                }
            }
        }
    }

    private fun changeRenameDialogVisible(visible: Boolean) {
        _viewState.update {
            it.copy(
                renameDialogVisible = visible,
                newName = if (visible) _viewState.value.feed?.name ?: "" else "",
            )
        }
    }

    private fun inputNewName(content: String) {
        _viewState.update {
            it.copy(
                newName = content
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
data class FeedOptionViewState(
    var drawerState: ModalBottomSheetState = ModalBottomSheetState(ModalBottomSheetValue.Hidden),
    val feed: Feed? = null,
    val selectedGroupId: String = "",
    val newGroupContent: String = "",
    val newGroupDialogVisible: Boolean = false,
    val groups: List<Group> = emptyList(),
    val deleteDialogVisible: Boolean = false,
    val newName: String = "",
    val renameDialogVisible: Boolean = false,
)

sealed class FeedOptionViewAction {
    data class Show(
        val scope: CoroutineScope,
        val feedId: String
    ) : FeedOptionViewAction()

    data class Hide(
        val scope: CoroutineScope,
    ) : FeedOptionViewAction()

    object ChangeAllowNotificationPreset : FeedOptionViewAction()
    object ChangeParseFullContentPreset : FeedOptionViewAction()

    data class SelectedGroup(
        val groupId: String
    ) : FeedOptionViewAction()

    data class InputNewGroup(
        val content: String
    ) : FeedOptionViewAction()

    data class Delete(
        val callback: () -> Unit = {}
    ) : FeedOptionViewAction()

    object ShowDeleteDialog : FeedOptionViewAction()
    object HideDeleteDialog : FeedOptionViewAction()

    object ShowNewGroupDialog : FeedOptionViewAction()
    object HideNewGroupDialog : FeedOptionViewAction()
    object AddNewGroup : FeedOptionViewAction()

    object ShowRenameDialog : FeedOptionViewAction()
    object HideRenameDialog : FeedOptionViewAction()
    object Rename : FeedOptionViewAction()
    data class InputNewName(
        val content: String
    ) : FeedOptionViewAction()
}
