package ru.winterunivercity.throughtheages

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class Marker(
    val title: String,
    val subtitle: String,
    val card: String,
    val position: Offset,
    val latitude: Double?,
    val longitude: Double?,
)

internal data class GuideMap(
    val title: String,
    val city: String,
    val markers: List<Marker>,
    val routeLabel: String,
    val routePoints: List<Offset>,
)

internal data class Monument(
    val title: String,
    val ensemble: String,
    val builtYear: Int?,
    val dismantledYear: Int?,
    val description: String,
    val address: String,
    val source: String,
    val ar: String,
)

internal data class Era(
    val id: String,
    val label: String,
    val title: String,
    val description: String,
)

internal data class Comparison(val title: String, val eras: List<Era>, val source: String, val ar: String)

internal data class HistoricalSource(
    val title: String,
    val caption: String,
    val credit: String,
    val collection: String,
    val explanation: String,
)

internal data class ArGuide(
    val fallbackUrl: String,
    val model: ChurchModel?,
)

internal data class ChurchModel(val asset: String, val label: String) : java.io.Serializable

internal class GuideApi(context: Context) {
    private val assets = context.applicationContext.assets

    suspend fun map(path: String): GuideMap = document(path) {
        val route = getJSONObject("route")
        GuideMap(
            getString("title"),
            getString("city"),
            getJSONArray("markers").items { index ->
                val marker = getJSONObject(index)
                Marker(
                    marker.getString("title"),
                    marker.getString("subtitle"),
                    marker.getString("card"),
                    Offset(marker.getDouble("x").toFloat(), marker.getDouble("y").toFloat()),
                    marker.optDouble("latitude").takeIf { it in -90.0..90.0 },
                    marker.optDouble("longitude").takeIf { it in -180.0..180.0 },
                )
            },
            route.getString("label"),
            route.getJSONArray("points").items { index ->
                val point = getJSONArray(index)
                Offset(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
            },
        )
    }

    suspend fun monument(path: String): Monument = document(path) {
        Monument(
            getString("title"), getString("ensemble"),
            getNullableYear("builtYear"), getNullableYear("dismantledYear"), getString("description"),
            getString("address"),
            getString("source"), getString("ar"),
        )
    }

    suspend fun comparison(path: String): Comparison = document(path) {
        Comparison(
            getString("title"),
            getJSONArray("views").items { index ->
                val era = getJSONObject(index)
                Era(
                    era.getString("id"), era.getString("label"), era.getString("title"),
                    era.getString("description"),
                )
            },
            getString("source"),
            getString("ar"),
        )
    }

    suspend fun source(path: String): HistoricalSource = document(path) {
        HistoricalSource(
            getString("title"), getString("caption"),
            getString("credit"), getString("collection"), getString("explanation"),
        )
    }

    suspend fun ar(path: String): ArGuide = document(path) {
        val models = optJSONArray("models")?.items { getJSONObject(it) }.orEmpty()
        val model = models.firstNotNullOfOrNull {
            bundledModel(it.getString("modelUrl"), it.getString("label"))
        }
        ArGuide(
            getJSONObject("fallback").getString("url"),
            model ?: bundledModel(optString("modelUrl"), "Историческая реконструкция"),
        )
    }

    private fun bundledModel(url: String, label: String): ChurchModel? {
        val prefix = "/media/3dModeling/"
        if (!url.startsWith(prefix)) return null
        val filename = url.removePrefix(prefix)
        if (!filename.endsWith(".glb") || filename !in assets.list("models").orEmpty()) return null
        return ChurchModel("models/$filename", label)
    }

    private suspend fun <T> document(path: String, parse: JSONObject.() -> T): T = withContext(Dispatchers.IO) {
        if (!path.startsWith('/') || path.startsWith("//") ||
            path.any { it == '\\' || it == '?' || it == '#' || it.isISOControl() } ||
            path.split('/').any { it == "." || it == ".." }
        ) {
            throw IOException("Invalid catalog path")
        }
        if (BuildConfig.DEBUG) {
            try {
                val connection = URL("http://10.0.2.2:8080$path").openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 1_200
                    connection.readTimeout = 2_500
                    connection.instanceFollowRedirects = false
                    val status = connection.responseCode
                    if (status != HttpURLConnection.HTTP_OK) throw IOException("HTTP $status")
                    val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                        JSONObject(it.readText()).parse()
                    }
                    Log.d("GuideApi", "GET $path: HTTP $status")
                    return@withContext response
                } finally {
                    connection.disconnect()
                }
            } catch (_: IOException) {
                Log.d("GuideApi", "Using bundled data for $path")
            } catch (_: JSONException) {
                Log.d("GuideApi", "Invalid API response for $path, using bundled data")
            }
        }
        assets.open("catalog.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText()).getJSONObject(catalogKey(path)).parse()
        }
    }

    private fun catalogKey(path: String): String = when (path) {
        "/api/v1/map" -> "map"
        "/api/v1/monuments/tserkov-lazarya" -> "card"
        "/api/v1/monuments/tserkov-lazarya/comparison" -> "comparison"
        "/api/v1/monuments/tserkov-lazarya/source" -> "source"
        "/api/v1/monuments/tserkov-lazarya/ar" -> "ar"
        else -> throw IOException("Catalog entry unavailable offline")
    }
}

private fun JSONObject.getNullableYear(key: String): Int? = if (isNull(key)) null else getInt(key)

private inline fun <T> JSONArray.items(parse: JSONArray.(Int) -> T): List<T> =
    List(length()) { parse(it) }
