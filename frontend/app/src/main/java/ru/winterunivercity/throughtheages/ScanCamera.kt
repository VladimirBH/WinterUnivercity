package ru.winterunivercity.throughtheages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CameraRequestSheet(
    builtYear: Int,
    dismantledYear: Int,
    path: String,
    api: GuideApi,
    onDismiss: () -> Unit,
    onCamera: (ArGuide) -> Unit,
    onWithoutCamera: (ArGuide) -> Unit,
) {
    val context = LocalContext.current
    var denied by rememberSaveable(path) { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ArGuide?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) pending?.let(onCamera)
        pending = null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.matchParentSize().background(Ink.copy(alpha = .72f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Card)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        ) {
            SheetHandle()
            Spacer(Modifier.height(14.dp))
            MonumentYears(builtYear, dismantledYear)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Border)
            GuideData(path, onDismiss, api::ar, modal = true) { guide ->
                Text(guide.title, color = Ink, fontSize = 31.sp, lineHeight = 37.sp, letterSpacing = (-.8).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(13.dp))
                Text(guide.instruction + "\nНа месте фундамента появится модель\nхрама.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(25.dp))
                if (denied) {
                    Text("Разрешите доступ к камере в настройках или продолжите без камеры.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                GuideAction(
                    "Разрешить камеру",
                    {
                        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            onCamera(guide)
                        } else {
                            pending = guide
                            launcher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    icon = Glyph.CAMERA, arrow = true,
                )
                Spacer(Modifier.height(10.dp))
                GuideAction("Смотреть без камеры", { onWithoutCamera(guide) }, ActionStyle.OUTLINE)
                GuideAction("Вернуться к памятнику", onDismiss, ActionStyle.PLAIN)
            }
        }
    }
}

@Composable
internal fun ScanScreen(onBack: () -> Unit, onShowComparison: () -> Unit, onMissingMark: () -> Unit) {
    Column(Modifier.fillMaxSize().background(CanvasGray)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            CameraHeaderShade()
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 25.dp)) {
                GuideHeader("Поиск метки", onBack, dark = true)
            }
            Box(
                Modifier.align(Alignment.Center).offset(y = maxHeight * .10f)
                    .size(width = minOf(232.dp, maxWidth * .72f), height = minOf(336.dp, maxHeight * .72f)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val arm = 22.dp.toPx()
                    val right = size.width
                    val bottom = size.height
                    val corners = Path().apply {
                        moveTo(0f, arm)
                        lineTo(0f, 0f)
                        lineTo(arm, 0f)
                        moveTo(right - arm, 0f)
                        lineTo(right, 0f)
                        lineTo(right, arm)
                        moveTo(right, bottom - arm)
                        lineTo(right, bottom)
                        lineTo(right - arm, bottom)
                        moveTo(arm, bottom)
                        lineTo(0f, bottom)
                        lineTo(0f, bottom - arm)
                    }
                    drawPath(corners, Card, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Night).padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 16.dp)) {
            Text("Наведите на метку", color = Card, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
            Spacer(Modifier.height(5.dp))
            Text("Совместите метку у стенда с рамкой.", color = NightText, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(25.dp))
            GuideAction("Показать сравнение", onShowComparison, ActionStyle.LIME, Glyph.SCAN, arrow = true)
            Spacer(Modifier.height(9.dp))
            GuideAction("Не вижу метку", onMissingMark, ActionStyle.DARK_OUTLINE)
        }
    }
}
