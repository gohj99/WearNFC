package com.gohj99.wearnfc.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Text
import com.gohj99.wearnfc.R

/**
 * 通用错误对话框。
 *
 * 用法：在 Activity / Screen 顶层维护一个 `AppError?` 状态，非空时传入此 Composable。
 *
 * @param error   当前要展示的错误；为 null 时不渲染任何内容。
 * @param onDismiss 点击"知道了"后的回调（通常将状态置为 null）。
 */
@Composable
fun ErrorDialog(
    error: AppError?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        visible = error != null,
        onDismissRequest = onDismiss,
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = onDismiss)
        },
        title = {
            Text(stringResource(error?.titleRes ?: R.string.error_dismiss))
        },
        text = {
            Text(stringResource(error?.bodyRes ?: R.string.error_dismiss))
        },
    )
}

/**
 * 所有可展示的错误类型。
 *
 * 新增错误：加一个 object，提供对应的字符串资源即可。
 */
sealed class AppError(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
) {
    data object NoRoot : AppError(
        titleRes = R.string.error_no_root_title,
        bodyRes = R.string.error_no_root_body,
    )

    data object NoSupportedModule : AppError(
        titleRes = R.string.error_no_supported_module_title,
        bodyRes = R.string.error_no_supported_module_body,
    )
}
