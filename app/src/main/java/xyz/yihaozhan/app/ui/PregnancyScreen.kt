package xyz.yihaozhan.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.yihaozhan.app.data.HealthRec
import xyz.yihaozhan.app.data.Hospital
import xyz.yihaozhan.app.data.Pregnancy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 孕期全程管理（2.9.6）。
 *
 * 站点上那份 `assets/tools/yuchanqi.html` 的原生版 —— 以前从「导航 → 万物归一」点进来
 * 是套在 WebView 里跑的网页，现在整页原生：算预产期、记体重/血压/胎动/宫缩、
 * 按周给指南、存医院和医生电话、导出导入数据。
 *
 * 算法照网页版一字不差（末次月经 + 280 天，双胎 −14、三胎 −28），
 * 指南文案从 `assets/pregnancy.json` 读 —— 那份是网页里 PREGNANCY_DATA /
 * GLOBAL_GUIDES 两个对象抠出来的，改内容不用动 Kotlin。
 *
 * ⚠️ 记录只存本机（`yihaozhan.pregnancy`），跟网页版 localStorage 不共通，
 * 想搬就用「⋯ → 导出」，导出的 JSON 只有本 App 认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PregnancyScreen(onBack: () -> Unit) {
    val pal = LocalXyPal.current
    val ctx = LocalContext.current
    val pg = remember { Pregnancy.of(ctx) }
    val scope = rememberCoroutineScope()

    var pickDate by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }

    val info = pg.info()
    val guide = pg.guide

    /* 当前看哪一段指南：跟着孕周自动选，用户点了就以点击为准 */
    var stageKey by remember(info.stageKey) { mutableStateOf(info.stageKey) }
    var tab by remember { mutableStateOf("checks") }
    /** null = 看阶段指南；非空 = 看那条全局指南（分娩/产后/…） */
    var globalId by remember { mutableStateOf<String?>(null) }

    /* 导出 / 导入：走系统文件选择器，不申请任何存储权限 */
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val json = pg.exportJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
                }.isSuccess
            }
            toast(ctx, if (ok) "已导出孕期数据" else "导出失败，换个位置再试试")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)!!.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            val n = text?.let { pg.importJson(it) }
            toast(
                ctx,
                when {
                    n == null -> "读不到那个文件"
                    n < 0 -> "不是孕期数据文件"
                    else -> "已导入，共 $n 条记录"
                }
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        /* 顶部：返回 + 标题 + ⋯ */
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
            }
            Text(
                "孕期全程管理",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = pal.dim,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { more = true }) {
                Icon(Icons.Rounded.MoreVert, "更多", tint = pal.dim)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            /* ---------- 输入 ---------- */
            item("form") {
                CardBox(padding = PaddingValues(16.dp)) {
                    Column {
                        Text("📆 末次月经第一天", fontSize = 12.sp, color = pal.mute)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(pal.rMd.dp))
                                .background(pal.glass2)
                                .clickable { pickDate = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                pg.lmp.ifBlank { "点这里选日期" },
                                fontSize = 15.sp,
                                color = if (pg.lmp.isBlank()) pal.mute else pal.text,
                                modifier = Modifier.weight(1f)
                            )
                            Text("选择", fontSize = 12.sp, color = pal.inkA1)
                        }

                        Spacer(Modifier.height(14.dp))
                        Text("👶 多胎", fontSize = 12.sp, color = pal.mute)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "单胎", 2 to "双胎", 3 to "三胎").forEach { (n, label) ->
                                Chip(label, pg.multiples == n) { pg.saveMultiples(n) }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("⏰ 提前提醒", fontSize = 12.sp, color = pal.mute)
                                Text(
                                    "倒计时会先减去这几天",
                                    fontSize = 11.sp,
                                    color = pal.mute.copy(alpha = 0.75f)
                                )
                            }
                            Step("−") { pg.saveRemind(pg.remind - 1) }
                            Text(
                                "${pg.remind} 天",
                                fontSize = 14.sp,
                                color = pal.text,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Step("+") { pg.saveRemind(pg.remind + 1) }
                        }
                    }
                }
            }

            /* ---------- 结果 ---------- */
            item("result") {
                CardBox(padding = PaddingValues(18.dp)) {
                    Column {
                        if (!info.valid) {
                            Text("先选一下末次月经的日期", fontSize = 14.sp, color = pal.dim)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "选完立刻出预产期、当前孕周和倒计时。",
                                fontSize = 12.sp,
                                color = pal.mute
                            )
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    if (info.isFuture) "还没开始" else "还有",
                                    fontSize = 12.sp,
                                    color = pal.mute,
                                    modifier = Modifier.padding(bottom = 6.dp, end = 6.dp)
                                )
                                Text(
                                    "${info.daysToDue}",
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = pal.text
                                )
                                Text(
                                    "天",
                                    fontSize = 14.sp,
                                    color = pal.dim,
                                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                )
                                Spacer(Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("预产期", fontSize = 11.sp, color = pal.mute)
                                    Text(info.dueDate, fontSize = 14.sp, color = pal.text)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "孕 ${info.weeks} 周 ${if (info.remDays > 0) "+${info.remDays} 天" else ""}" +
                                    " · ${info.trimester}",
                                fontSize = 13.sp,
                                color = pal.dim
                            )
                            Spacer(Modifier.height(12.dp))
                            /* 进度条：280 天的比例，渐变那一段就是走完的路 */
                            Box(
                                Modifier.fillMaxWidth().height(7.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(pal.glass2)
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(info.progress).height(7.dp)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(
                                            Brush.horizontalGradient(listOf(pal.a1, pal.a2, pal.a3))
                                        )
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "已过 ${info.daysPregnant} 天 / 280 天（${(info.progress * 100).toInt()}%）",
                                fontSize = 11.sp,
                                color = pal.mute
                            )
                            if (info.fetus.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "👶 ${info.fetus}",
                                    fontSize = 13.sp,
                                    color = pal.dim,
                                    lineHeight = 21.sp
                                )
                            }
                        }
                    }
                }
            }

            /* ---------- 记录 ---------- */
            item("rec-title") { SectionTitle("记录") }
            item("w") {
                RecCard(
                    icon = "⚖️", title = "体重", unit = "kg",
                    placeholder = "输入体重", recs = pg.weight,
                    onAdd = { pg.addRec("weight", it) },
                    onDel = { pg.delRec("weight", it) }
                )
            }
            item("bp") {
                RecCard(
                    icon = "💉", title = "血压", unit = "mmHg",
                    placeholder = "例如 120/80", recs = pg.bp,
                    onAdd = { pg.addRec("bp", it) },
                    onDel = { pg.delRec("bp", it) }
                )
            }
            item("kick") {
                RecCard(
                    icon = "👣", title = "胎动", unit = "次",
                    placeholder = "输入次数", recs = pg.kick,
                    onAdd = { pg.addRec("kick", it) },
                    onDel = { pg.delRec("kick", it) }
                )
            }
            item("ct") {
                RecCard(
                    icon = "📊", title = "宫缩", unit = "",
                    placeholder = "例如 10分钟1次", recs = pg.contraction,
                    onAdd = { pg.addRec("contraction", it) },
                    onDel = { pg.delRec("contraction", it) }
                )
            }

            /* ---------- 指南 ---------- */
            item("guide-title") { SectionTitle("指南") }
            item("guide") {
                CardBox(padding = PaddingValues(16.dp)) {
                    Column {
                        /* 第一排：三个阶段 */
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (guide?.stages ?: emptyList()).forEach { st ->
                                Chip(
                                    "${st.emoji} ${st.label}",
                                    globalId == null && stageKey == st.id
                                ) {
                                    globalId = null
                                    stageKey = st.id
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        /* 第二排：这一阶段里的六个内容页 */
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GUIDE_TABS.forEach { (id, label) ->
                                val usesStage = globalId == null
                                Chip(label, usesStage && tab == id, small = true) {
                                    if (usesStage) tab = id
                                    else {
                                        /* 正看着全局指南时点内容页 → 先回到阶段指南 */
                                        globalId = null
                                        tab = id
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        /* 第三排：全局指南 */
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (guide?.guides ?: emptyList()).forEach { g ->
                                Chip(
                                    g.title.substringBefore(" · "),
                                    globalId == g.id,
                                    small = true
                                ) { globalId = g.id }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        val stage = guide?.stages?.firstOrNull { it.id == stageKey }
                        val g = guide?.guides?.firstOrNull { it.id == globalId }
                        if (g != null) {
                            /* 全局指南：几块「标题 + 条目」 */
                            Text(g.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = pal.text)
                            g.blocks.forEach { b ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    b.title,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = pal.inkA1
                                )
                                b.items.forEach { line ->
                                    Bullet(line)
                                }
                            }
                        } else if (stage == null) {
                            Text("指南数据没读到（assets/pregnancy.json）", fontSize = 12.sp, color = pal.mute)
                        } else {
                            when (tab) {
                                "checks", "notes", "diet" -> {
                                    val list = when (tab) {
                                        "checks" -> stage.checks
                                        "notes" -> stage.notes
                                        else -> stage.diet
                                    }
                                    list.forEach { Bullet(it.text, it.icon) }
                                }
                                "social" -> {
                                    Text(
                                        stage.social.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = pal.text
                                    )
                                    stage.social.items.forEach { it2 ->
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            "${it2.icon} ${it2.label}",
                                            fontSize = 12.5.sp,
                                            color = pal.inkA1
                                        )
                                        Text(
                                            it2.desc,
                                            fontSize = 12.sp,
                                            color = pal.dim,
                                            lineHeight = 19.sp
                                        )
                                    }
                                    if (stage.social.extra.isNotBlank()) {
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            stage.social.extra,
                                            fontSize = 12.sp,
                                            color = pal.mute,
                                            lineHeight = 19.sp
                                        )
                                    }
                                }
                                "fetus" -> stage.fetus.forEach { f ->
                                    Spacer(Modifier.height(8.dp))
                                    Text("📆 孕${f.week}周", fontSize = 12.5.sp, color = pal.inkA1)
                                    Text(
                                        f.desc,
                                        fontSize = 12.5.sp,
                                        color = pal.dim,
                                        lineHeight = 19.sp
                                    )
                                }
                                "plan" -> stage.plan.forEach { p ->
                                    val key = "${stage.id}_${p.week}"
                                    val done = key in pg.planDone
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(pal.rSm.dp))
                                            .clickable { pg.togglePlan(key) }
                                            .padding(vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(17.dp)
                                                .clip(CircleShape)
                                                .background(if (done) pal.inkA1 else pal.glass2)
                                                .border(
                                                    1.dp,
                                                    if (done) pal.inkA1 else pal.outline,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (done) Text("✓", fontSize = 10.sp, color = pal.bg)
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "孕${p.week}周",
                                            fontSize = 12.sp,
                                            color = pal.mute
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            p.content,
                                            fontSize = 12.5.sp,
                                            color = if (done) pal.mute else pal.text,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /* ---------- 医院 ---------- */
            item("hosp-title") { SectionTitle("医院与医生") }
            item("hosp") {
                HospitalCard(pg.hospital) { h ->
                    pg.saveHospital(h)
                    toast(ctx, "已保存")
                }
            }

            item("tip") {
                Text(
                    "🌱 仅供参考 · 具体请遵医嘱 · 各地区政策可能不同",
                    fontSize = 11.sp,
                    color = pal.mute,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }

    /* 日期选择 */
    if (pickDate) {
        val ms = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(pg.lmp)?.time
        }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = ms)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        /* DatePicker 给的是 UTC 零点，按 UTC 解才是那天 */
                        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                            .apply { timeInMillis = it }
                        pg.saveLmp(
                            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(c.time)
                        )
                    }
                    pickDate = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickDate = false }) { Text("取消") }
            }
        ) { DatePicker(state = state) }
    }

    /* 更多：导出 / 导入 */
    if (more) {
        Dialog(onDismissRequest = { more = false }) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(pal.rXl.dp))
                    .background(pal.bgSoft)
                    .padding(vertical = 8.dp)
            ) {
                MenuRow("📤 导出数据") {
                    more = false
                    exportLauncher.launch("pregnancy-data.json")
                }
                MenuRow("📥 导入数据") {
                    more = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
                MenuRow("关闭") { more = false }
            }
        }
    }
}

/** 阶段指南里的六个内容页（key 跟 pregnancy.json 里的字段同名） */
private val GUIDE_TABS = listOf(
    "checks" to "✅ 产检",
    "notes" to "📝 注意",
    "diet" to "🥗 饮食",
    "social" to "📋 社保",
    "fetus" to "👶 胎儿",
    "plan" to "📅 计划"
)

@Composable
private fun SectionTitle(t: String) {
    val pal = LocalXyPal.current
    Text(
        t,
        fontSize = 12.sp,
        color = pal.mute,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

/** 小胶囊按钮。选中时用强调色描边 + 浅底，跟站点后台那排一致 */
@Composable
private fun Chip(
    label: String,
    on: Boolean,
    small: Boolean = false,
    onClick: () -> Unit
) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (on) pal.glassHi else pal.glass2)
            .border(
                1.dp,
                if (on) pal.inkA1.copy(alpha = 0.55f) else pal.outline,
                RoundedCornerShape(99.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = if (small) 11.dp else 13.dp, vertical = if (small) 5.dp else 7.dp)
    ) {
        Text(
            label,
            fontSize = if (small) 11.5.sp else 12.5.sp,
            color = if (on) pal.text else pal.dim
        )
    }
}

/** 加减那颗圆钮（提前提醒用） */
@Composable
private fun Step(label: String, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Box(
        Modifier.size(30.dp)
            .clip(CircleShape)
            .background(pal.glass2)
            .border(1.dp, pal.outline, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 16.sp, color = pal.dim) }
}

@Composable
private fun Bullet(text: String, icon: String = "•") {
    val pal = LocalXyPal.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(icon, fontSize = 12.5.sp, modifier = Modifier.padding(end = 8.dp))
        Text(text, fontSize = 12.5.sp, color = pal.dim, lineHeight = 19.sp)
    }
}

/**
 * 一条记录卡：输入框 + 添加 + 最近 5 条。
 *
 * 只显示最近 5 条是照网页版来的 —— 体重一天一条，攒一个月几十条会把整页撑得很长，
 * 真要翻全部就去导出。
 */
@Composable
private fun RecCard(
    icon: String,
    title: String,
    unit: String,
    placeholder: String,
    recs: List<HealthRec>,
    onAdd: (String) -> Unit,
    onDel: (Int) -> Unit
) {
    val pal = LocalXyPal.current
    var draft by remember { mutableStateOf("") }

    fun submit() {
        if (draft.isBlank()) return
        onAdd(draft)
        draft = ""
    }

    CardBox(padding = PaddingValues(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$icon $title", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = pal.text)
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(unit, fontSize = 11.sp, color = pal.mute)
                }
                Spacer(Modifier.weight(1f))
                if (recs.isNotEmpty()) {
                    Text("共 ${recs.size} 条", fontSize = 11.sp, color = pal.mute)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(pal.rMd.dp))
                        .background(pal.glass2)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (draft.isEmpty()) {
                        Text(placeholder, fontSize = 13.sp, color = pal.mute)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = pal.text),
                        cursorBrush = SolidColor(pal.inkA1),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(pal.rMd.dp))
                        .background(pal.glassHi)
                        .clickable { submit() }
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) { Text("添加", fontSize = 13.sp, color = pal.text) }
            }
            if (recs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                recs.reversed().take(5).forEachIndexed { i, r ->
                    val trueIndex = recs.lastIndex - i
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(pal.rSm.dp))
                            .clickable { onDel(trueIndex) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$icon ${r.value}", fontSize = 12.5.sp, color = pal.text)
                        Spacer(Modifier.weight(1f))
                        Text(r.time, fontSize = 11.sp, color = pal.mute)
                    }
                }
                Text(
                    "点一条可以删掉（最近 5 条）",
                    fontSize = 10.5.sp,
                    color = pal.mute.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun HospitalCard(save: Hospital, onSave: (Hospital) -> Unit) {
    val pal = LocalXyPal.current
    var name by remember(save) { mutableStateOf(save.name) }
    var doctor by remember(save) { mutableStateOf(save.doctor) }
    var phone by remember(save) { mutableStateOf(save.phone) }

    CardBox(padding = PaddingValues(16.dp)) {
        Column {
            FieldLine("医院", name, "医院名称") { name = it }
            Spacer(Modifier.height(8.dp))
            FieldLine("医生", doctor, "医生姓名") { doctor = it }
            Spacer(Modifier.height(8.dp))
            FieldLine("电话", phone, "联系电话") { phone = it }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(pal.rMd.dp))
                    .background(Brush.horizontalGradient(listOf(pal.a1, pal.a2)))
                    .clickable { onSave(Hospital(name.trim(), doctor.trim(), phone.trim())) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) { Text("保存", fontSize = 13.sp, color = pal.bg) }
        }
    }
}

@Composable
private fun FieldLine(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    val pal = LocalXyPal.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = pal.mute, modifier = Modifier.width(46.dp))
        Box(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(pal.rMd.dp))
                .background(pal.glass2)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty()) Text(hint, fontSize = 13.sp, color = pal.mute)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.5.sp, color = pal.text),
                cursorBrush = SolidColor(pal.inkA1),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 弹窗里的一行菜单 */
@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Box(
        Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) { Text(label, fontSize = 14.sp, color = pal.text) }
}

private fun toast(ctx: android.content.Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
