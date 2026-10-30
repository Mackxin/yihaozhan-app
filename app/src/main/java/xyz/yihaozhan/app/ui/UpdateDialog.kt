package xyz.yihaozhan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.MainVm

/**
 * 发现新版本时的弹窗。
 *
 * 三个阶段共用这一个壳：
 *   1. 说明 + 「稍后 / 立即更新」
 *   2. 下载中（进度条）
 *   3. 下好了 —— 按钮变成「去安装」，点了才拉起系统安装器
 *
 * 第 3 步不自动跳安装器是有意的：下载可能在后台完成，突然跳出一个系统安装框会吓人。
 */
@Composable
fun UpdateDialog(
    upd: MainVm.Upd,
    myVersion: String,
    onLater: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val pal = LocalXyPal.current
    val info = upd.latest ?: return
    val shape = RoundedCornerShape(XyR.lg.dp)

    AlertDialog(
        onDismissRequest = { if (!upd.downloading) onLater() },
        shape = shape,
        containerColor = pal.bgSoft,
        titleContentColor = pal.text,
        textContentColor = pal.dim,
        title = {
            Column {
                Text(
                    "新版本 v" + info.versionName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = pal.text
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "你现在是 v$myVersion" + if (info.size > 0) {
                        " · " + String.format("%.1f", info.size / 1048576f) + " MB"
                    } else "",
                    fontSize = 12.sp,
                    color = pal.mute
                )
            }
        },
        text = {
            Column {
                if (upd.downloading) {
                    Text("正在下载…", fontSize = 13.sp, color = pal.dim)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { upd.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(99.dp)),
                        color = pal.inkA1,
                        trackColor = pal.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (upd.progress * 100).toInt().toString() + "%",
                        fontSize = 12.sp,
                        color = pal.mute
                    )
                } else if (upd.apk != null) {
                    Text("下载好了，点「去安装」交给系统装。", fontSize = 13.sp, color = pal.dim)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "第一次会问「允许安装未知应用」，允许一次就行。",
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        color = pal.mute
                    )
                } else {
                    Text(
                        info.notes.ifBlank { "看看这次改了什么，装上就是了。" },
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        color = pal.dim
                    )
                    if (upd.error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(upd.error ?: "", fontSize = 12.sp, color = pal.inkA2)
                    }
                }
            }
        },
        confirmButton = {
            val label = when {
                upd.downloading -> "下载中…"
                upd.apk != null -> "去安装"
                else -> "立即更新"
            }
            TextButton(
                onClick = {
                    when {
                        upd.downloading -> Unit
                        upd.apk != null -> onInstall()
                        else -> onDownload()
                    }
                },
                enabled = !upd.downloading
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (upd.downloading) pal.mute else pal.inkA1
                )
            }
        },
        dismissButton = {
            if (!upd.downloading) {
                TextButton(onClick = onLater) {
                    Text(
                        if (upd.apk != null) "取消" else "稍后",
                        fontSize = 14.sp,
                        color = pal.mute
                    )
                }
            }
        }
    )
}

/**
 * 设置页「关于」里那一行：当前版本 + 检查更新 / 发现新版 / 下载进度。
 * 没弹窗时也能在这儿手动走一遍完整流程。
 */
@Composable
fun UpdateRow(
    upd: MainVm.Upd,
    myVersion: String,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val pal = LocalXyPal.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "App 版本",
                fontSize = 13.5.sp,
                color = pal.dim,
                modifier = Modifier.weight(1f)
            )
            Text("v$myVersion", fontSize = 13.5.sp, color = pal.text)
        }
        Spacer(Modifier.height(10.dp))

        val info = upd.latest
        when {
            upd.downloading -> Column {
                LinearProgressIndicator(
                    progress = { upd.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = pal.inkA1,
                    trackColor = pal.outline
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "正在下载 v" + (info?.versionName ?: "") + " · " +
                        (upd.progress * 100).toInt() + "%",
                    fontSize = 11.5.sp,
                    color = pal.mute
                )
            }

            upd.apk != null -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "v" + (info?.versionName ?: "") + " 已下载",
                    fontSize = 12.sp,
                    color = pal.inkA3,
                    modifier = Modifier.weight(1f)
                )
                MiniAction("去安装", true, onInstall)
            }

            info != null -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "发现 v" + info.versionName,
                    fontSize = 12.sp,
                    color = pal.inkA1,
                    modifier = Modifier.weight(1f)
                )
                MiniAction("更新", true, onDownload)
            }

            else -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        upd.checking -> "正在检查…"
                        upd.error != null -> upd.error ?: ""
                        else -> "已经是最新版"
                    },
                    fontSize = 12.sp,
                    color = if (upd.error != null) pal.inkA2 else pal.mute,
                    modifier = Modifier.weight(1f)
                )
                MiniAction(if (upd.checking) "检查中" else "检查更新", false, onCheck)
            }
        }
    }
}

/** 一颗小胶囊按钮 */
@Composable
private fun MiniAction(label: String, primary: Boolean, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .then(
                if (primary) Modifier.background(pal.grad())
                else Modifier.background(pal.glass)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            /* 渐变底上写字用深墨，白字压最浅那档青色不够看 */
            color = if (primary) XY_INK else pal.text
        )
    }
}
