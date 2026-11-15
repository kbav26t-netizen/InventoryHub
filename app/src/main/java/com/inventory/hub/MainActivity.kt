package com.inventory.hub

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import com.inventory.hub.data.ProductDatabase
import com.inventory.hub.data.ProductEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class PluginInfo(
    val id: String, val name: String, val description: String,
    val icon: ImageVector, val packageName: String, val githubReleaseUrl: String
)

class MainActivity : ComponentActivity() {
    private val db by lazy { ProductDatabase.getInstance(this) }

    private val plugins = listOf(
        PluginInfo("scanner", "Штрих-код", "Сканирование и сохранение в историю", Icons.Default.QrCodeScanner, "com.inventory.scanner", "https://github.com/kbav26t-netizen/ScannerPlugin/releases/latest/download/scanner-plugin-arm64-v8a-debug.apk"),
        PluginInfo("exporter", "Экспорт Excel", "Выгрузка данных в таблицу", Icons.Default.TableChart, "com.inventory.exporter", "https://github.com/kbav26t-netizen/InventoryExporter/releases/latest/download/exporter-plugin-debug.apk")
    )

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadCsvFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HubScreen(plugins = plugins, onLaunchPlugin = ::launchPlugin, onDownloadPlugin = ::downloadPlugin, onLoadReference = { filePicker.launch("text/csv") })
                }
            }
        }
    }

    private fun loadCsvFile(uri: Uri) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            try {
                db.productDao().clearAll()
                val products = mutableListOf<ProductEntity>()
                
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
                        lines.drop(1).forEach { line -> // Пропускаем заголовок
                            val parts = line.split(";") // Если в Excel сохраняли как "CSV (точка с запятой)"
                            if (parts.size >= 3) {
                                val article = parts[0].trim()
                                val barcode = parts[1].trim()
                                val name = parts[2].trim()
                                val qty = parts.getOrNull(3)?.trim()?.toIntOrNull() ?: 0
                                if (barcode.isNotBlank()) {
                                    products.add(ProductEntity(barcode, name, article, qty))
                                }
                            }
                        }
                    }
                }
                
                if (products.isNotEmpty()) {
                    db.productDao().insertAll(products)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Загружено ${products.size} товаров", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun launchPlugin(plugin: PluginInfo) {
        val intent = Intent().apply {
            component = android.content.ComponentName(plugin.packageName, "${plugin.packageName}.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        try { startActivity(intent) } 
        catch (e: Exception) { Toast.makeText(this, "Плагин не установлен", Toast.LENGTH_SHORT).show() }
    }

    private fun downloadPlugin(plugin: PluginInfo) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(plugin.githubReleaseUrl)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(plugins: List<PluginInfo>, onLaunchPlugin: (PluginInfo) -> Unit, onDownloadPlugin: (PluginInfo) -> Unit, onLoadReference: () -> Unit) {
    val context = LocalContext.current
    var installedPlugins by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var productCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        installedPlugins = plugins.associate { plugin ->
            plugin.id to try { pm.getPackageInfo(plugin.packageName, 0); true } catch (e: PackageManager.NameNotFoundException) { false }
        }
        // Читаем количество товаров из БД
        productCount = com.inventory.hub.data.ProductDatabase.getInstance(context).productDao().getCount()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Инвентаризация") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Выберите режим работы", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            
            Card(modifier = Modifier.fillMaxWidth().clickable { onLoadReference() }.padding(bottom = 16.dp), shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Загрузить справочник (CSV)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("В базе: $productCount товаров", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            plugins.forEach { plugin ->
                val isInstalled = installedPlugins[plugin.id] ?: false
                PluginCard(plugin = plugin, isInstalled = isInstalled, onLaunch = { onLaunchPlugin(plugin) }, onDownload = { onDownloadPlugin(plugin) })
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PluginCard(plugin: PluginInfo, isInstalled: Boolean, onLaunch: () -> Unit, onDownload: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().height(120.dp).then(if (isInstalled) Modifier.clickable { onLaunch() } else Modifier), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = plugin.icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = if (isInstalled) MaterialTheme.colorScheme.primary else Color.Gray)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(plugin.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isInstalled) MaterialTheme.colorScheme.onSurface else Color.Gray)
                Text(plugin.description, style = MaterialTheme.typography.bodyMedium, color = if (isInstalled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray)
            }
            if (isInstalled) Icon(Icons.Default.CheckCircle, contentDescription = "Установлено", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            else Button(onClick = onDownload, modifier = Modifier.height(40.dp)) { Text("Скачать") }
        }
    }
}
