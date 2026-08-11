package com.retro.minimallauncher

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var homeRequestCounter by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RetroLauncherApp(homeRequestCounter) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeRequestCounter++
        }
    }
}

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val className: String
)

data class PhoneContact(
    val id: Long,
    val name: String,
    val numbers: List<String>,
    val starred: Boolean
)

data class RecentCall(
    val id: Long,
    val name: String?,
    val number: String,
    val type: Int,
    val timestamp: Long
)

enum class Screen { MENU, OPTIONS, DIALER, RECENTS, CONTACTS, CONTACT_DETAIL, SETTINGS }

enum class RetroTheme(val title: String, val bg: Color, val fg: Color, val accent: Color) {
    CLASSIC("Classic LCD", Color(0xFFB7C69A), Color(0xFF182015), Color(0xFF83966B)),
    GREEN("Green LCD", Color(0xFF9FBC83), Color(0xFF152114), Color(0xFF718D59)),
    AMBER("Amber", Color(0xFF211B11), Color(0xFFFFC766), Color(0xFF5C4520)),
    NIGHT("Night", Color(0xFF101510), Color(0xFFD9E8CE), Color(0xFF344233)),
    MONO("Monochrome", Color.Black, Color(0xFFF1F1F1), Color(0xFF252525))
}

private const val PREFS = "retro_launcher_prefs"
private const val KEY_SELECTED = "selected_packages"
private const val KEY_THEME = "theme"
private const val KEY_HAPTICS = "haptics"

@Composable
fun RetroLauncherApp(homeRequestCounter: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val allApps = rememberLaunchableApps(context)

    var screen by remember { mutableStateOf(Screen.DIALER) }
    LaunchedEffect(homeRequestCounter) { screen = Screen.DIALER }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var selectedContact by remember { mutableStateOf<PhoneContact?>(null) }
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCallLogPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasPhonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
        if (granted) screen = Screen.CONTACTS
    }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCallLogPermission = granted
        screen = Screen.RECENTS
    }

    val phonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPhonePermission = granted
        val number = pendingCallNumber
        pendingCallNumber = null
        if (granted && !number.isNullOrBlank()) {
            placeCallDirectly(context, number)
        }
    }

    val contacts = rememberPhoneContacts(context, hasContactsPermission)
    val recentCalls = rememberRecentCalls(context, hasCallLogPermission)

    var themeName by remember {
        mutableStateOf(
            prefs.getString(KEY_THEME, RetroTheme.NIGHT.name) ?: RetroTheme.NIGHT.name
        )
    }
    var haptics by remember { mutableStateOf(prefs.getBoolean(KEY_HAPTICS, false)) }

    val initialSelected = remember(allApps) {
        val saved = prefs.getStringSet(KEY_SELECTED, null)
        saved?.toSet() ?: defaultSelectedPackages(allApps)
    }
    var selectedPackages by remember { mutableStateOf(initialSelected) }

    val theme = RetroTheme.entries.firstOrNull { it.name == themeName } ?: RetroTheme.NIGHT
    val visibleApps = remember(allApps, selectedPackages) {
        allApps.filter { it.packageName in selectedPackages }
    }
    val favoriteContacts = remember(contacts) {
        contacts.filter { it.starred }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    LaunchedEffect(visibleApps.size) {
        if (selectedIndex > visibleApps.size) selectedIndex = 0
    }

    fun openContacts() {
        if (hasContactsPermission) {
            screen = Screen.CONTACTS
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    fun openRecents() {
        if (hasCallLogPermission) {
            screen = Screen.RECENTS
        } else {
            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    fun openContact(contact: PhoneContact) {
        selectedContact = contact
        screen = Screen.CONTACT_DETAIL
    }

    fun callNumber(number: String) {
        if (number.isBlank()) return
        if (hasPhonePermission) {
            placeCallDirectly(context, number)
        } else {
            pendingCallNumber = number
            phonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    fun lockScreen() {
        lockScreenOrRequestAdmin(context)
    }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            color = theme.bg
        ) {
            when (screen) {
                Screen.MENU -> MenuScreen(
                    apps = visibleApps,
                    theme = theme,
                    haptics = haptics,
                    onOptions = { screen = Screen.OPTIONS },
                    onBack = { screen = Screen.DIALER },
                    onDialer = { screen = Screen.DIALER },
                    onHomeSettings = { launchHomeSettings(context) }
                )

                Screen.OPTIONS -> OptionsScreen(
                    theme = theme,
                    onSettings = { screen = Screen.SETTINGS },
                    onLockSetup = { requestDeviceAdmin(context) },
                    onHomeSettings = { launchHomeSettings(context) },
                    onBack = { screen = Screen.MENU }
                )

                Screen.DIALER -> DialerScreen(
                    theme = theme,
                    haptics = haptics,
                    favoriteContacts = favoriteContacts,
                    contactsEnabled = hasContactsPermission,
                    onFavorite = ::openContact,
                    onContacts = ::openContacts,
                    onRecents = ::openRecents,
                    onMenu = { screen = Screen.MENU },
                    onCall = ::callNumber,
                    onDoubleTapLock = ::lockScreen
                )

                Screen.RECENTS -> RecentsScreen(
                    calls = recentCalls,
                    permissionGranted = hasCallLogPermission,
                    theme = theme,
                    onCall = ::callNumber,
                    onBack = { screen = Screen.DIALER }
                )

                Screen.CONTACTS -> ContactsScreen(
                    contacts = contacts,
                    theme = theme,
                    haptics = haptics,
                    onOpenContact = ::openContact,
                    onBack = { screen = Screen.DIALER }
                )

                Screen.CONTACT_DETAIL -> ContactDetailScreen(
                    contact = selectedContact,
                    theme = theme,
                    onCall = ::callNumber,
                    onMessage = { number -> launchMessage(context, number) },
                    onBack = { screen = Screen.CONTACTS }
                )

                Screen.SETTINGS -> SettingsScreen(
                    allApps = allApps,
                    selectedPackages = selectedPackages,
                    theme = theme,
                    haptics = haptics,
                    contactsEnabled = hasContactsPermission,
                    onToggleApp = { packageName ->
                        val updated = if (packageName in selectedPackages) {
                            selectedPackages - packageName
                        } else {
                            selectedPackages + packageName
                        }
                        selectedPackages = updated
                        prefs.edit().putStringSet(KEY_SELECTED, updated).apply()
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
                    onContacts = ::openContacts,
                    onHomeSettings = { launchHomeSettings(context) },
                    onBack = { screen = Screen.MENU }
                )
            }
        }
    }
}

@Composable
private fun MenuScreen(
    apps: List<LaunchableApp>,
    theme: RetroTheme,
    haptics: Boolean,
    onOptions: () -> Unit,
    onBack: () -> Unit,
    onDialer: () -> Unit,
    onHomeSettings: () -> Unit
) {
    val feedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }

    val filteredApps = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { appMatchesT9(it, query) }
    }
    val totalRows = filteredApps.size + 1 // Options always stays available.

    fun buzz() {
        if (haptics) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun move(delta: Int) {
        if (totalRows <= 0) return
        selectedIndex = (selectedIndex + delta + totalRows) % totalRows
    }

    fun openSelected() {
        if (selectedIndex == filteredApps.size) {
            onOptions()
        } else {
            filteredApps.getOrNull(selectedIndex)?.let { launchApp(context, it) }
        }
    }

    LaunchedEffect(filteredApps.size, query) {
        if (selectedIndex > filteredApps.size) selectedIndex = 0
    }
    LaunchedEffect(selectedIndex, filteredApps.size) {
        if (totalRows > 0) {
            val first = listState.firstVisibleItemIndex
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first
            if (selectedIndex < first || selectedIndex > last) listState.scrollToItem(selectedIndex)
        }
    }

    val visibleRows = totalRows.coerceIn(1, 7)
    val menuHeight = (visibleRows * 46 + 18).dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "MENU",
            color = theme.fg,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(7.dp))

        BasicTextField(
            value = query,
            onValueChange = { value -> query = value.filter(Char::isDigit).take(12) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(1.dp, theme.fg, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = theme.fg,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank()) {
                        Text(
                            "T9 app search — tap and type numbers",
                            color = theme.fg.copy(alpha = 0.55f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (query.isNotBlank()) {
            Text(
                "${filteredApps.size} match${if (filteredApps.size == 1) "" else "es"}",
                color = theme.fg.copy(alpha = 0.58f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
            )
        }
        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(menuHeight)
                .border(2.dp, theme.fg, RoundedCornerShape(5.dp))
                .padding(7.dp)
        ) {
            LazyColumn(state = listState) {
                itemsIndexed(items = filteredApps, key = { _, app -> app.packageName }) { index, app ->
                    val selected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .background(
                                if (selected) theme.accent.copy(alpha = 0.42f) else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable {
                                selectedIndex = index
                                launchApp(context, app)
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (selected) "▶" else " ",
                            color = theme.fg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(
                            app.label,
                            color = theme.fg,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 17.sp,
                            maxLines = 1
                        )
                    }
                }
                item(key = "__options__") {
                    val selected = selectedIndex == filteredApps.size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .background(
                                if (selected) theme.accent.copy(alpha = 0.42f) else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable {
                                selectedIndex = filteredApps.size
                                onOptions()
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (selected) "▶" else " ",
                            color = theme.fg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(
                            "Options",
                            color = theme.fg,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 17.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        SoftKeyRow(
            left = if (query.isBlank()) "Dial" else "Clear",
            center = "Select",
            right = "Back",
            theme = theme,
            onLeft = {
                if (query.isBlank()) onDialer() else {
                    query = ""
                    selectedIndex = 0
                }
            },
            onCenter = ::openSelected,
            onRight = onBack
        )

        Spacer(Modifier.height(7.dp))
        DirectionPad(
            theme = theme,
            onUp = { buzz(); move(-1) },
            onDown = { buzz(); move(1) },
            onLeft = { buzz(); move(-1) },
            onRight = { buzz(); move(1) },
            onCenter = { buzz(); openSelected() },
            onCenterLong = onHomeSettings
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SoftKeyRow(
    left: String,
    center: String,
    right: String,
    theme: RetroTheme,
    onLeft: () -> Unit,
    onCenter: () -> Unit,
    onRight: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SoftKey(left, theme, Modifier.weight(1f), onLeft)
        SoftKey(center, theme, Modifier.weight(1f), onCenter)
        SoftKey(right, theme, Modifier.weight(1f), onRight)
    }
}

@Composable
private fun SoftKey(
    label: String,
    theme: RetroTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        text = label,
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 9.dp, horizontal = 5.dp),
        color = theme.fg,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
private fun DirectionPad(
    theme: RetroTheme,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onCenter: () -> Unit,
    onCenterLong: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DpadKey("▲", theme, onUp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            DpadKey("◀", theme, onLeft)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .padding(4.dp)
                    .border(2.dp, theme.fg, RoundedCornerShape(34.dp))
                    .combinedClickable(onClick = onCenter, onLongClick = onCenterLong),
                contentAlignment = Alignment.Center
            ) {
                Text("●", color = theme.fg, fontSize = 20.sp)
            }
            DpadKey("▶", theme, onRight)
        }
        DpadKey("▼", theme, onDown)
    }
}

@Composable
private fun DpadKey(label: String, theme: RetroTheme, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .padding(3.dp)
            .border(1.dp, theme.fg, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = theme.fg, fontFamily = FontFamily.Monospace, fontSize = 19.sp)
    }
}

@Composable
private fun DialerScreen(
    theme: RetroTheme,
    haptics: Boolean,
    favoriteContacts: List<PhoneContact>,
    contactsEnabled: Boolean,
    onFavorite: (PhoneContact) -> Unit,
    onContacts: () -> Unit,
    onRecents: () -> Unit,
    onMenu: () -> Unit,
    onCall: (String) -> Unit,
    onDoubleTapLock: () -> Unit
) {
    var number by remember { mutableStateOf("") }
    val feedback = LocalHapticFeedback.current
    val keys = remember {
        listOf(
            "1" to "", "2" to "ABC", "3" to "DEF",
            "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
            "*" to "", "0" to "+", "#" to ""
        )
    }

    fun press(value: String) {
        if (haptics) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (number.length < 24) number += value
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = { },
                onDoubleClick = onDoubleTapLock
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PHONE",
                color = theme.fg,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp
            )
            Text(
                "Menu",
                modifier = Modifier.clickable { onMenu() }.padding(5.dp),
                color = theme.fg,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            "FAVORITES",
            color = theme.fg.copy(alpha = 0.72f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth()
        )

        if (!contactsEnabled) {
            Text(
                "Contacts access off — tap More Contacts to enable",
                color = theme.fg.copy(alpha = 0.62f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            )
        } else if (favoriteContacts.isEmpty()) {
            Text(
                "No starred contacts",
                color = theme.fg.copy(alpha = 0.62f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            )
        } else {
            favoriteContacts.take(8).chunked(2).forEach { rowContacts ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowContacts.forEach { contact ->
                        FavoriteContactButton(contact, theme, Modifier.weight(1f)) {
                            onFavorite(contact)
                        }
                    }
                    if (rowContacts.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Text(
            text = if (favoriteContacts.size > 8) "More Contacts  •  ${favoriteContacts.size - 8} more favorites" else "More Contacts",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onContacts() }
                .padding(vertical = 4.dp),
            color = theme.fg,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .border(2.dp, theme.fg, RoundedCornerShape(4.dp))
                .padding(7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (number.isBlank()) "Enter number" else number,
                color = theme.fg,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(6.dp))
        keys.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                row.forEach { (digit, letters) ->
                    DialKey(digit, letters, theme, Modifier.weight(1f)) { press(digit) }
                }
            }
            Spacer(Modifier.height(5.dp))
        }

        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            RetroButton("Menu", theme, Modifier.weight(0.8f), onMenu)
            RetroButton("Contacts", theme, Modifier.weight(1.15f), onContacts)
            RetroButton("Recents", theme, Modifier.weight(1.0f), onRecents)
            RetroButton("⌫", theme, Modifier.weight(0.55f)) {
                if (number.isNotEmpty()) number = number.dropLast(1)
            }
            RetroButton("CALL", theme, Modifier.weight(0.85f)) {
                if (number.isNotBlank()) onCall(number)
            }
        }
        Text(
            "Double-tap empty space to lock",
            color = theme.fg.copy(alpha = 0.45f),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

@Composable
private fun FavoriteContactButton(
    contact: PhoneContact,
    theme: RetroTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .border(1.dp, theme.fg.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            "★ ${contact.name}",
            color = theme.fg,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun DialKey(
    digit: String,
    letters: String,
    theme: RetroTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .border(2.dp, theme.fg, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                digit,
                color = theme.fg,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            if (letters.isNotBlank()) {
                Text(
                    letters,
                    color = theme.fg.copy(alpha = 0.68f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun OptionsScreen(
    theme: RetroTheme,
    onSettings: () -> Unit,
    onLockSetup: () -> Unit,
    onHomeSettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "OPTIONS",
            color = theme.fg,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Spacer(Modifier.height(18.dp))
        RetroButton("Launcher Settings", theme, Modifier.fillMaxWidth(), onSettings)
        Spacer(Modifier.height(8.dp))
        RetroButton("Enable Double-Tap Lock", theme, Modifier.fillMaxWidth(), onLockSetup)
        Spacer(Modifier.height(8.dp))
        RetroButton("Android Home Settings", theme, Modifier.fillMaxWidth(), onHomeSettings)
        Spacer(Modifier.weight(1f))
        RetroButton("BACK TO MENU", theme, Modifier.fillMaxWidth(), onBack)
    }
}

@Composable
private fun RecentsScreen(
    calls: List<RecentCall>,
    permissionGranted: Boolean,
    theme: RetroTheme,
    onCall: (String) -> Unit,
    onBack: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "RECENTS",
                color = theme.fg,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Text(
                "Back",
                modifier = Modifier.clickable { onBack() }.padding(6.dp),
                color = theme.fg,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))

        if (!permissionGranted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Call-history access is off.\nReturn to Phone and tap Recents to request it.",
                    color = theme.fg,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        } else if (calls.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No recent calls",
                    color = theme.fg,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(calls, key = { it.id }) { call ->
                    val typeLabel = when (call.type) {
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.OUTGOING_TYPE -> "OUT"
                        CallLog.Calls.INCOMING_TYPE -> "IN"
                        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                        else -> "CALL"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCall(call.number) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                call.name?.takeIf { it.isNotBlank() } ?: call.number,
                                color = theme.fg,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            if (!call.name.isNullOrBlank()) {
                                Text(
                                    call.number,
                                    color = theme.fg.copy(alpha = 0.58f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                typeLabel,
                                color = theme.fg.copy(alpha = if (call.type == CallLog.Calls.MISSED_TYPE) 1f else 0.72f),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (call.type == CallLog.Calls.MISSED_TYPE) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 9.sp
                            )
                            Text(
                                Instant.ofEpochMilli(call.timestamp)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                                    .format(formatter),
                                color = theme.fg.copy(alpha = 0.55f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
            Text(
                "Tap an entry to call directly.",
                color = theme.fg.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ContactsScreen(
    contacts: List<PhoneContact>,
    theme: RetroTheme,
    haptics: Boolean,
    onOpenContact: (PhoneContact) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val feedback = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val t9Keys = remember { listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#") }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter { contactMatchesSearch(it, query) }
    }

    LaunchedEffect(filtered.size, query) {
        if (selectedIndex > filtered.lastIndex) selectedIndex = 0
    }
    LaunchedEffect(selectedIndex, filtered.size) {
        if (filtered.isNotEmpty()) listState.scrollToItem(selectedIndex)
    }

    fun press(value: String) {
        if (haptics) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (value.firstOrNull()?.isDigit() == true && query.length < 18) query += value
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CONTACTS",
                color = theme.fg,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp
            )
            Text(
                "${filtered.size}/${contacts.size}",
                color = theme.fg.copy(alpha = 0.62f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }

        BasicTextField(
            value = query,
            onValueChange = { value ->
                query = value.take(32)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .border(1.dp, theme.fg, RoundedCornerShape(4.dp))
                .padding(horizontal = 9.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = theme.fg,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank()) {
                        Text(
                            "T9 542… or tap to type a name",
                            color = theme.fg.copy(alpha = 0.55f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(2.dp, theme.fg, RoundedCornerShape(5.dp))
                .padding(5.dp)
        ) {
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (contacts.isEmpty()) "No contacts found" else "No T9 matches",
                        color = theme.fg,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(state = listState) {
                    itemsIndexed(filtered, key = { _, contact -> contact.id }) { index, contact ->
                        val selected = index == selectedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .background(
                                    if (selected) theme.accent.copy(alpha = 0.38f) else Color.Transparent,
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable {
                                    selectedIndex = index
                                    onOpenContact(contact)
                                }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when {
                                    selected && contact.starred -> "▶★"
                                    selected -> "▶ "
                                    contact.starred -> " ★"
                                    else -> "  "
                                },
                                color = theme.fg,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier.width(28.dp)
                            )
                            Column {
                                Text(
                                    contact.name,
                                    color = theme.fg,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                Text(
                                    contact.numbers.firstOrNull().orEmpty(),
                                    color = theme.fg.copy(alpha = 0.58f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(5.dp))
        SoftKeyRow(
            left = "Clear",
            center = "Select",
            right = "Back",
            theme = theme,
            onLeft = {
                if (query.isNotEmpty()) query = query.dropLast(1)
                else if (selectedIndex > 0) selectedIndex--
            },
            onCenter = { filtered.getOrNull(selectedIndex)?.let(onOpenContact) },
            onRight = onBack
        )

        t9Keys.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { digit ->
                    val letters = when (digit) {
                        "2" -> "ABC"; "3" -> "DEF"; "4" -> "GHI"; "5" -> "JKL"
                        "6" -> "MNO"; "7" -> "PQRS"; "8" -> "TUV"; "9" -> "WXYZ"
                        else -> ""
                    }
                    DialKey(digit, letters, theme, Modifier.weight(1f)) { press(digit) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ContactDetailScreen(
    contact: PhoneContact?,
    theme: RetroTheme,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    if (contact == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Contact unavailable", color = theme.fg, fontFamily = FontFamily.Monospace)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (contact.starred) "★ CONTACT" else "CONTACT",
            color = theme.fg.copy(alpha = 0.72f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(16.dp))
        Text(
            contact.name,
            color = theme.fg,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(contact.numbers.distinct()) { number ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, theme.fg.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        number,
                        color = theme.fg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RetroButton("☎ Call", theme, Modifier.weight(1f)) { onCall(number) }
                        RetroButton("✉ Message", theme, Modifier.weight(1f)) { onMessage(number) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        RetroButton("BACK TO CONTACTS", theme, Modifier.fillMaxWidth(), onBack)
    }
}

@Composable
private fun SettingsScreen(
    allApps: List<LaunchableApp>,
    selectedPackages: Set<String>,
    theme: RetroTheme,
    haptics: Boolean,
    contactsEnabled: Boolean,
    onToggleApp: (String) -> Unit,
    onTheme: (RetroTheme) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onContacts: () -> Unit,
    onHomeSettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            "SETTINGS",
            color = theme.fg,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Text(
            "Keep only the apps you actually need.",
            color = theme.fg.copy(alpha = 0.78f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))

        Text(
            "THEME",
            color = theme.fg,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        RetroTheme.entries.chunked(2).forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowThemes.forEach { option ->
                    Text(
                        option.title,
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, theme.fg)
                            .background(
                                if (option == theme) theme.accent.copy(alpha = 0.55f)
                                else Color.Transparent
                            )
                            .clickable { onTheme(option) }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        color = theme.fg,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (option == theme) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
                if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = haptics, onCheckedChange = onHaptics)
            Column {
                Text("Key haptics", color = theme.fg, fontFamily = FontFamily.Monospace)
                Text(
                    "Off uses slightly less power",
                    color = theme.fg.copy(alpha = 0.68f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }

        RetroButton(
            if (contactsEnabled) "CONTACTS: ENABLED" else "ENABLE CONTACTS",
            theme,
            Modifier.fillMaxWidth(),
            onContacts
        )
        Spacer(Modifier.height(6.dp))
        RetroButton("ANDROID HOME SETTINGS", theme, Modifier.fillMaxWidth(), onHomeSettings)
        Spacer(Modifier.height(10.dp))
        Text(
            "VISIBLE APPS (${selectedPackages.size})",
            color = theme.fg,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items = allApps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleApp(app.packageName) }
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = app.packageName in selectedPackages,
                        onCheckedChange = { onToggleApp(app.packageName) }
                    )
                    Text(
                        app.label,
                        color = theme.fg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }
        }
        RetroButton("BACK TO MENU", theme, Modifier.fillMaxWidth(), onBack)
    }
}

@Composable
private fun RetroButton(
    label: String,
    theme: RetroTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .border(2.dp, theme.fg, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = theme.fg,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun rememberLaunchableApps(context: Context): List<LaunchableApp> {
    val appContext = context.applicationContext
    var apps by remember { mutableStateOf(loadLaunchableApps(appContext)) }

    DisposableEffect(appContext) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                apps = loadLaunchableApps(appContext)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }

    return apps
}

@Composable
private fun rememberPhoneContacts(context: Context, enabled: Boolean): List<PhoneContact> {
    val appContext = context.applicationContext
    var contacts by remember { mutableStateOf(if (enabled) loadPhoneContacts(appContext) else emptyList()) }

    LaunchedEffect(enabled) {
        contacts = if (enabled) loadPhoneContacts(appContext) else emptyList()
    }

    DisposableEffect(appContext, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                contacts = loadPhoneContacts(appContext)
            }
        }
        appContext.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer
        )
        onDispose {
            runCatching { appContext.contentResolver.unregisterContentObserver(observer) }
        }
    }

    return contacts
}

@Composable
private fun rememberRecentCalls(context: Context, enabled: Boolean): List<RecentCall> {
    val appContext = context.applicationContext
    var calls by remember { mutableStateOf(if (enabled) loadRecentCalls(appContext) else emptyList()) }

    LaunchedEffect(enabled) {
        calls = if (enabled) loadRecentCalls(appContext) else emptyList()
    }

    DisposableEffect(appContext, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                calls = loadRecentCalls(appContext)
            }
        }
        appContext.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            observer
        )
        onDispose {
            runCatching { appContext.contentResolver.unregisterContentObserver(observer) }
        }
    }

    return calls
}

private fun loadRecentCalls(context: Context): List<RecentCall> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) !=
        PackageManager.PERMISSION_GRANTED
    ) return emptyList()

    val calls = mutableListOf<RecentCall>()
    val projection = arrayOf(
        CallLog.Calls._ID,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.NUMBER,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE
    )

    runCatching {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)

            while (cursor.moveToNext() && calls.size < 20) {
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                if (number.isBlank()) continue
                calls += RecentCall(
                    id = cursor.getLong(idIndex),
                    name = cursor.getString(nameIndex)?.trim(),
                    number = number,
                    type = cursor.getInt(typeIndex),
                    timestamp = cursor.getLong(dateIndex)
                )
            }
        }
    }
    return calls
}

private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .asSequence()
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
        .toList()
}

private fun loadPhoneContacts(context: Context): List<PhoneContact> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
        PackageManager.PERMISSION_GRANTED
    ) return emptyList()

    data class MutableContact(
        val id: Long,
        var name: String,
        var starred: Boolean,
        val numbers: MutableList<String>
    )

    val grouped = linkedMapOf<Long, MutableContact>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.Contacts.STARRED
    )

    runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val starredIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex)?.trim().orEmpty().ifBlank { "Unnamed" }
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                val starred = cursor.getInt(starredIndex) == 1
                if (number.isBlank()) continue

                val contact = grouped.getOrPut(id) {
                    MutableContact(id, name, starred, mutableListOf())
                }
                contact.name = name
                contact.starred = contact.starred || starred
                if (number !in contact.numbers) contact.numbers += number
            }
        }
    }

    return grouped.values
        .map { PhoneContact(it.id, it.name, it.numbers.toList(), it.starred) }
        .sortedBy { it.name.lowercase(Locale.getDefault()) }
}

private fun defaultSelectedPackages(apps: List<LaunchableApp>): Set<String> {
    val wanted = listOf("messages", "camera", "maps", "whatsapp", "music", "spotify")
    val matches = apps.filter { app ->
        wanted.any { key -> app.label.lowercase(Locale.getDefault()).contains(key) }
    }
    return (if (matches.isNotEmpty()) matches.take(7) else apps.take(6))
        .map { it.packageName }
        .toSet()
}

private fun appMatchesT9(app: LaunchableApp, query: String): Boolean {
    val cleanQuery = query.filter(Char::isDigit)
    if (cleanQuery.isBlank()) return true

    fun digitsFor(text: String): String = text
        .filter(Char::isLetterOrDigit)
        .uppercase(Locale.getDefault())
        .mapNotNull(::letterToT9Digit)
        .joinToString("")

    val wholeLabelMatch = digitsFor(app.label).startsWith(cleanQuery)
    val wordMatch = app.label
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .any { digitsFor(it).startsWith(cleanQuery) }

    return wholeLabelMatch || wordMatch
}

private fun contactMatchesSearch(contact: PhoneContact, query: String): Boolean {
    if (query.isBlank()) return true
    val trimmed = query.trim()

    if (trimmed.any(Char::isLetter)) {
        val normalized = trimmed.lowercase(Locale.getDefault())
        return contact.name.lowercase(Locale.getDefault()).contains(normalized) ||
            contact.numbers.any { it.filter(Char::isDigit).contains(trimmed.filter(Char::isDigit)) && trimmed.any(Char::isDigit) }
    }

    val cleanQuery = trimmed.filter(Char::isDigit)
    if (cleanQuery.isBlank()) return true

    val nameDigits = contact.name
        .filter(Char::isLetterOrDigit)
        .uppercase(Locale.getDefault())
        .mapNotNull(::letterToT9Digit)
        .joinToString("")

    val numberMatch = contact.numbers.any { number ->
        number.filter(Char::isDigit).contains(cleanQuery)
    }
    return nameDigits.startsWith(cleanQuery) || numberMatch
}

private fun letterToT9Digit(char: Char): Char? = when (char) {
    in 'A'..'C' -> '2'
    in 'D'..'F' -> '3'
    in 'G'..'I' -> '4'
    in 'J'..'L' -> '5'
    in 'M'..'O' -> '6'
    in 'P'..'S' -> '7'
    in 'T'..'V' -> '8'
    in 'W'..'Z' -> '9'
    in '0'..'9' -> char
    else -> null
}

private fun t9LettersHint(query: String): String {
    val last = query.lastOrNull() ?: return ""
    return when (last) {
        '2' -> "ABC"; '3' -> "DEF"; '4' -> "GHI"; '5' -> "JKL"
        '6' -> "MNO"; '7' -> "PQRS"; '8' -> "TUV"; '9' -> "WXYZ"
        else -> ""
    }
}

private fun launchApp(context: Context, app: LaunchableApp) {
    runCatching {
        val intent = Intent()
            .setClassName(app.packageName, app.className)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.recoverCatching {
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            context.startActivity(it)
        }
    }
}

private fun placeCallDirectly(context: Context, number: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) !=
        PackageManager.PERMISSION_GRANTED
    ) return

    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun requestDeviceAdmin(context: Context) {
    val admin = ComponentName(context, LockDeviceAdminReceiver::class.java)
    val manager = context.getSystemService(DevicePolicyManager::class.java)
    if (manager.isAdminActive(admin)) return

    runCatching {
        context.startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Allow Retro Minimal Launcher to lock the screen when you double-tap the Phone home screen."
                )
            }
        )
    }
}

private fun lockScreenOrRequestAdmin(context: Context) {
    val admin = ComponentName(context, LockDeviceAdminReceiver::class.java)
    val manager = context.getSystemService(DevicePolicyManager::class.java)
    if (manager.isAdminActive(admin)) {
        runCatching { manager.lockNow() }
    } else {
        requestDeviceAdmin(context)
    }
}

private fun launchMessage(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")))
    }
}

private fun launchHomeSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    intents.firstOrNull { it.resolveActivity(context.packageManager) != null }
        ?.let { context.startActivity(it) }
}
