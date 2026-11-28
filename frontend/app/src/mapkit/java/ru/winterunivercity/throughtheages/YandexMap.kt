package ru.winterunivercity.throughtheages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.ScreenPoint
import com.yandex.mapkit.ScreenRect
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.SizeChangedListener
import com.yandex.mapkit.map.TextStyle
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import java.lang.ref.WeakReference

private const val YANDEX_MAPS_TERMS = "https://yandex.ru/legal/maps_termsofuse/"

@Composable
internal fun CityMap(points: List<Marker>, modifier: Modifier, onMonumentClick: (String) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val uriHandler = LocalUriHandler.current
    val monuments = remember(points) {
        points.mapNotNull { marker ->
            val latitude = marker.latitude ?: return@mapNotNull null
            val longitude = marker.longitude ?: return@mapNotNull null
            YandexMonument(latitude, longitude, marker.title, marker.card)
        }
    }
    val mapUrl = remember(monuments) { yandexMapsUrl(monuments) }

    Column(modifier) {
        AndroidView(
            factory = MapKitViewFactory::create,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onRelease = YandexGuideMapView::release,
            update = { view ->
                view.onMonumentClick = onMonumentClick
                view.bind(lifecycle)
                view.setMonuments(monuments)
            },
        )

        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Условия использования сервиса Яндекс.Карты",
                Modifier.weight(1f).clickable { uriHandler.openUri(YANDEX_MAPS_TERMS) }
                    .padding(vertical = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
            Text(
                "Открыть в Картах",
                Modifier.clickable { uriHandler.openUri(mapUrl) }.padding(vertical = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

private data class YandexMonument(val latitude: Double, val longitude: Double, val title: String, val card: String) {
    fun point() = Point(latitude, longitude)
}

private val novgorodCenter = Point(58.521, 31.275)

private fun yandexMapsUrl(monuments: List<YandexMonument>): String {
    val center = if (monuments.isEmpty()) novgorodCenter else Point(
        monuments.map { it.latitude }.average(),
        monuments.map { it.longitude }.average(),
    )
    return Uri.parse("https://yandex.ru/maps/").buildUpon()
        .appendQueryParameter("ll", "${center.longitude},${center.latitude}")
        .appendQueryParameter("z", "16")
        .apply {
            if (monuments.isNotEmpty()) {
                val pins = monuments.joinToString("~") { "${it.longitude},${it.latitude},pm2rdm" }
                appendQueryParameter("pt", pins)
            }
        }
        .build().toString()
}

private object MapKitViewFactory {
    init {
        // The MapView constructor initializes MapKit, so configure its key first.
        MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
    }

    fun create(context: Context) = YandexGuideMapView(context)
}

private class YandexGuideMapView(context: Context) : MapView(context), DefaultLifecycleObserver {
    var onMonumentClick: (String) -> Unit = {}

    private var lifecycle: Lifecycle? = null
    private var started = false
    private var monuments = emptyList<YandexMonument>()
    private var needsCameraFit = true
    private val pinIcon = context.getDrawable(R.drawable.yandex_map_pin)!!.let { drawable ->
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        ImageProvider.fromBitmap(bitmap)
    }
    // MapKit retains listeners weakly, so keep them alive for as long as the view exists.
    private val markerTapListener = MapObjectTapListener { marker, _ ->
        val card = marker.userData as? String ?: return@MapObjectTapListener false
        onMonumentClick(card)
        true
    }
    private val markerTapReference = WeakReference(markerTapListener)
    private val sizeListener = SizeChangedListener { _, width, height -> fitCamera(width, height) }
    private val sizeReference = WeakReference(sizeListener)

    init {
        mapWindow.addSizeChangedListener(sizeReference)
    }

    fun bind(ownerLifecycle: Lifecycle) {
        if (lifecycle === ownerLifecycle) return
        lifecycle?.removeObserver(this)
        stopRendering()
        lifecycle = ownerLifecycle
        ownerLifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (started) return
        MapKitFactory.getInstance().onStart()
        onStart()
        started = true
    }

    override fun onStop(owner: LifecycleOwner) {
        stopRendering()
    }

    fun setMonuments(next: List<YandexMonument>) {
        if (next == monuments) return
        monuments = next
        val markerLayer = mapWindow.map.mapObjects
        markerLayer.clear()
        val iconStyle = IconStyle().setAnchor(PointF(0.5f, 0.98f))
        val labelStyle = TextStyle().setPlacement(TextStyle.Placement.BOTTOM)
            .setOffsetFromIcon(true).setOffset(3f).setSize(12f).setTextOptional(true)
        for (monument in monuments) {
            markerLayer.addPlacemark().apply {
                geometry = monument.point()
                setIcon(pinIcon, iconStyle)
                setText(monument.title, labelStyle)
                userData = monument.card
                addTapListener(markerTapReference)
            }
        }
        needsCameraFit = true
        fitCamera(mapWindow.width(), mapWindow.height())
    }

    fun release() {
        lifecycle?.removeObserver(this)
        lifecycle = null
        mapWindow.removeSizeChangedListener(sizeReference)
        stopRendering()
        destroy()
    }

    private fun stopRendering() {
        if (!started) return
        onStop()
        MapKitFactory.getInstance().onStop()
        started = false
    }

    private fun fitCamera(width: Int, height: Int) {
        if (!needsCameraFit || width <= 0 || height <= 0) return
        val coordinates = monuments.distinctBy { it.latitude to it.longitude }
        val map = mapWindow.map
        val camera = when (coordinates.size) {
            0 -> CameraPosition(novgorodCenter, 14f, 0f, 0f)
            1 -> CameraPosition(coordinates.single().point(), 17f, 0f, 0f)
            else -> {
                val bounds = BoundingBox(
                    Point(coordinates.minOf { it.latitude }, coordinates.minOf { it.longitude }),
                    Point(coordinates.maxOf { it.latitude }, coordinates.maxOf { it.longitude }),
                )
                val left = width * 0.16f
                val top = height * 0.2f
                val focus = ScreenRect(ScreenPoint(left, top), ScreenPoint(width - left, height - top))
                val fitted = map.cameraPosition(Geometry.fromBoundingBox(bounds), focus)
                CameraPosition(fitted.target, fitted.zoom.coerceAtMost(17f), 0f, 0f)
            }
        }
        needsCameraFit = false
        map.move(camera)
    }
}
