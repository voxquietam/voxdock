package com.vox.voxdock.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vox.voxdock.data.AppSettings
import com.vox.voxdock.ui.theme.LocalVoxPalette

private val UNSEEN_ALL = listOf(
    "sea" to "Sea",
    "air" to "Air / Dust",
    "quake" to "Quakes",
    "space" to "Space / Kp",
    "bio" to "Wildlife"
)

@Composable
fun SettingsOverlay(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onClose: () -> Unit
) {
    val p = LocalVoxPalette.current
    val context = LocalContext.current

    var workerUrl by remember { mutableStateOf(settings.workerUrl) }
    var apiToken by remember { mutableStateOf(settings.apiToken) }
    var city by remember { mutableStateOf(settings.city) }
    var street by remember { mutableStateOf(settings.powerStreet) }
    var house by remember { mutableStateOf(settings.powerHouse) }
    var queue by remember { mutableStateOf(settings.powerQueue) }
    var autoDock by remember { mutableStateOf(settings.autoDockOnCharge) }
    var nightOled by remember { mutableStateOf(settings.nightOledEnabled) }
    var enabledUnseen by remember { mutableStateOf(settings.enabledUnseen) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VOXDOCK SETTINGS",
                color = p.text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .border(1.dp, p.border, RoundedCornerShape(10.dp))
                        .background(p.card, RoundedCornerShape(10.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("CANCEL", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .background(p.weatherBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            onSave(
                                settings.copy(
                                    workerUrl = workerUrl.trim(),
                                    apiToken = apiToken.trim(),
                                    city = city.trim().ifBlank { "Odesa" },
                                    powerStreet = street.trim(),
                                    powerHouse = house.trim(),
                                    powerQueue = queue.trim(),
                                    autoDockOnCharge = autoDock,
                                    nightOledEnabled = nightOled,
                                    enabledUnseen = enabledUnseen
                                )
                            )
                            onClose()
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        "SAVE",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Hub connection
        Text("CLOUDFLARE WORKER HUB", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        OutlinedTextField(
            value = workerUrl,
            onValueChange = { workerUrl = it },
            label = { Text("Worker URL (https://voxdock....workers.dev)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = apiToken,
            onValueChange = { apiToken = it },
            label = { Text("Bearer Token (VOXDOCK_TOKEN)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Address & DTEK queue
        Text("LOCATION & POWER OUTAGES (DTEK)", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = queue,
                onValueChange = { queue = it },
                label = { Text("Queue (e.g. 3.1)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = street,
                onValueChange = { street = it },
                label = { Text("Street") },
                singleLine = true,
                modifier = Modifier.weight(2f)
            )
            OutlinedTextField(
                value = house,
                onValueChange = { house = it },
                label = { Text("House") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Unseen categories
        Text("UNSEEN (NEZRYME) MODULES", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            UNSEEN_ALL.forEach { (key, label) ->
                val active = enabledUnseen.contains(key)
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (active) p.unseenBorder else p.border,
                            RoundedCornerShape(10.dp)
                        )
                        .background(
                            if (active) p.unseenBg else p.card,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            enabledUnseen = if (active) enabledUnseen - key else enabledUnseen + key
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (active) p.text else p.muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Dock & OLED switches
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Auto Dock Mode on landscape charge", color = p.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Switch(checked = autoDock, onCheckedChange = { autoDock = it })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Night OLED mode (23:00 - 07:00)", color = p.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Switch(checked = nightOled, onCheckedChange = { nightOled = it })
        }

        // Notification access button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, p.border, RoundedCornerShape(12.dp))
                .background(p.card, RoundedCornerShape(12.dp))
                .clickable {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "GRANT NOTIFICATION ACCESS (TG / ZOOM / KSU24)",
                color = p.text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
