package com.vox.voxdock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vox.voxdock.data.WeatherPayload
import com.vox.voxdock.ui.theme.LocalVoxPalette

fun weatherIcon(code: Int): String = when (code) {
    0 -> "☀"
    in 1..3 -> "🌤"
    in 45..48 -> "🌫"
    in 51..67 -> "🌧"
    in 71..77 -> "❄"
    in 80..82 -> "🌦"
    in 95..99 -> "⛈"
    else -> "☁"
}

@Composable
fun WeatherForecastOverlay(
    weather: WeatherPayload,
    onClose: () -> Unit
) {
    val p = LocalVoxPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${weather.city.uppercase()} · 10-DAY FORECAST",
                    color = p.text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                val sea = weather.current.seaTemp?.let { " · SEA ${it}°C" }.orEmpty()
                Text(
                    text = "NOW ${weather.current.temp}°C (FEELS ${weather.current.feelsLike}°C) · WIND ${weather.current.windSpeed} km/h$sea",
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
            Box(
                modifier = Modifier
                    .border(1.dp, p.border, RoundedCornerShape(10.dp))
                    .background(p.card, RoundedCornerShape(10.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "CLOSE ✕",
                    color = p.text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(weather.daily) { day ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, p.border, RoundedCornerShape(14.dp))
                        .background(p.card, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${weatherIcon(day.weatherCode)}  ${day.date}",
                        color = p.text,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "RAIN ${day.precipProb}% · WIND ${day.windMax}",
                        color = p.muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${day.tempMin}° / ${day.tempMax}°",
                        color = p.text,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
