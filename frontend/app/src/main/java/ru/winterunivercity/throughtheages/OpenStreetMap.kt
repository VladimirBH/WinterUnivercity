package ru.winterunivercity.throughtheages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as MapPin

@Composable
internal fun OpenStreetMap(
    points: List<Marker>,
    modifier: Modifier,
    onMonumentClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val uriHandler = LocalUriHandler.current
    val openMonument by rememberUpdatedState(onMonumentClick)
    val map = remember(context) {
        Configuration.getInstance().apply {
            osmdroidBasePath = File(context.cacheDir, "osm")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            userAgentValue = "WinterUnivercity/${BuildConfig.VERSION_NAME} (https://github.com/VladimirBH/WinterUnivercity)"
        }
        MapView(context).apply {
            setDestroyMode(false)
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(17.0)
        }
    }

    DisposableEffect(map, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onPause()
            map.onDetach()
        }
    }

    LaunchedEffect(map, points) {
        val locations = points.mapNotNull { marker ->
            val latitude = marker.latitude ?: return@mapNotNull null
            val longitude = marker.longitude ?: return@mapNotNull null
            marker to GeoPoint(latitude, longitude)
        }
        map.overlays.removeAll { it is MapPin }
        for ((marker, point) in locations) {
            map.overlays.add(MapPin(map).apply {
                position = point
                title = marker.title
                icon = ContextCompat.getDrawable(context, R.drawable.city_map_pin)
                setAnchor(MapPin.ANCHOR_CENTER, MapPin.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    openMonument(marker.card)
                    true
                }
            })
        }
        if (locations.isNotEmpty()) {
            map.controller.setCenter(GeoPoint(
                locations.map { it.second.latitude }.average(),
                locations.map { it.second.longitude }.average(),
            ))
        }
        map.invalidate()
    }

    Box(modifier) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
        Text(
            "© OpenStreetMap contributors",
            color = Ink,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomStart)
                .background(Card)
                .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
