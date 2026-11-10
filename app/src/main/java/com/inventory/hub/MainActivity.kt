package com.inventory.hub

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val packageName: String,
    val githubReleaseUrl: String
)

class MainActivity : ComponentActivity() {
    
    private val plugins = listOf(
        PluginInfo(
            id = "scanner",
            name = "Штрих-код",
            description = "Сканирование и сохранение в историю",
            icon = Icons.Default.QrCodeScanner,
            packageName = "com.inventory.scanner",
            githubReleaseUrl = "https://github.com/kbav26t-netizen/InventoryScanner/releases/latest/download/app-debug.apk"
        ),
        PluginInfo(
            id = "counter",
            name = "Подсчёт объектов",
            description = "Саморезы, фитинги, коробки",
            icon = Icons.Default.Inventory,
            packageName = "com.inventory.counter",
            githubReleaseUrl = "https://github.com/kbav26t-netizen/InventoryCounter/releases/latest/download/app-debug.apk"
        ),
        PluginInfo(
            id = "exporter",
            name = "Экспорт Excel",
            description = "Выгрузка данных в таблицу",
            icon = Icons.Default.TableChart,
            packageName = "com.inventory.exporter",
            githubReleaseUrl = "https://github.com/kbav26t-netizen/InventoryExporter/releases/latest/download/app-debug.apk"
        )
    )
    
    private val installLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HubScreen(
                        plugins = plugins,
                        onLaunchPlugin = { plugin -> launchPlugin(plugin) },
                        onDownloadPlugin = { plugin -> downloadPlugin(plugin) }
                    )
                }
            }
        }
    }
    
    private fun launchPlugin(plugin: PluginInfo) {
        val intent = packageManager.getLaunchIntentForPackage(plugin.packageName)
        if (intent != null) {
            startActivity(intent)
        }
    }
    
    private fun downloadPlugin(plugin: PluginInfo) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(plugin.githubReleaseUrl))
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    plugins: List<PluginInfo>,
    onLaunchPlugin: (PluginInfo) -> Unit,
    onDownloadPlugin: (PluginInfo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installedPlugins by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    
    // Проверяем установленные плагины
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val installed = plugins.associate { plugin ->
            plugin.id to try {
                pm.getPackageInfo(plugin.packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
        installedPlugins = installed
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инвентаризация") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Выберите режим работы",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Кнопки плагинов
            plugins.forEach { plugin ->
                val isInstalled = installedPlugins[plugin.id] ?: false
                PluginCard(
                    plugin = plugin,
                    isInstalled = isInstalled,
                    onLaunch = { onLaunchPlugin(plugin) },
                    onDownload = { onDownloadPlugin(plugin) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun PluginCard(
    plugin: PluginInfo,
    isInstalled: Boolean,
    onLaunch: () -> Unit,
    onDownload: () -> Unit
) {
    OutlinedCard(
        onClick = if (isInstalled) onLaunch else {},
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = plugin.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isInstalled) MaterialTheme.colorScheme.primary
                       else Color.Gray
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plugin.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isInstalled) MaterialTheme.colorScheme.onSurface
                            else Color.Gray
                )
                Text(
                    plugin.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isInstalled) MaterialTheme.colorScheme.onSurfaceVariant
                            else Color.Gray
                )
            }
            if (isInstalled) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Установлено",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Скачать")
                }
            }
        }
    }
}
