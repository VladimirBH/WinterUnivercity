package ru.winterunivercity.throughtheages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun CityMap(points: List<Marker>, modifier: Modifier, onMonumentClick: (String) -> Unit) {
    OpenStreetMap(points, modifier, onMonumentClick)
}
