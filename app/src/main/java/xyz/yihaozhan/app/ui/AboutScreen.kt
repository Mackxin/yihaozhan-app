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
import xyz.yihaozhan.app.data.AboutCfg

/**
 * 关于这一页 —— 网页端 `#/about` 的原生版。
 *
 * 正文跟文章页同一套 Markdown 渲染器，所以 `##` 标题、列表、引用、表格都能写；
 * 文案不写死在 App 里，全部来自站点 `app.json` 的 `about` 段 ——
 * 后台改完「关于这一页」重新生成 json，手机上打开就是新的。
 */
@Composable
fun AboutScreen(
    data: AppPayload,
    onBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    val pal = LocalXyPal.current
    val cfg: AboutCfg? = data.about
    val md = cfg?.md.orEmpty()
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
                    cfg?.title?.ifBlank { "关于" } ?: "关于",
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
                            cfg?.title?.ifBlank { data.site.name } ?: data.site.name,
                            fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (!cfg?.lede.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                cfg!!.lede, fontSize = 13.5.sp, lineHeight = 23.sp,
                                textAlign = TextAlign.Center, color = pal.dim
                            )
                        }
                        if (!cfg?.updated.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text("最后更新 " + cfg!!.updated, fontSize = 11.sp, color = pal.mute)
                        }
                        Spacer(Modifier.height(14.dp))
                        HairLine()
                    }
                }

                if (blocks.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            "📖", "还没写关于",
                            "站点后台「首页内容 → 关于这一页」写点东西，这里就会出现。"
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
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            androidx.compose.material3.TextButton(onClick = {
                                onOpen(data.site.url)
                            }) {
                                Text("在浏览器里打开网站", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
