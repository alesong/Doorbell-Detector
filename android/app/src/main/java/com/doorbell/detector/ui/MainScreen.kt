package com.doorbell.detector.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.doorbell.detector.data.ApiClient
import com.doorbell.detector.data.PreferencesManager
import com.doorbell.detector.model.AppInfo
import com.doorbell.detector.service.NotificationEntry
import com.doorbell.detector.service.NotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen {
    Privacy, Permission, Main
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { PreferencesManager(context) }

    val privacyAccepted by preferencesManager.privacyAccepted.collectAsState(initial = false)
    val selectedPackages by preferencesManager.selectedPackages.collectAsState(initial = emptySet())
    val selectedAppNames by preferencesManager.selectedAppNames.collectAsState(initial = emptyMap())

    val hasPostPermission = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    var hasNotificationAccess by remember { mutableStateOf(checkNotificationAccess(context)) }
    var checking by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(Screen.Main) }

    LaunchedEffect(privacyAccepted) {
        currentScreen = when {
            !privacyAccepted -> Screen.Privacy
            else -> Screen.Main
        }
    }

    when (currentScreen) {
        Screen.Privacy -> PrivacyScreen(
            onAccept = {
                scope.launch {
                    preferencesManager.acceptPrivacy()
                }
            }
        )

        Screen.Permission -> PermissionScreen(
            hasPostPermission = hasPostPermission,
            hasNotificationAccess = hasNotificationAccess,
            checking = checking,
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onOpenNotificationAccess = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            },
            onCheckPermission = {
                checking = true
                scope.launch {
                    hasNotificationAccess = checkNotificationAccess(context)
                    checking = false
                }
            }
        )

        Screen.Main -> MainContent(
            preferencesManager = preferencesManager,
            selectedPackages = selectedPackages,
            selectedAppNames = selectedAppNames,
            hasNotificationAccess = hasNotificationAccess,
            onOpenNotificationAccess = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onRefreshPermission = {
                hasNotificationAccess = checkNotificationAccess(context)
                if (!hasNotificationAccess) {
                    currentScreen = Screen.Permission
                }
            }
        )
    }
}

@Composable
private fun PrivacyScreen(onAccept: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Privacidad ante todo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Doorbell Detector funciona 100% local:",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrivacyBullet("Lee SOLO las notificaciones de la app que selecciones")
        PrivacyBullet("Envía los datos ÚNICAMENTE al servidor que tú configures")
        PrivacyBullet("No compartimos datos con terceros")
        PrivacyBullet("No tenemos acceso a tu información")
        PrivacyBullet("No hay rastreo ni analytics")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tú controlas completamente dónde va tu información.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aceptar y continuar")
        }
    }
}

@Composable
private fun PrivacyBullet(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PermissionScreen(
    hasPostPermission: Boolean,
    hasNotificationAccess: Boolean,
    checking: Boolean,
    onOpenSettings: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onCheckPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Activar acceso a notificaciones",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Doorbell Detector necesita permiso para leer notificaciones. Solo así puede capturar cuando alguien toque el timbre y reenviarlo a tu servidor.",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Importante: En algunos dispositivos (Xiaomi, Huawei, Oppo), deberás activar primero \"Permitir configuración restringida\" dentro de los ajustes de la app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abrir ajustes de la app")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Paso 1: Abrir ajustes de la app",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "1. Busca \"Doorbell Detector\" en la lista y activa el interruptor",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "2. Si no aparece, toca el menú ⋮ y activa \"Mostrar sistema\" o \"Permitir configuración restringida\"",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "3. Vuelve a la app cuando esté activado",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "En Xiaomi, Redmi, POCO y otros, primero debes permitir \"Configuración restringida\" desde los ajustes de la app:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (Build.VERSION.SDK_INT >= 33 && !hasPostPermission) {
            Text(
                text = "En Android 13+, también necesitas permitir que la app muestre notificaciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = onOpenNotificationAccess,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ir a acceso a notificaciones")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onCheckPermission,
            modifier = Modifier.fillMaxWidth(),
            enabled = !checking
        ) {
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (checking) "Verificando..." else "Verificar permiso")
        }

        if (hasNotificationAccess) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Permiso concedido",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    preferencesManager: PreferencesManager,
    selectedPackages: Set<String>,
    selectedAppNames: Map<String, String>,
    hasNotificationAccess: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshPermission: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient() }

    val apiUrl by preferencesManager.apiUrl.collectAsState(initial = "")

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var filteredApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showManualEntry by remember { mutableStateOf(false) }
    var manualPackageName by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var apiUrlInput by remember(apiUrl) { mutableStateOf(apiUrl) }
    var isStartingService by remember { mutableStateOf(false) }

    // Simulator state
    var simAppName by remember { mutableStateOf("Tuya Smart") }
    var simPackageName by remember { mutableStateOf("com.tuya.smart") }
    var simTitle by remember { mutableStateOf("Alguien toca el timbre") }
    var simBody by remember { mutableStateOf("Persona detectada en la puerta principal") }
    var simSending by remember { mutableStateOf(false) }
    var simResult by remember { mutableStateOf<String?>(null) }

    fun filterApps(appList: List<AppInfo>, query: String) {
        filteredApps = if (query.isBlank()) {
            appList
        } else {
            appList.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "Doorbell Detector",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Selected apps section
            if (selectedPackages.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Escuchando notificaciones de:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = {
                                scope.launch { preferencesManager.clearSelectedPackages() }
                            }) {
                                Icon(Icons.Default.Clear, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Limpiar todo")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedPackages.forEach { pkg ->
                                val name = selectedAppNames[pkg] ?: pkg
                                AssistChip(
                                    onClick = {
                                        scope.launch { preferencesManager.removeSelectedPackage(pkg) }
                                    },
                                    label = { Text(name, maxLines = 1) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Clear, "Quitar", Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // App list card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selecciona apps a monitorear",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (apps.isNotEmpty()) {
                            Text(
                                text = "${filteredApps.size} apps (${selectedPackages.size} seleccionadas)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isLoadingApps = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    context.loadInstalledApps()
                                }
                                apps = result
                                filterApps(result, searchQuery)
                                isLoadingApps = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoadingApps
                    ) {
                        if (isLoadingApps) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cargando...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cargar apps")
                        }
                    }

                    if (apps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                filterApps(apps, it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Buscar app...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null
                                )
                            },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.height(300.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                val isChecked = app.packageName in selectedPackages
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                if (isChecked) {
                                                    preferencesManager.removeSelectedPackage(app.packageName)
                                                } else {
                                                    preferencesManager.addSelectedPackage(app.packageName, app.appName)
                                                }
                                            }
                                        }
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                if (checked) {
                                                    preferencesManager.addSelectedPackage(app.packageName, app.appName)
                                                } else {
                                                    preferencesManager.removeSelectedPackage(app.packageName)
                                                }
                                            }
                                        }
                                    )
                                    val painter = rememberAppIconPainter(context, app.packageName)
                                    if (painter != null) {
                                        Icon(
                                            painter = painter,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(36.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = app.appName.take(1).uppercase(),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { showManualEntry = !showManualEntry }
                    ) {
                        Text(
                            if (showManualEntry) "Ocultar entrada manual"
                            else "O escribir package manualmente"
                        )
                    }

                    if (showManualEntry) {
                        OutlinedTextField(
                            value = manualPackageName,
                            onValueChange = { manualPackageName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Package name") },
                            placeholder = { Text("com.example.app") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (manualPackageName.isNotBlank()) {
                                        val pkg = manualPackageName.trim()
                                        scope.launch {
                                            preferencesManager.addSelectedPackage(pkg, pkg)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = manualPackageName.isNotBlank()
                            ) {
                                Text("Agregar")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (manualPackageName.isNotBlank()) {
                                        val pkg = manualPackageName.trim()
                                        scope.launch {
                                            preferencesManager.removeSelectedPackage(pkg)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = manualPackageName.isNotBlank()
                            ) {
                                Text("Quitar")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Server configuration
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Configuración del servidor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiUrlInput,
                        onValueChange = { apiUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL del servidor") },
                        placeholder = { Text("http://192.168.1.100:3000") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    preferencesManager.setApiUrl(apiUrlInput)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Guardar")
                        }

                        Button(
                            onClick = {
                                isTesting = true
                                connectionStatus = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        apiClient.testConnection(apiUrlInput)
                                    }
                                    connectionStatus = if (result.isSuccess) {
                                        "Conectado al servidor"
                                    } else {
                                        "Connection failed: ${result.exceptionOrNull()?.message}"
                                    }
                                    isTesting = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isTesting
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isTesting) "Probando conexión..." else "Probar conexión")
                        }
                    }

                    if (connectionStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = connectionStatus ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (connectionStatus?.startsWith("Conectado") == true)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status and settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Estado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasNotificationAccess) Icons.Default.Check else Icons.Default.Clear,
                            contentDescription = null,
                            tint = if (hasNotificationAccess) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasNotificationAccess) "Acceso a notificaciones: Concedido"
                                   else "Acceso a notificaciones: Denegado",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val hasTarget = selectedPackages.isNotEmpty()
                        val combined = hasNotificationAccess && hasTarget
                        Icon(
                            imageVector = if (combined) Icons.Default.Check else Icons.Default.Clear,
                            contentDescription = null,
                            tint = if (combined) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                !hasNotificationAccess -> "Activar acceso a notificaciones primero"
                                !hasTarget -> "Selecciona una app para monitorear"
                                else -> "Todo listo — enviando notificaciones al servidor"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenNotificationAccess,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ir a acceso a notificaciones")
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ir a permisos de la app")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onRefreshPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verificar permiso")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device configuration (Xiaomi/Huawei/etc)
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Configuración del dispositivo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "En Xiaomi/HyperOS y otras ROMs, el sistema bloquea servicios en segundo plano. Activa estas opciones para que Doorbell Detector funcione correctamente:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Deshabilitar optimización de batería")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Abrir ajustes de la app (autoinicio)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ir a acceso a notificaciones")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isStartingService = true
                            val intent = Intent(context, NotificationListener::class.java).apply {
                                putExtra("start_from_ui", true)
                            }
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainScreen", "startService failed", e)
                            }
                            scope.launch {
                                kotlinx.coroutines.delay(2000)
                                isStartingService = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isStartingService
                    ) {
                        if (isStartingService) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Iniciando...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Iniciar / Reiniciar servicio de escucha")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Instrucciones para Xiaomi/HyperOS:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1. Abre Seguridad → Batería → Bloqueo de autoinicio y activa Doorbell Detector\n2. Abre Ajustes → Apps → Doorbell Detector → Otros permisos → Permitir configuración restringida\n3. Abre el menú de apps recientes, mantén presionada Doorbell Detector y elige \"Fijar\" o \"Bloquear\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Simulator card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Simulador de pruebas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Envía notificaciones de prueba al servidor para verificar que funciona",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = simAppName,
                        onValueChange = { simAppName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("App name") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = simPackageName,
                        onValueChange = { simPackageName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Package name") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = simTitle,
                        onValueChange = { simTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Título") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = simBody,
                        onValueChange = { simBody = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Cuerpo") },
                        minLines = 2,
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            simSending = true
                            simResult = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    apiClient.sendNotification(
                                        baseUrl = apiUrlInput,
                                        appName = simAppName,
                                        packageName = simPackageName,
                                        title = simTitle,
                                        body = simBody
                                    )
                                }
                                simResult = if (result.isSuccess) {
                                    "Enviado correctamente"
                                } else {
                                    "Error: ${result.exceptionOrNull()?.message}"
                                }
                                simSending = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !simSending && apiUrlInput.isNotBlank()
                    ) {
                        if (simSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enviando...")
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enviar notificación de prueba")
                        }
                    }

                    if (simResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = simResult ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (simResult?.startsWith("Enviado") == true)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Debug section: recently received notifications
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Notificaciones recibidas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { NotificationListener.clearNotifications() }) {
                            Text("Limpiar")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Targets: ${NotificationListener.targetPackages.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Servidor: ${NotificationListener.targetServer}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val entries by NotificationListener.notificationsFlow.collectAsState()
                    if (entries.isEmpty()) {
                        Text(
                            text = "Esperando notificaciones...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        entries.take(10).forEach { entry ->
                            val matched = entry.packageName in NotificationListener.targetPackages
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = entry.timestamp,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = when {
                                        entry.error != null -> "Error"
                                        entry.sent -> "Enviado"
                                        !matched -> "Filtrada"
                                        else -> "Pendiente"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        entry.error != null -> MaterialTheme.colorScheme.error
                                        entry.sent -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Text(
                                text = entry.packageName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (matched) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                            if (entry.title.isNotBlank() || entry.text.isNotBlank()) {
                                Text(
                                    text = buildString {
                                        if (entry.title.isNotBlank()) append(entry.title)
                                        if (entry.text.isNotBlank()) {
                                            if (isNotEmpty()) append(": ")
                                            append(entry.text)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                            }
                            if (entry.error != null) {
                                Text(
                                    text = entry.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun rememberAppIconPainter(context: Context, packageName: String): Painter? {
    val drawable = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }
    return remember(drawable) {
        if (drawable != null) {
            val bitmap = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth.takeIf { it > 0 } ?: 64,
                drawable.intrinsicHeight.takeIf { it > 0 } ?: 64,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            BitmapPainter(bitmap.asImageBitmap())
        } else null
    }
}

private fun Context.loadInstalledApps(): List<AppInfo> {
    val pm = packageManager
    val apps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))

    return apps
        .filter { info ->
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) return@filter false

            val installer = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val sourceInfo: InstallSourceInfo = pm.getInstallSourceInfo(info.packageName)
                    sourceInfo.installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(info.packageName)
                }
            } catch (e: Exception) {
                null
            }

            installer == "com.android.vending"
        }
        .map { info ->
            val appName = pm.getApplicationLabel(info).toString()
            AppInfo(
                packageName = info.packageName,
                appName = appName
            )
        }
        .sortedBy { it.appName.lowercase() }
}

private fun checkNotificationAccess(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabledListeners.contains(context.packageName)
}
