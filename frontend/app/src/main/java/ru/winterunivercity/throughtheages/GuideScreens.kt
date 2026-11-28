package ru.winterunivercity.throughtheages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.IOException
import java.util.Locale
import org.json.JSONException

private sealed interface GuideResult<out T> {
    data object Loading : GuideResult<Nothing>
    data object Unavailable : GuideResult<Nothing>
    data class Ready<T>(val value: T) : GuideResult<T>
}

@Composable
internal fun <T> GuideData(
    path: String,
    onBack: (() -> Unit)? = null,
    load: suspend (String) -> T,
    modal: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    var attempt by remember(path) { mutableIntStateOf(0) }
    val result by produceState<GuideResult<T>>(GuideResult.Loading, path, attempt) {
        value = GuideResult.Loading
        value = try {
            GuideResult.Ready(load(path))
        } catch (_: IOException) {
            GuideResult.Unavailable
        } catch (_: JSONException) {
            GuideResult.Unavailable
        }
    }
    when (val current = result) {
        is GuideResult.Ready -> content(current.value)
        else -> Box(
            if (modal) Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(vertical = 8.dp)
            else Modifier.fillMaxSize().background(Paper).padding(20.dp),
        ) {
            if (onBack != null && !modal) BackArrow(onBack)
            if (current == GuideResult.Loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Не удалось загрузить данные", color = Ink)
                    GuideAction("Повторить", { attempt++ }, ActionStyle.OUTLINE)
                }
            }
        }
    }
}

@Composable
internal fun OfflineComparisonScreen(
    comparison: Comparison,
    onBack: () -> Unit,
    onSource: () -> Unit,
    onCamera: () -> Unit,
) {
    GuidePage {
        GuideHeader("Без камеры", onBack)
        Spacer(Modifier.height(19.dp))
        Text(comparison.title, color = Ink, fontSize = 34.sp, lineHeight = 40.sp, fontFamily = GuideHeadingFont, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp)
        Text("Церковь Лазаря сегодня и в XV веке.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(26.dp))
        comparison.eras.forEachIndexed { index, era ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))) {
                val tileWidth = maxWidth
                val height = tileWidth * (if (era.id == "today") (190f / 350f) else (239f / 350f))
                val today = era.id == "today"
                if (today) {
                    GuideImage(R.drawable.lazarus_today, Modifier.fillMaxWidth().height(height), era.title)
                } else {
                    Box(Modifier.fillMaxWidth().height(height).background(Color(0xFFEFF1F4))) {
                        GuideImage(
                            R.drawable.lazarus_reconstruction,
                            Modifier.align(Alignment.TopCenter).offset(y = height * (36f / 239f))
                                .size(tileWidth * (120f / 350f), height * (190f / 239f)),
                            era.title, ContentScale.Fit,
                        )
                    }
                }
                val suffix = if (today) era.title.lowercase(Locale.ROOT) else "реконструкция"
                Text(
                    era.label + " · " + suffix,
                    Modifier.padding(12.dp).background(Card, RoundedCornerShape(6.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                    color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Spacer(Modifier.weight(1f))
        GuideAction("Как восстановили облик", onSource, icon = Glyph.DOCUMENT, arrow = true)
        Spacer(Modifier.height(10.dp))
        GuideAction("Попробовать с камерой", onCamera, ActionStyle.OUTLINE, icon = Glyph.CAMERA, height = 54)
    }
}

@Composable
internal fun CameraComparisonScreen(
    comparison: Comparison,
    title: String,
    selectedEra: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onSource: () -> Unit,
) {
    val era = comparison.eras.firstOrNull { it.id == selectedEra } ?: comparison.eras.firstOrNull()
    val reconstructed = era?.id == "xv"
    BoxWithConstraints(Modifier.fillMaxSize().background(Night)) {
        val sceneHeight = maxHeight
        val sceneWidth = maxWidth
        GuideImage(R.drawable.lazarus_camera, Modifier.matchParentSize(), "Контур церкви Лазаря")
        if (reconstructed) {
            GuideImage(
                R.drawable.model_shadow,
                Modifier.align(Alignment.TopCenter).offset(y = sceneHeight * (533f / 844f))
                    .size(sceneWidth * (250f / 390f), sceneHeight * (44f / 844f)),
                scale = ContentScale.FillBounds,
            )
            GuideImage(
                R.drawable.lazarus_reconstruction,
                Modifier.align(Alignment.TopCenter).offset(y = sceneHeight * (194f / 844f))
                    .size(sceneWidth * (234f / 390f), sceneHeight * (370f / 844f)),
                "Реконструкция церкви Лазаря", ContentScale.Fit,
            )
        } else {
            GuideImage(
                R.drawable.foundation_outline,
                Modifier.fillMaxWidth().offset(y = sceneHeight * (404f / 844f))
                    .height(sceneHeight * (176f / 844f)),
                scale = ContentScale.FillBounds,
            )
        }
        CameraHeaderShade()
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 20.dp, end = 20.dp, top = 25.dp),
                ) {
                    GuideHeader(title, onBack, dark = true)
                    Spacer(Modifier.height(11.dp))
                    Row(
                        Modifier.background(Night.copy(alpha = .92f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuideGlyph(if (reconstructed) Glyph.LAYERS else Glyph.CHECK, OnNight, Modifier.size(16.dp))
                        Text(if (reconstructed) "Реконструкция" else "Метка найдена", color = OnNight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            val panelRadius = if (reconstructed) 42.dp else 18.dp
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = panelRadius, topEnd = panelRadius))
                .background(Night).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
                if (era != null) {
                    Text(era.title, color = OnNight, fontSize = 30.sp, lineHeight = 37.sp, fontFamily = GuideHeadingFont, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp)
                    Spacer(Modifier.height(7.dp))
                    Text(era.description, color = NightText, fontSize = 14.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF343E4A)).padding(5.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    for (item in comparison.eras) {
                        val selected = item.id == era?.id
                        Box(
                            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(7.dp))
                                .background(if (selected) Yellow else Color.Transparent)
                                .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.id) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(item.label, color = if (selected) Ink else OnNight, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                GuideAction("Как восстановили облик", onSource, ActionStyle.DARK_OUTLINE, icon = Glyph.DOCUMENT)
            }
        }
    }
}

@Composable
internal fun HistoricalSourceScreen(
    source: HistoricalSource,
    attribution: String,
    returnsToComparison: Boolean,
    onBack: () -> Unit,
) {
    GuidePage {
        GuideHeader("Источники", onBack, centered = true)
        Spacer(Modifier.height(21.dp))
        Text(attribution, color = Ink, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(14.dp))
        val heading = if (source.title == "Как восстановили облик") "Как восстановили\nоблик" else source.title
        Text(heading, color = Ink, fontSize = 36.sp, lineHeight = 41.sp, fontFamily = GuideArchiveFont, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(17.dp))
        val mount = RoundedCornerShape(18.dp)
        val frame = RoundedCornerShape(4.dp)
        Column(
            Modifier.fillMaxWidth().clip(mount).background(Color(0xFFF0E6D5))
                .border(1.dp, Color(0xFFDFD2BE), mount)
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
        ) {
            GuideImage(
                R.drawable.lazarus_drawing,
                Modifier.fillMaxWidth().aspectRatio(316f / 181f).clip(frame)
                    .border(4.dp, Color(0xFFDFC9AA), frame),
                "Фасад, план и разрез церкви по чертежу И. И. Горностаева",
            )
            Spacer(Modifier.height(18.dp))
            Text(source.caption.uppercase(Locale.ROOT), Modifier.padding(start = 4.dp), color = Muted, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
        }
        Spacer(Modifier.height(15.dp))
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(source.credit, color = Ink, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("1850–1860-е годы · " + source.collection + "\nГоскаталог № 18451467", color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(17.dp))
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(15.dp))
            Text("Что видно на чертеже", color = Ink, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(source.explanation, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(23.dp))
        Spacer(Modifier.weight(1f))
        GuideAction(if (returnsToComparison) "Вернуться к сравнению" else "Вернуться к памятнику", onBack, icon = Glyph.LEFT)
    }
}
