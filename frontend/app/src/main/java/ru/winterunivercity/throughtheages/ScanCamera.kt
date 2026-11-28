package ru.winterunivercity.throughtheages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CameraRequestSheet(
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
            Modifier.matchParentSize().background(Scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Card)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 6.dp),
        ) {
            SheetHandle()
            Spacer(Modifier.height(12.dp))
            GuideImage(R.drawable.camera_badge, Modifier.size(52.dp), scale = ContentScale.Fit)
            Spacer(Modifier.height(23.dp))
            GuideData(path, onDismiss, api::ar, modal = true) { guide ->
                Text(guide.title, color = Ink, fontSize = 31.sp, lineHeight = 38.sp, letterSpacing = (-.6).sp, fontFamily = GuideHeadingFont, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text(guide.instruction + "\nНа месте фундамента появится модель\nхрама.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(27.dp))
                if (denied) {
                    Text("Разрешите доступ к камере в настройках или продолжите без камеры.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                GuideAction(
                    "Разрешить доступ к камере",
                    {
                        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            onCamera(guide)
                        } else {
                            pending = guide
                            launcher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    style = ActionStyle.ACCENT, icon = Glyph.CAMERA, arrow = true, radius = 16,
                )
                Spacer(Modifier.height(10.dp))
                GuideAction("Смотреть без камеры", { onWithoutCamera(guide) }, ActionStyle.OUTLINE, radius = 16, height = 49)
                Spacer(Modifier.height(6.dp))
                GuideAction("Вернуться к памятнику", onDismiss, ActionStyle.PLAIN, height = 44)
            }
        }
    }
}

@Composable
internal fun ScanScreen(onBack: () -> Unit, onShowComparison: () -> Unit, onMissingMark: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Night)) {
        GuideImage(R.drawable.lazarus_sign, Modifier.matchParentSize(), "Информационный стенд у церкви Лазаря")
        CameraHeaderShade()
        GuideImage(
            R.drawable.scan_corners,
            Modifier.align(Alignment.TopCenter).offset(y = maxHeight * (188f / 844f))
                .size(maxWidth * (236f / 390f), maxHeight * (338f / 844f)),
            scale = ContentScale.FillBounds,
        )
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 20.dp, top = 25.dp),
        ) {
            GuideHeader("Поиск метки", onBack, dark = true)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Night).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 11.dp),
        ) {
            Text("Наведите на метку", color = OnNight, fontSize = 29.sp, lineHeight = 36.sp, fontFamily = GuideHeadingFont, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp)
            Spacer(Modifier.height(5.dp))
            Text("Совместите метку у стенда с рамкой.", color = NightText, fontSize = 14.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(27.dp))
            GuideAction("Показать сравнение", onShowComparison, ActionStyle.ACCENT, Glyph.SCAN, arrow = true, height = 51)
            Spacer(Modifier.height(9.dp))
            GuideAction("Не вижу метку", onMissingMark, ActionStyle.DARK_OUTLINE, height = 49)
        }
    }
}
