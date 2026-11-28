package xyz.yihaozhan.app.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ==========================================================================
 *  指南数据 —— 来源是站点那个 `assets/tools/yuchanqi.html` 里的 PREGNANCY_DATA /
 *  GLOBAL_GUIDES 两个对象，用脚本抠出来烘成 `assets/pregnancy.json`。
 *
 *  为什么把文案搬成资产而不是抄进 Kotlin：那些内容会改（孕期知识、产检项），
 *  放进 json 里以后只换文件就行，不用重新读一遍 2000 行 Kotlin。
 * ========================================================================== */

@Serializable
data class GuideItem(val icon: String = "", val text: String = "")

@Serializable
data class GuideSocialItem(val icon: String = "", val label: String = "", val desc: String = "")

@Serializable
data class GuideSocial(
    val title: String = "",
    val items: List<GuideSocialItem> = emptyList(),
    val extra: String = ""
)

@Serializable
data class GuideFetus(val week: Int = 0, val desc: String = "")

@Serializable
data class GuidePlan(val week: Int = 0, val content: String = "", val status: String = "upcoming")

/** 一个孕期阶段（early / mid / late） */
@Serializable
data class GuideStage(
    val id: String = "",
    val label: String = "",
    val emoji: String = "",
    val weeks: String = "",
    val checks: List<GuideItem> = emptyList(),
    val notes: List<GuideItem> = emptyList(),
    val diet: List<GuideItem> = emptyList(),
    val social: GuideSocial = GuideSocial(),
    val fetus: List<GuideFetus> = emptyList(),
    val plan: List<GuidePlan> = emptyList()
)

@Serializable
data class GuideBlock(val title: String = "", val items: List<String> = emptyList())

/** 全局指南（分娩 / 产后 / 新生儿手续 / 哺乳 / 准爸爸 / 用药疫苗） */
@Serializable
data class GlobalGuide(
    val id: String = "",
    val title: String = "",
    val blocks: List<GuideBlock> = emptyList()
)

@Serializable
data class PregGuide(
    val stages: List<GuideStage> = emptyList(),
    val guides: List<GlobalGuide> = emptyList()
)

/* ==========================================================================
 *  本机数据
 * ========================================================================== */

@Serializable
data class HealthRec(val time: String = "", val value: String = "")

@Serializable
data class Hospital(val name: String = "", val doctor: String = "", val phone: String = "")

/**
 * 算出来的那一堆数。
 *
 * [progress] 是 0..1 的孕期进度（按 280 天算，多胎不折算 —— 进度条看的是「走到哪了」）。
 * [daysToDue] 已经扣过「提前提醒天数」，网页版那个倒计时也是这么算的。
 */
data class PregInfo(
    val valid: Boolean = false,
    val dueDate: String = "",
    val daysPregnant: Int = 0,
    val weeks: Int = 0,
    val remDays: Int = 0,
    val trimester: String = "--",
    val stageKey: String = "early",
    val daysToDue: Int = 0,
    val isFuture: Boolean = false,
    val progress: Float = 0f,
    val fetus: String = ""
)

/**
 * 孕期数据仓库。
 *
 * 跟随手记 / 待办一个套路：SharedPreferences（`yihaozhan.pregnancy`）+ `mutableStateOf`，
 * 改完立刻反映到界面。单独一份而不是塞进 [Local]：这里面有几百条体重/血压记录的可能，
 * 每次勾一下选项把整坨重写不值得。
 *
 * ⚠️ 跟网页版**不共用** localStorage（WebView 里那套 `weightData` 等键）。要搬数据走
 * 「导出 / 导入」那对按钮，格式是自己的 JSON，不保证能跟网页版互通。
 */
@Stable
class Pregnancy private constructor(ctx: Context) {

    private val app = ctx.applicationContext
    private val sp = app.getSharedPreferences("yihaozhan.pregnancy", Context.MODE_PRIVATE)
    private val J = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 末次月经第一天，`yyyy-MM-dd`；空 = 还没填 */
    var lmp by mutableStateOf(sp.getString("lmp", "").orEmpty())
        private set

    /** 1 单胎 / 2 双胎 / 3 三胎 */
    var multiples by mutableStateOf(sp.getInt("multiples", 1))
        private set

    /** 倒计时提前几天（网页版那个「提前提醒」） */
    var remind by mutableStateOf(sp.getInt("remind", 14))
        private set

    var weight by mutableStateOf(recs("weight"))
        private set
    var bp by mutableStateOf(recs("bp"))
        private set
    var kick by mutableStateOf(recs("kick"))
        private set
    var contraction by mutableStateOf(recs("contraction"))
        private set

    var hospital by mutableStateOf(
        runCatching { J.decodeFromString(Hospital.serializer(), sp.getString("hospital", "") ?: "") }
            .getOrDefault(Hospital())
    )
        private set

    /** 「孕 6 周: B超确认胎心胎芽」这种计划项的完成状态，键是 `阶段_周数` */
    var planDone by mutableStateOf(sp.getStringSet("planDone", emptySet()).orEmpty().toSet())
        private set

    /* ------------------------------ 写入 ------------------------------ */

    fun saveLmp(v: String) {
        lmp = v
        sp.edit().putString("lmp", v).apply()
    }

    fun saveMultiples(v: Int) {
        multiples = v.coerceIn(1, 3)
        sp.edit().putInt("multiples", multiples).apply()
    }

    fun saveRemind(v: Int) {
        remind = v.coerceIn(0, 60)
        sp.edit().putInt("remind", remind).apply()
    }

    /** kind 取 weight / bp / kick / contraction */
    fun addRec(kind: String, value: String) {
        val v = value.trim()
        if (v.isEmpty()) return
        val one = HealthRec(SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date()), v)
        when (kind) {
            "weight" -> weight = weight + one
            "bp" -> bp = bp + one
            "kick" -> kick = kick + one
            "contraction" -> contraction = contraction + one
        }
        saveRec(kind)
    }

    fun delRec(kind: String, index: Int) {
        fun drop(l: List<HealthRec>) = l.filterIndexed { i, _ -> i != index }
        when (kind) {
            "weight" -> weight = drop(weight)
            "bp" -> bp = drop(bp)
            "kick" -> kick = drop(kick)
            "contraction" -> contraction = drop(contraction)
        }
        saveRec(kind)
    }

    fun saveHospital(h: Hospital) {
        hospital = h
        sp.edit().putString("hospital", J.encodeToString(Hospital.serializer(), h)).apply()
    }

    fun togglePlan(key: String) {
        planDone = if (key in planDone) planDone - key else planDone + key
        sp.edit().putStringSet("planDone", planDone).apply()
    }

    /* ------------------------------ 备份 ------------------------------ */

    @Serializable
    private data class Backup(
        val lmp: String = "",
        val multiples: Int = 1,
        val remind: Int = 14,
        val weight: List<HealthRec> = emptyList(),
        val bp: List<HealthRec> = emptyList(),
        val kick: List<HealthRec> = emptyList(),
        val contraction: List<HealthRec> = emptyList(),
        val hospital: Hospital = Hospital(),
        val planDone: List<String> = emptyList()
    )

    fun exportJson(): String = J.encodeToString(
        Backup.serializer(),
        Backup(lmp, multiples, remind, weight, bp, kick, contraction, hospital, planDone.toList())
    )

    /** 导入：整份覆盖。返回读到的记录条数，-1 = 解析失败 */
    fun importJson(text: String): Int {
        val b = runCatching { J.decodeFromString(Backup.serializer(), text) }.getOrNull() ?: return -1
        lmp = b.lmp
        multiples = b.multiples.coerceIn(1, 3)
        remind = b.remind.coerceIn(0, 60)
        weight = b.weight
        bp = b.bp
        kick = b.kick
        contraction = b.contraction
        hospital = b.hospital
        planDone = b.planDone.toSet()
        sp.edit()
            .putString("lmp", lmp).putInt("multiples", multiples).putInt("remind", remind)
            .putString("hospital", J.encodeToString(Hospital.serializer(), hospital))
            .putStringSet("planDone", planDone)
            .apply()
        saveRec("weight"); saveRec("bp"); saveRec("kick"); saveRec("contraction")
        return b.weight.size + b.bp.size + b.kick.size + b.contraction.size
    }

    /* ------------------------------ 计算 ------------------------------ */

    /**
     * 照网页版的算法一字不差地搬过来：
     * 预产期 = 末次月经 + 280 天，双胎减 14、三胎减 28；
     * 孕周 = 已怀天数 / 7，余数就是「+N 天」；
     * 孕期分段 0–13 第一、14–27 第二、28–40 第三、>40 已过预产期。
     */
    fun info(): PregInfo {
        val start = parseDay(lmp) ?: return PregInfo()
        val today = zero(Calendar.getInstance())
        val lmpCal = zero(Calendar.getInstance().apply { time = start })

        val adjust = when (multiples) {
            2 -> -14
            3 -> -28
            else -> 0
        }
        val dueCal = (lmpCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 280 + adjust) }

        val future = today.before(lmpCal)
        var daysPregnant = ((today.timeInMillis - lmpCal.timeInMillis) / 86400000L).toInt()
        if (daysPregnant < 0) daysPregnant = 0
        val weeks = daysPregnant / 7
        val remDays = daysPregnant % 7

        val trimester = when {
            future -> "尚未怀孕"
            weeks <= 13 -> "第一孕期"
            weeks <= 27 -> "第二孕期"
            weeks <= 40 -> "第三孕期"
            else -> "已过预产期"
        }
        val stageKey = when {
            weeks <= 12 -> "early"
            weeks <= 27 -> "mid"
            else -> "late"
        }
        val daysDue = ((dueCal.timeInMillis - today.timeInMillis) / 86400000L).toInt()
        val daysToDue = (daysDue - remind).coerceAtLeast(0)
        val progress = (daysPregnant.toFloat() / 280f).coerceIn(0f, 1f)

        return PregInfo(
            valid = true,
            dueDate = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(dueCal.time),
            daysPregnant = daysPregnant,
            weeks = weeks,
            remDays = remDays,
            trimester = trimester,
            stageKey = stageKey,
            daysToDue = daysToDue,
            isFuture = future,
            progress = progress,
            fetus = fetusFor(weeks)
        )
    }

    /* ------------------------------ 内部 ------------------------------ */

    private fun recs(kind: String): List<HealthRec> = runCatching {
        J.decodeFromString(
            ListSerializer(HealthRec.serializer()),
            sp.getString("rec_" + kind, "") ?: ""
        )
    }.getOrDefault(emptyList())

    private fun saveRec(kind: String) {
        val list = when (kind) {
            "weight" -> weight
            "bp" -> bp
            "kick" -> kick
            else -> contraction
        }
        sp.edit().putString(
            "rec_" + kind,
            J.encodeToString(ListSerializer(HealthRec.serializer()), list)
        ).apply()
    }

    private fun fetusFor(weeks: Int): String {
        /* 网页版是「取不超过当前周的最大那一档」，所以每周都有话说，不会空着 */
        val g = guide ?: return ""
        val map = g.stages.flatMap { it.fetus }.sortedBy { it.week }
        val hit = map.lastOrNull { it.week <= weeks } ?: map.firstOrNull() ?: return ""
        return hit.desc
    }

    /** 指南数据（assets/pregnancy.json），第一次用到才读 */
    val guide: PregGuide? by lazy {
        runCatching {
            J.decodeFromString(PregGuide.serializer(), app.assets.open("pregnancy.json").use {
                it.readBytes().toString(Charsets.UTF_8)
            })
        }.getOrNull()
    }

    private fun parseDay(s: String): Date? = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(s)
    }.getOrNull()

    private fun zero(c: Calendar): Calendar = c.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    companion object {
        @Volatile
        private var inst: Pregnancy? = null

        fun of(ctx: Context): Pregnancy =
            inst ?: synchronized(this) { inst ?: Pregnancy(ctx).also { inst = it } }
    }
}
