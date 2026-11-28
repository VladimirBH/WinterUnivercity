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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ar.core.ArCoreApk

@Composable
internal fun CameraRequestSheet(
    builtYear: Int?,
    dismantledYear: Int?,
    path: String,
    api: GuideApi,
    onDismiss: () -> Unit,
    onCamera: (ArGuide) -> Unit,
    onWithoutCamera: (ArGuide) -> Unit,
) {
    val context = LocalContext.current
    var denied by rememberSaveable(path) { mutableStateOf(false) }
    var cameraRequested by rememberSaveable(path) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        cameraRequested = granted
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
                val availability by produceState<ArCoreApk.Availability?>(null, context, guide.model) {
                    if (guide.model != null) {
                        ArCoreApk.getInstance().checkAvailabilityAsync(context) { value = it }
                    }
                }
                LaunchedEffect(cameraRequested) {
                    if (cameraRequested) {
                        cameraRequested = false
                        if (guide.model != null) onCamera(guide)
                    }
                }
                Text("Посмотреть в AR", color = Ink, fontSize = 31.sp, lineHeight = 37.sp, letterSpacing = (-.8).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(13.dp))
                if (guide.model == null) {
                    Text("3D-модель этого памятника недоступна.", color = Muted)
                } else if (availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
                    Text("Устройство не поддерживает ARCore.", color = Muted)
                } else if (availability == null) {
                    Text("Проверяем поддержку AR…", color = Muted)
                } else {
                    Text(
                        "Наведите камеру на пол или стол, чтобы разместить модель храма.",
                        color = Muted, fontSize = 15.sp, lineHeight = 22.sp,
                    )
                    Spacer(Modifier.height(20.dp))
                    if (denied) {
                        Text("Разрешите доступ к камере в настройках или продолжите без камеры.", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    val hasPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    GuideAction(
                        if (hasPermission) "Открыть камеру" else "Разрешить камеру",
                        {
                            if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                onCamera(guide)
                            } else {
                                launcher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        icon = Glyph.CAMERA, arrow = true,
                    )
                }
                Spacer(Modifier.height(10.dp))
                GuideAction("Смотреть без камеры", { onWithoutCamera(guide) }, ActionStyle.OUTLINE)
                GuideAction("Вернуться к памятнику", onDismiss, ActionStyle.PLAIN)
            }
        }
    }
}
