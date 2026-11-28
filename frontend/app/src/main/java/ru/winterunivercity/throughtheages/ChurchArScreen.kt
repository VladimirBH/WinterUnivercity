package ru.winterunivercity.throughtheages

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.DeadlineExceededException
import com.google.ar.core.exceptions.NotTrackingException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.placementHit
import io.github.sceneview.math.Position
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.CancellationException

@Composable
internal fun ChurchArScreen(
    title: String,
    model: ChurchModel,
    onBack: () -> Unit,
    onWithoutCamera: () -> Unit,
) {
    var status by remember(model) { mutableStateOf("Загружаем 3D-модель…") }
    Column(Modifier.fillMaxSize().background(Night)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            ChurchArScene(model, onStatus = { status = it })
            CameraHeaderShade()
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                GuideHeader(title, onBack, dark = true)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(status, color = Card)
            GuideAction("Смотреть без камеры", onWithoutCamera, ActionStyle.DARK_OUTLINE)
        }
    }
}

@Composable
private fun ChurchArScene(model: ChurchModel, onStatus: (String) -> Unit) {
    val engine = rememberEngine()
    val loader = rememberModelLoader(engine)
    val loaded by produceState<Result<ModelInstance>?>(null, loader, model.asset) {
        value = try {
            Result.success(checkNotNull(loader.loadModelInstance(model.asset)))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w("ChurchAr", "Could not load ${model.asset}", exception)
            Result.failure(exception)
        }
    }
    val instance = loaded?.getOrNull()
    var sessionError by remember(model) { mutableStateOf<Exception?>(null) }
    var anchor by remember(model) { mutableStateOf<Anchor?>(null) }
    var cameraTracking by remember { mutableStateOf(false) }
    var surfaceDetected by remember { mutableStateOf(false) }
    val latestFrame = remember { mutableStateOf<Frame?>(null) }
    val status = when {
        sessionError is UnavailableDeviceNotCompatibleException -> "Устройство не поддерживает ARCore."
        sessionError != null -> "Не удалось запустить AR. Проверьте камеру и сервисы Google Play для AR."
        loaded?.isFailure == true -> "Не удалось загрузить 3D-модель."
        instance == null -> "Загружаем 3D-модель…"
        !cameraTracking -> "Плавно перемещайте камеру над полом или столом."
        anchor != null -> "${model.label} · Обойдите модель, чтобы рассмотреть храм."
        surfaceDetected -> "Коснитесь подсвеченной поверхности, чтобы поставить модель."
        else -> "Ищем поверхность. Продолжайте плавно перемещать камеру."
    }
    SideEffect { onStatus(status) }
    if (instance == null || sessionError != null) return

    // Place a 70 cm miniature with its base on the tracked surface.
    val baseOffset = remember(instance) {
        val bounds = instance.asset.boundingBox
        val center = bounds.center
        val half = bounds.halfExtent
        val scale = MODEL_SIZE_METERS / (2f * half.max())
        Position(-center[0] * scale, (half[1] - center[1]) * scale, -center[2] * scale)
    }
    ARSceneView(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = loader,
        planeRenderer = anchor == null,
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
        onSessionUpdated = { _, frame ->
            latestFrame.value = frame
            cameraTracking = frame.camera.trackingState == TrackingState.TRACKING
            if (!surfaceDetected && cameraTracking) {
                surfaceDetected = frame.getUpdatedTrackables(Plane::class.java).any {
                    it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.trackingState == TrackingState.TRACKING
                }
            }
        },
        onSessionPaused = {
            latestFrame.value = null
            cameraTracking = false
        },
        onSessionFailed = { exception ->
            Log.w("ChurchAr", "Could not start ARCore", exception)
            sessionError = exception
        },
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { tap, _ ->
                val frame = latestFrame.value
                if (anchor == null && frame != null) {
                    try {
                        val hits = frame.hitTest(tap.x, tap.y).filter {
                            (it.trackable as? Plane)?.type == Plane.Type.HORIZONTAL_UPWARD_FACING
                        }
                        anchor = placementHit(frame, hits, instantPlacement = false)?.createAnchor()
                    } catch (exception: NotTrackingException) {
                        Log.d("ChurchAr", "Surface tracking changed during placement", exception)
                    } catch (exception: DeadlineExceededException) {
                        Log.d("ChurchAr", "AR frame expired during placement", exception)
                    }
                }
            },
        ),
    ) {
        anchor?.let { placement ->
            AnchorNode(anchor = placement) {
                ModelNode(
                    modelInstance = instance,
                    autoAnimate = false,
                    scaleToUnits = MODEL_SIZE_METERS,
                    position = baseOffset,
                )
            }
        }
    }
}

private const val MODEL_SIZE_METERS = .7f
