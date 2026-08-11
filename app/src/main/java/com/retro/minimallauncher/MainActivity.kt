package com.retro.minimallauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RetroLauncherApp() }
    }
}

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val className: String
)

enum class Screen { HOME, MENU, DIALER, SETTINGS }

enum class RetroTheme(val title: String, val bg: Color, val fg: Color, val accent: Color) {
    CLASSIC("Classic LCD", Color(0xFFB7C69A), Color(0xFF182015), Color(0xFF7F9368)),
    GREEN("Green LCD", Color(0xFF9FBC83), Color(0xFF152114), Color(0xFF6F8E55)),
    AMBER("Amber", Color(0xFF211B11), Color(0xFFFFC766), Color(0xFF5C4520)),
    NIGHT("Night", Color(0xFF101510), Color(0xFFD9E8CE), Color(0xFF344233))
}

private const val PREFS = "retro_launcher_prefs"
private const val KEY_SELECTED = "selected_packages"
private const val KEY_THEME = "theme"
private const val KEY_HAPTICS = "haptics"

@Composable
fun RetroLauncherApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val allApps = remember { loadLaunchableApps(context) }

    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var themeName by remember { mutableStateOf(prefs.getString(KEY_THEME, RetroTheme.CLASSIC.name) ?: RetroTheme.CLASSIC.name) }
    var haptics by remember { mutableStateOf(prefs.getBoolean(KEY_HAPTICS, true)) }

    val initialSelected = remember(allApps) {
        val saved = prefs.getStringSet(KEY_SELECTED, null)
        saved?.toSet() ?: defaultSelectedPackages(allApps)
    }
    var selectedPackages by remember { mutableStateOf(initialSelected) }

    val theme = RetroTheme.entries.firstOrNull { it.name == themeName } ?: RetroTheme.CLASSIC
    val visibleApps = allApps.filter { it.packageName in selectedPackages }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = theme.bg) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    theme = theme,
                    onMenu = { screen = Screen.MENU },
                    onDialer = { screen = Screen.DIALER },
                    onHomeSettings = { launchHomeSettings(context) }
                )
                Screen.MENU -> MenuScreen(
                    apps = visibleApps,
                    selectedIndex = selectedIndex.coerceIn(0, (visibleApps.size - 1).coerceAtLeast(0)),
                    theme = theme,
                    haptics = haptics,
                    onMove = { delta ->
                        if (visibleApps.isNotEmpty()) {
                            selectedIndex = (selectedIndex + delta + visibleApps.size) % visibleApps.size
                        }
                    },
                    onOpen = {
                        visibleApps.getOrNull(selectedIndex)?.let { launchApp(context, it) }
                    },
                    onSettings = { screen = Screen.SETTINGS },
                    onBack = { screen = Screen.HOME },
                    onDialer = { screen = Screen.DIALER },
                    onHomeSettings = { launchHomeSettings(context) }
                )
                Screen.DIALER -> DialerScreen(
                    theme = theme,
                    haptics = haptics,
                    onCall = { number -> launchDialer(context, number) },
                    onBack = { screen = Screen.HOME }
                )
                Screen.SETTINGS -> SettingsScreen(
                    allApps = allApps,
                    selectedPackages = selectedPackages,
                    theme = theme,
                    haptics = haptics,
                    onToggleApp = { packageName ->
                        selectedPackages = if (packageName in selectedPackages) {
                            selectedPackages - packageName
                        } else {
                            selectedPackages + packageName
                        }
                        prefs.edit().putStringSet(KEY_SELECTED, selectedPackages).apply()
                        selectedIndex = 0
                    },
                    onTheme = {
                        themeName = it.name
                        prefs.edit().putString(KEY_THEME, it.name).apply()
                    },
                    onHaptics = {
                        haptics = it
                        prefs.edit().putBoolean(KEY_HAPTICS, it).apply()
                    },
                    onHomeSettings = { launchHomeSettings(context) },
                    onBack = { screen = Screen.MENU }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    theme: RetroTheme,
    onMenu: () -> Unit,
    onDialer: () -> Unit,
    onHomeSettings: () -> Unit
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000)
        }
    }
    val context = LocalContext.current
    val battery = remember(now) { getBatteryPercent(context) }
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)

    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("▂▄▆", color = theme.fg, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            Text("$battery% ▰", color = theme.fg, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }

        Spacer(Modifier.height(48.dp))
        Text(time, color = theme.fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 34.sp)
        Text(date, color = theme.fg, fontFamily = FontFamily.Monospace, fontSize = 17.sp)

        Spacer(Modifier.weight(1f))
        Text("RETRO", color = theme.fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 5.sp)
        Text("MINIMAL MODE", color = theme.fg.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))

        RetroButton("☎  DIAL", theme, Modifier.fillMaxWidth(0.64f), onDialer)
        Spacer(Modifier.height(22.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Menu", modifier = Modifier.clickable { onMenu() }.padding(10.dp), color = theme.fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text("Home settings", modifier = Modifier.clickable { onHomeSettings() }.padding(10.dp), color = theme.fg, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MenuScreen(
    apps: List<LaunchableApp>,
    selectedIndex: Int,
    theme: RetroTheme,
    haptics: Boolean,
    onMove: (Int) -> Unit,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    onDialer: () -> Unit,
    onHomeSettings: () -> Unit
) {
    val feedback = LocalHapticFeedback.current
    fun buzz() { if (haptics) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    Column(modifier = Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("MENU", color = theme.fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).border(2.dp, theme.fg, RoundedCornerShape(4.dp)).padding(10.dp)
        ) {
            if (apps.isEmpty()) {
                Text("No apps selected.\nOpen Settings to choose apps.", color = theme.fg, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn {
                    items(apps) { app ->
                        val index = apps.indexOf(app)
                        val selected = index == selectedIndex
                        Row(
                            modifier = Modifier.fillMaxWidth().background(if (selected) theme.fg else Color.Transparent).clickable { launchApp(LocalContext.current, app) }.padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (selected) "▶" else " ", color = if (selected) theme.bg else theme.fg, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(10.dp))
                            Text(app.label, color = if (selected) theme.bg else theme.fg, fontFamily = FontFamily.Monospace, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        DirectionPad(
            theme = theme,
            onUp = { buzz(); onMove(-1) },
            onDown = { buzz(); onMove(1) },
            onCenter = { buzz(); onOpen() },
            onCenterLong = onHomeSettings
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Dial", modifier = Modifier.clickable { onDialer() }.padding(8.dp), color = theme.fg, fontFamily = FontFamily.Monospace)
            Text("Settings", modifier = Modifier.clickable { onSettings() }.padding(8.dp), color = theme.fg, fontFamily = FontFamily.Monospace)
            Text("Back", modifier = Modifier.clickable { onBack() }.padding(8.dp), color = theme.fg, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DirectionPad(
    theme: RetroTheme,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onCenter: () -> Unit,
    onCenterLong: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DpadKey("▲", theme, onUp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            DpadKey("◀", theme, onUp)
            Box(
                modifier = Modifier.size(58.dp).padding(4.dp).border(2.dp, theme.fg, RoundedCornerShape(30.dp)).combinedClickable(onClick = onCenter, onLongClick = onCenterLong),
                contentAlignment = Alignment.Center
            ) { Text("●", color = theme.fg, fontSize = 20.sp) }
            DpadKey("▶", theme, onDown)
        }
        DpadKey("▼", theme, onDown)
    }
}

@Composable
private fun DpadKey(label: String, theme: RetroTheme, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(52.dp).padding(3.dp).border(1.dp, theme.fg, RoundedCornerShape(6.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, color = theme.fg, fontFamily = FontFamily.Monospace, fontSize = 18.sp) }
}


@Composable
private fun DialerScreen(
    theme: RetroTheme,
    haptics: Boolean,
    onCall: (String) -> Unit,
    onBack: () -> Unit
) {
    var number by remember { mutableStateOf("") }
    val feedback = LocalHapticFeedback.current
    val keys = listOf(
        "1" to "", "2" to "ABC", "3" to "DEF",
        "4" to "GHI", "5" to "JKL", "6" to "MNO",
        "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
        "*" to "", "0" to "+", "#" to ""
    )
    fun press(value: String) {
        if (haptics) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (number.length < 24) number += value
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DIAL", color = theme.fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Back", modifier = Modifier.clickable { onBack() }.padding(6.dp), color = theme.fg, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(82.dp).border(2.dp, theme.fg, RoundedCornerShape(4.dp)).padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (number.isBlank()) "Enter number" else number, color = theme.fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 25.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        keys.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (digit, letters) ->
                    Box(
                        modifier = Modifier.weight(1f).height(66.dp).border(2.dp, theme.fg, RoundedCornerShape(10.dp)).clickable { press(digit) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(digit, color = theme.fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            if (letters.isNotBlank()) Text(letters, color = theme.fg.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RetroButton("⌫", theme, Modifier.weight(1f)) { if (number.isNotEmpty()) number = number.dropLast(1) }
            RetroButton("☎ CALL", theme, Modifier.weight(2f)) { onCall(number) }
        }
    }
}

@Composable
private fun SettingsScreen(
    allApps: List<LaunchableApp>,
    selectedPackages: Set<String>,
    theme: RetroTheme,
    haptics: Boolean,
    onToggleApp: (String) -> Unit,
    onTheme: (RetroTheme) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onHomeSettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
        Text("SETTINGS", color = theme.fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("Choose only the apps you want visible.", color = theme.fg.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        Text("THEME", color = theme.fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RetroTheme.entries.forEach { option ->
                Text(
                    option.title,
                    modifier = Modifier.weight(1f).border(1.dp, theme.fg).background(if (option == theme) theme.fg else Color.Transparent).clickable { onTheme(option) }.padding(7.dp),
                    color = if (option == theme) theme.bg else theme.fg,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = haptics, onCheckedChange = onHaptics)
            Text("Key haptics", color = theme.fg, fontFamily = FontFamily.Monospace)
        }

        RetroButton("ANDROID HOME SETTINGS", theme, Modifier.fillMaxWidth(), onHomeSettings)
        Spacer(Modifier.height(12.dp))
        Text("VISIBLE APPS (${selectedPackages.size})", color = theme.fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(allApps) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggleApp(app.packageName) }.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = app.packageName in selectedPackages, onCheckedChange = { onToggleApp(app.packageName) })
                    Text(app.label, color = theme.fg, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
            }
        }
        RetroButton("BACK TO MENU", theme, Modifier.fillMaxWidth(), onBack)
    }
}

@Composable
private fun RetroButton(label: String, theme: RetroTheme, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.border(2.dp, theme.fg, RoundedCornerShape(4.dp)).clickable { onClick() }.padding(vertical = 12.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = theme.fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val flags = PackageManager.MATCH_ALL
    return context.packageManager.queryIntentActivities(intent, flags)
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            LaunchableApp(
                label = it.loadLabel(context.packageManager).toString(),
                packageName = it.activityInfo.packageName,
                className = it.activityInfo.name
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun defaultSelectedPackages(apps: List<LaunchableApp>): Set<String> {
    val wanted = listOf("phone", "messages", "camera", "maps", "whatsapp", "music", "spotify")
    val matches = apps.filter { app -> wanted.any { key -> app.label.lowercase(Locale.getDefault()).contains(key) } }
    return (if (matches.isNotEmpty()) matches.take(7) else apps.take(6)).map { it.packageName }.toSet()
}

private fun launchApp(context: Context, app: LaunchableApp) {
    runCatching {
        val intent = Intent().setClassName(app.packageName, app.className).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.recoverCatching {
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { context.startActivity(it) }
    }
}

private fun launchDialer(context: Context, number: String = "") {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))
    }
}

private fun launchHomeSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    intents.firstOrNull { it.resolveActivity(context.packageManager) != null }?.let { context.startActivity(it) }
}

private fun getBatteryPercent(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}
