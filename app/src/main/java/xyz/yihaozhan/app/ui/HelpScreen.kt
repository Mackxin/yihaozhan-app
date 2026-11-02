package xyz.yihaozhan.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.data.AppPayload

/**
 * 使用手册 —— App 端的「这玩意儿怎么用」。
 *
 * 正文来自站点仓库 `data/help.md`，由 `tools/gen-app-json.js` 烘进 `app.json` 的 `help` 段。
 * 为什么不写死在 App 里：手册这种东西改得比代码勤，写死等于每改一句话都要发一次版本；
 * 放在站点仓库改完重跑一次生成脚本就同步过来了。
 *
 * 渲染器跟「关于」「文章正文」共用一套（`Markdown.kt`），所以 `##` 标题、列表、
 * 引用、表格都能写，写手册时不用记另一套语法。
 */
@Composable
fun HelpScreen(
    data: AppPayload,
    onBack: () -> Unit
) {
    val pal = LocalXyPal.current
    val md = data.help
    val blocks = remember(md) { parseMarkdown(md) }
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
                    "使用手册",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = pal.dim,
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "head") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "使用手册",
                            fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "从零认一遍这个 App：顶栏、底栏、六个页签，以及那些藏起来的小动作。",
                            fontSize = 13.5.sp, lineHeight = 23.sp,
                            textAlign = TextAlign.Center, color = pal.dim
                        )
                        Spacer(Modifier.height(14.dp))
                        HairLine()
                    }
                }

                if (blocks.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            "📘", "手册还没写好",
                            "等下一次内容更新就有了。"
                        )
                    }
                } else {
                    itemsIndexed(blocks) { _, b ->
                        SelectionContainer { MdBlockView(b) }
                    }
                }

                item(key = "foot") {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                        HairLine()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "手册来自站点仓库的 data/help.md，改完重新生成一次数据就会同步到 App。",
                            fontSize = 11.sp, lineHeight = 18.sp, color = pal.mute,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
