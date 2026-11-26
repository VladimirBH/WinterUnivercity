package ru.winterunivercity.throughtheages

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.io.Serializable
import java.util.Locale

private val Rust = Color(0xFF913F29)
private val Park = Color(0xFFEDF2EA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0xFFF4F6F2.toInt(), 0xFFF4F6F2.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFF4F6F2.toInt(), 0xFFF4F6F2.toInt()),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Ink, onPrimary = Card, background = Paper, onBackground = Ink)) {
                ThroughTheAges { immersive, dimmed ->
                    val bars = WindowCompat.getInsetsController(window, window.decorView)
                    bars.isAppearanceLightStatusBars = !immersive && !dimmed
                    bars.isAppearanceLightNavigationBars = !immersive
                }
            }
        }
    }
}

private enum class Screen { MAP, MONUMENT, SCAN, CAMERA_COMPARISON, OFFLINE_COMPARISON, SOURCE }
private data class Subject(val title: String, val builtYear: Int, val dismantledYear: Int) : Serializable {
    val attribution get() = title.uppercase(Locale.ROOT) + " · " + builtYear
}
private data class Destination(
    val screen: Screen,
    val path: String,
    val subject: Subject? = null,
    val comparisonPath: String? = null,
) : Serializable
private data class CameraRequest(val arPath: String, val subject: Subject) : Serializable

@Composable
private fun ThroughTheAges(updateBars: (Boolean, Boolean) -> Unit) {
    val context = LocalContext.current
    val api = remember(context) { GuideApi(context) }
    var navigation by rememberSaveable { mutableStateOf(arrayListOf(Destination(Screen.MAP, "/api/v1/map"))) }
    var cameraRequest by rememberSaveable { mutableStateOf<CameraRequest?>(null) }
    var selectedEra by rememberSaveable { mutableStateOf<String?>(null) }

    val destination = navigation.last()
    val parent = navigation.getOrNull(navigation.lastIndex - 1)
    val immersive = destination.screen == Screen.SCAN || destination.screen == Screen.CAMERA_COMPARISON
    val onBack: () -> Unit = {
        if (cameraRequest != null) cameraRequest = null
        else if (navigation.size > 1) navigation = ArrayList(navigation.dropLast(1))
    }
    fun open(next: Destination) {
        navigation = ArrayList(navigation + next)
    }
    fun openOffline(path: String, subject: Subject) {
        val screen = Destination(Screen.OFFLINE_COMPARISON, path, subject)
        val previous = if (navigation.last().screen == Screen.SCAN) navigation.dropLast(1) else navigation
        navigation = ArrayList(if (previous.last() == screen) previous else previous + screen)
    }

    BackHandler(enabled = cameraRequest != null || navigation.size > 1, onBack = onBack)
    SideEffect { updateBars(immersive, cameraRequest != null) }
    val background = when {
        immersive -> Night
        destination.screen == Screen.MAP -> Card
        else -> Paper
    }
    Box(Modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val path = destination.path
            when (destination.screen) {
                Screen.MAP -> GuideData(path, load = api::map) { map ->
                    MapScreen(map) { open(Destination(Screen.MONUMENT, it)) }
                }
                Screen.MONUMENT -> GuideData(path, onBack, api::monument) { monument ->
                    val subject = Subject(monument.title, monument.builtYear, monument.dismantledYear)
                    MonumentDetails(
                        monument, onBack,
                        onAr = { cameraRequest = CameraRequest(monument.ar, subject) },
                        onSource = { open(Destination(Screen.SOURCE, monument.source, subject)) },
                    )
                }
                Screen.SCAN -> {
                    val subject = requireNotNull(destination.subject)
                    val comparison = requireNotNull(destination.comparisonPath)
                    ScanScreen(
                        onBack,
                        onShowComparison = {
                            selectedEra = null
                            open(Destination(Screen.CAMERA_COMPARISON, comparison, subject))
                        },
                        onMissingMark = { openOffline(comparison, subject) },
                    )
                }
                Screen.CAMERA_COMPARISON -> GuideData(path, onBack, api::comparison) { comparison ->
                    val subject = requireNotNull(destination.subject)
                    CameraComparisonScreen(comparison, subject.title, selectedEra, { selectedEra = it }, onBack) {
                        open(Destination(Screen.SOURCE, comparison.source, subject))
                    }
                }
                Screen.OFFLINE_COMPARISON -> GuideData(path, onBack, api::comparison) { comparison ->
                    val subject = requireNotNull(destination.subject)
                    OfflineComparisonScreen(
                        comparison, onBack,
                        onSource = { open(Destination(Screen.SOURCE, comparison.source, subject)) },
                        onCamera = { cameraRequest = CameraRequest(comparison.ar, subject) },
                    )
                }
                Screen.SOURCE -> GuideData(path, onBack, api::source) { source ->
                    HistoricalSourceScreen(
                        source, requireNotNull(destination.subject).attribution,
                        parent?.screen in setOf(Screen.CAMERA_COMPARISON, Screen.OFFLINE_COMPARISON), onBack,
                    )
                }
            }
            cameraRequest?.let { request ->
                CameraRequestSheet(
                    request.subject.builtYear, request.subject.dismantledYear, request.arPath, api,
                    onDismiss = { cameraRequest = null },
                    onCamera = { ar ->
                        cameraRequest = null
                        open(Destination(Screen.SCAN, request.arPath, request.subject, ar.fallbackUrl))
                    },
                    onWithoutCamera = { ar ->
                        cameraRequest = null
                        openOffline(ar.fallbackUrl, request.subject)
                    },
                )
            }
        }
        if (cameraRequest != null) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Ink.copy(alpha = .72f)))
        }
    }
}

@Composable
private fun MapScreen(map: GuideMap, onOpen: (String) -> Unit) {
    val first = map.markers.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Paper).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(map.title, color = Ink, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.15).sp)
                Text(map.city, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
            if (first != null) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(Lime)
                        .semantics { contentDescription = "Открыть памятник" }
                        .clickable(role = Role.Button) { onOpen(first.card) },
                    contentAlignment = Alignment.Center,
                ) { GuideGlyph(Glyph.CHURCH, Rust, Modifier.size(22.dp)) }
            }
        }
        NeighborhoodMap(map, Modifier.weight(1f).fillMaxWidth(), onOpen)
        if (first != null) MonumentCard(first) { onOpen(first.card) }
    }
}

@Composable
private fun NeighborhoodMap(map: GuideMap, modifier: Modifier, onMonumentClick: (String) -> Unit) {
    BoxWithConstraints(modifier.background(Park)) {
        Image(
            painterResource(R.drawable.neighborhood_map), "Схема района Кожевники",
            Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
        )
        Canvas(Modifier.fillMaxSize()) {
            val start = map.routePoints.firstOrNull() ?: return@Canvas
            val path = Path().apply {
                moveTo(start.x * size.width, start.y * size.height)
                map.routePoints.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
            }
            val routeStyle = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx())))
            drawPath(path, Card, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, Color(0xFF466331), style = routeStyle)
            val center = Offset(start.x * size.width, start.y * size.height)
            drawCircle(Card, 12.dp.toPx(), center)
            drawCircle(Color(0xFFDCEBA5), 10.dp.toPx(), center)
            drawCircle(Color(0xFF426139), 4.5.dp.toPx(), center)
        }
        Text("Кожевники", Modifier.offset(maxWidth * .05f, maxHeight * .04f), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Великая ул.", Modifier.offset(maxWidth * .56f, maxHeight * .19f), color = Muted, fontSize = 11.sp)
        Text("Бредова-Звериная ул.", Modifier.offset(maxWidth * .04f, maxHeight * .37f), color = Muted, fontSize = 11.sp)
        Column(
            Modifier.align(Alignment.TopEnd).padding(20.dp).size(42.dp, 48.dp)
                .background(Card, RoundedCornerShape(11.dp)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("С", color = Ink, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold)
            Canvas(Modifier.size(18.dp)) {
                val arrow = Path().apply {
                    moveTo(size.width * .5f, 2f); lineTo(size.width * .82f, size.height * .86f)
                    lineTo(size.width * .5f, size.height * .68f); lineTo(size.width * .18f, size.height * .86f); close()
                }
                drawPath(arrow, Color(0xFF315547), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        for (marker in map.markers) {
            Box(
                Modifier.offset(maxWidth * marker.position.x - 25.dp, maxHeight * marker.position.y - 62.dp)
                    .size(50.dp, 62.dp).semantics { contentDescription = marker.title }
                    .clickable(role = Role.Button) { onMonumentClick(marker.card) },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    scale(size.width / 50f, size.height / 62f, Offset.Zero) {
                        val pin = Path().apply {
                            moveTo(25f, 61f)
                            cubicTo(17f, 50f, 2f, 37f, 2f, 25f)
                            cubicTo(2f, 12f, 12f, 1f, 25f, 1f)
                            cubicTo(38f, 1f, 48f, 12f, 48f, 25f)
                            cubicTo(48f, 37f, 33f, 50f, 25f, 61f)
                            close()
                        }
                        drawPath(pin, Rust)
                        drawPath(pin, Card, style = Stroke(width = 2.5f))
                    }
                }
                GuideGlyph(Glyph.CHURCH, Ink, Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(23.dp))
            }
        }
        map.routePoints.firstOrNull()?.let { start ->
            Text(
                map.routeLabel,
                Modifier.offset(maxWidth * start.x - 68.dp, (maxHeight * start.y + 12.dp).coerceAtMost(maxHeight - 42.dp))
                    .background(Card, RoundedCornerShape(5.dp)).padding(horizontal = 18.dp, vertical = 5.dp),
                color = Muted, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium,
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 20.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("© OpenStreetMap contributors", color = Muted, fontSize = 10.sp, lineHeight = 13.sp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(62.dp, 7.dp)) {
                    val bar = Path().apply {
                        moveTo(0f, 0f); lineTo(0f, size.height * .8f); lineTo(size.width, size.height * .8f); lineTo(size.width, 0f)
                    }
                    drawPath(bar, Muted, style = Stroke(width = 1.dp.toPx()))
                }
                Text("50 м", color = Muted, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
private fun MonumentCard(marker: Marker, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Park)
        .clip(RoundedCornerShape(topStart = 19.dp, topEnd = 19.dp)).background(Card)
        .padding(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 17.dp)) {
        SheetHandle()
        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.Top) {
            GrayCanvas(Modifier.size(77.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.padding(start = 15.dp, top = 4.dp)) {
                Text(marker.title, color = Ink, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.65).sp)
                Text(marker.subtitle, Modifier.padding(top = 5.dp), color = Muted, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
        Spacer(Modifier.height(21.dp))
        GuideAction("Открыть карточку", onOpen, icon = Glyph.CHURCH, arrow = true)
    }
}

@Composable
internal fun ColumnScope.SheetHandle() {
    Box(Modifier.align(Alignment.CenterHorizontally).size(34.dp, 4.dp).background(Border, RoundedCornerShape(2.dp)))
}

@Composable
internal fun MonumentYears(built: Int, dismantled: Int) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(.437f)) {
            Text(built.toString(), color = Ink, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
            Text("Построена", Modifier.padding(top = 4.dp), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Column {
            Text(dismantled.toString(), color = Ink, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
            Text("Разобрана", Modifier.padding(top = 4.dp), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun MonumentDetails(monument: Monument, onBack: () -> Unit, onAr: () -> Unit, onSource: () -> Unit) {
    GuidePage {
        GuideHeader("О памятнике", onBack)
        Spacer(Modifier.height(21.dp))
        Text(monument.ensemble, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(7.dp))
        Text(monument.title, color = Ink, fontSize = 35.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.05).sp)
        Spacer(Modifier.height(14.dp))
        GrayCanvas(Modifier.fillMaxWidth().aspectRatio(350f / 219f).clip(RoundedCornerShape(11.dp)))
        Spacer(Modifier.height(17.dp))
        MonumentYears(monument.builtYear, monument.dismantledYear)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Border)
        Spacer(Modifier.height(16.dp))
        Text(monument.description, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(34.dp))
        Row(Modifier.padding(start = 13.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(36.dp).background(Color(0xFFE3EEE1), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                GuideGlyph(Glyph.PIN, Color(0xFF315547), Modifier.size(19.dp))
            }
            Column(Modifier.padding(top = 1.dp)) {
                Text(monument.address, color = Ink, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Ищите контур у информационного стенда.", Modifier.padding(top = 5.dp), color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Spacer(Modifier.weight(1f))
        GuideAction("Посмотреть в AR", onAr, icon = Glyph.SCAN, arrow = true)
        Spacer(Modifier.height(10.dp))
        GuideAction("Как восстановили облик", onSource, ActionStyle.OUTLINE, icon = Glyph.DOCUMENT)
    }
}
