package com.foodmind.foodmind_android

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.foodmind.foodmind_android.core.network.CatalogueCoordinates
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.google.gson.Gson
import kotlinx.coroutines.launch

@Composable
fun OneMapPlaceMap(client: FoodMindApiClient, placeId: String, placeName: String, destination: CatalogueCoordinates) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var map by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    fun routeFromDevice() {
        val location = currentCoordinates(context)
        if (location == null) { status = "Current location is unavailable. Turn on location and try again."; return }
        if (!isInSingapore(location.latitude, location.longitude)) {
            status = "Walking routes are available only when your current location is in Singapore."
            return
        }
        scope.launch {
            runCatching { client.walkingRoute(placeId, location.latitude, location.longitude) }
                .onSuccess { route ->
                    status = "Walking route: %.1f km · %d min. Your location is not saved.".format(route.distanceMeters / 1000.0, maxOf(1L, route.durationSeconds / 60))
                    map?.evaluateJavascript("window.foodMindSetRoute(${Gson().toJson(route.coordinates)})", null)
                }
                .onFailure { status = "Walking directions are temporarily unavailable." }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            routeFromDevice()
        } else {
            status = "Location permission is needed to show a walking route. Your location is not saved."
        }
    }
    FoodMindSurfaceCard {
        Column {
            Text("OneMap location")
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(240.dp).padding(top = 10.dp),
                factory = {
                    WebView(it).also { view ->
                        view.settings.javaScriptEnabled = true
                        view.settings.allowFileAccess = false
                        view.settings.allowContentAccess = false
                        view.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                                request?.url?.host != "www.onemap.gov.sg"
                        }
                        view.loadDataWithBaseURL("https://www.onemap.gov.sg/", mapHtml(placeName, destination), "text/html", "UTF-8", null)
                        map = view
                    }
                },
            )
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    routeFromDevice()
                } else {
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Icon(Icons.Outlined.LocationOn, null); Text("Use my location for walking route", Modifier.padding(start = 8.dp)) }
            status?.let { Text(it, color = FoodMindMuted, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

private fun isInSingapore(latitude: Double, longitude: Double): Boolean =
    latitude in 1.13..1.48 && longitude in 103.60..104.10

private fun mapHtml(name: String, point: CatalogueCoordinates): String {
    val safeName = name.replace("\\", "\\\\").replace("'", "\\'")
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="https://www.onemap.gov.sg/web-assets/libs/leaflet/leaflet.css"><style>html,body,#map{height:240px;margin:0}</style></head><body><div id="map"></div><script src="https://www.onemap.gov.sg/web-assets/libs/leaflet/onemap-leaflet.js"></script><script>const map=L.map('map',{scrollWheelZoom:false}).setView([${point.latitude},${point.longitude}],16);L.tileLayer('https://www.onemap.gov.sg/maps/tiles/Default/{z}/{x}/{y}.png',{maxZoom:18,minZoom:11,attribution:'OneMap © Singapore Land Authority'}).addTo(map);L.marker([${point.latitude},${point.longitude}]).addTo(map).bindPopup('$safeName');window.foodMindSetRoute=(points)=>{if(window.route){map.removeLayer(window.route)};window.route=L.polyline(points.map(p=>[p[1],p[0]]),{color:'#167c5a',weight:5}).addTo(map);map.fitBounds(window.route.getBounds(),{padding:[20,20]})};</script></body></html>"""
}
