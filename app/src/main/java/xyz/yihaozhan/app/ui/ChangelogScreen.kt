package xyz.yihaozhan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.data.AppPayload
import xyz.yihaozhan.app.data.VersionLog

/**
 * 版本更新 —— 每个版本改了什么，一条一条列着。
 *
 * 数据来自站点仓库 `data/changelog.json`（`tools/gen-app-json.js` 烘进 `app.json` 的
 * `changelog` 段，新的排前面）。跟使用手册一样，改文案不用发版。
 *
 * 版式：一版一张卡，左边版本号 + 日期，右边那几条要点。
 * 最新那一版打个「最新」小标，省得每次都要自己比对版本号。
 */
@Composable
fun ChangelogScreen(
    data: AppPayload,
    onBack: () -> Unit
) {
    val pal = LocalXyPal.current
    val list = data.changelog
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
                }
                Text(
                    "版本更新",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = pal.dim,
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "head") {
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
                        Text(
                            "版本更新",
                            fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "每一版动了什么，按时间倒序。",
                            fontSize = 13.sp, color = pal.dim
                        )
                        Spacer(Modifier.height(12.dp))
                        HairLine()
                    }
                }

                if (list.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            "🧾", "还没有版本记录",
                            "等下一次内容更新就有了。"
                        )
                    }
                } else {
                    items(list, key = { it.code.toString() + it.version }) { v ->
                        VersionCard(v, isLatest = v === list.first())
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionCard(v: VersionLog, isLatest: Boolean) {
    val pal = LocalXyPal.current
    CardBox(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "v" + v.version,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = pal.text
                )
                if (isLatest) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(pal.glassHi)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text("最新", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = pal.inkA1)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (v.date.isNotBlank()) {
                    Text(v.date, fontSize = 11.sp, color = pal.mute)
                }
            }

            Spacer(Modifier.height(9.dp))

            v.items.forEach { line ->
                Row(Modifier.padding(bottom = 6.dp)) {
                    /* 圆点手动画：直接写「· 」在换行时会掉到下一行行首，看着别扭 */
                    Box(
                        Modifier
                            .padding(top = 7.dp, end = 8.dp)
                            .width(4.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(pal.mute)
                    )
                    Text(
                        line,
                        fontSize = 12.5.sp,
                        lineHeight = 20.sp,
                        color = pal.inkA2,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}
