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
import com.google.ar.core.ArCoreApk

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
    var cameraGranted by rememberSaveable(path) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        cameraGranted = granted
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
                val availability by produceState<ArCoreApk.Availability?>(null, context, guide.model) {
                    if (guide.model != null) ArCoreApk.getInstance().checkAvailabilityAsync(context) { value = it }
                }
                LaunchedEffect(guide, cameraGranted) {
                    if (cameraGranted) {
                        cameraGranted = false
                        if (guide.model != null) onCamera(guide)
                    }
                }
                Text(
                    "Откройте камеру", color = Ink, fontSize = 31.sp, lineHeight = 38.sp,
                    letterSpacing = (-.6).sp, fontFamily = GuideHeadingFont, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                val canOpen = guide.model != null && availability?.isSupported == true
                val instruction = when {
                    guide.model == null -> "3D-модель этого памятника недоступна."
                    availability == null -> "Проверяем поддержку AR…"
                    !canOpen -> "AR недоступна на этом устройстве. Можно сравнить эпохи без камеры."
                    else -> "Наведите камеру на пол или стол, чтобы разместить модель храма."
                }
                Text(instruction, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(27.dp))
                if (canOpen) {
                    if (denied) {
                        Text("Разрешите доступ к камере в настройках или продолжите без камеры.", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    val hasPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    GuideAction(
                        if (hasPermission) "Открыть камеру" else "Разрешить доступ к камере",
                        {
                            if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                onCamera(guide)
                            } else {
                                launcher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        style = ActionStyle.ACCENT, icon = Glyph.CAMERA, arrow = true, radius = 16,
                    )
                }
                Spacer(Modifier.height(10.dp))
                GuideAction("Смотреть без камеры", { onWithoutCamera(guide) }, ActionStyle.OUTLINE, radius = 16, height = 49)
                Spacer(Modifier.height(6.dp))
                GuideAction("Вернуться к памятнику", onDismiss, ActionStyle.PLAIN, height = 44)
            }
        }
    }
}
