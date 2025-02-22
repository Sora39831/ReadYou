package me.ash.reader.ui.page.home.drawer.feed

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import me.ash.reader.R
import me.ash.reader.ui.component.Dialog
import me.ash.reader.ui.ext.collectAsStateValue

@OptIn(ExperimentalPagerApi::class)
@Composable
fun DeleteFeedDialog(
    modifier: Modifier = Modifier,
    feedName: String,
    viewModel: FeedOptionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val viewState = viewModel.viewState.collectAsStateValue()
    val scope = rememberCoroutineScope()
    val toastString = stringResource(R.string.delete_toast, feedName)

    Dialog(
        visible = viewState.deleteDialogVisible,
        onDismissRequest = {
            viewModel.dispatch(FeedOptionViewAction.HideDeleteDialog)
        },
        icon = {
            Icon(
                imageVector = Icons.Outlined.DeleteForever,
                contentDescription = stringResource(R.string.unsubscribe),
            )
        },
        title = {
            Text(text = stringResource(R.string.unsubscribe))
        },
        text = {
            Text(text = stringResource(R.string.unsubscribe_tip, feedName))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.dispatch(FeedOptionViewAction.Delete {
                        viewModel.dispatch(FeedOptionViewAction.HideDeleteDialog)
                        viewModel.dispatch(FeedOptionViewAction.Hide(scope))
                        Toast.makeText(context, toastString, Toast.LENGTH_SHORT).show()
                    })
                }
            ) {
                Text(
                    text = stringResource(R.string.unsubscribe),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.dispatch(FeedOptionViewAction.HideDeleteDialog)
                }
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                )
            }
        },
    )
}