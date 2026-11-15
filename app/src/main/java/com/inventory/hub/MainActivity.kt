package com.inventory.hub

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader

data class PluginInfo(
    val id: String, val name: String, val description: String,
    val icon: ImageVector, val packageName: String, val githubReleaseUrl: String
)

class MainActivity : ComponentActivity() {
    private val db by lazy { ProductDatabase.getInstance(this) }
    private val _productCount = MutableStateFlow(0)
    val productCount: StateFlow<Int> = _productCount.asStateFlow()

    private val plugins = listOf(
        PluginInfo("scanner", "Штрих-код", "Сканирование и сохранение в историю", Icons.Default.QrCodeScanner, "com.inventory.scanner", "https://github.com/kbav26t-netizen/ScannerPlugin/releases/latest/download/scanner-plugin-arm64-v8a-debug.apk"),
        PluginInfo("exporter", "Экспорт Excel", "Выгрузка данных в таблицу", Icons.Default.TableChart, "com.inventory.exporter", "https://github.com/kbav26t-netizen/InventoryExporter/releases/latest/download/exporter-plugin-debug.apk")
    )

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri?
        if (uri != null) loadFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val count by productCount.collectAsState()
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HubScreen(
                        plugins = plugins,
                        productCount = count,
                        onLaunchPlugin = ::launchPlugin,
                        onDownloadPlugin = ::downloadPlugin,
                        onLoadReference = { filePicker.launch("*/*") }
                    )
                }
            }
        }
    }

    private fun loadFile(uri: Uri) {
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Обработка файла...", Toast.LENGTH_SHORT).show()
                }
                db.productDao().clearAll()
                val products = mutableListOf<ProductEntity>()
                val fileName = getFileName(uri) ?: ""
                when {
                    fileName.endsWith(".csv", ignoreCase = true) -> parseCsv(uri, products)
                    fileName.endsWith(".xls", ignoreCase = true) || fileName.endsWith(".xlsx", ignoreCase = true) -> parseExcel(uri, products)
                    else -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Неподдерживаемый формат. Используйте CSV или Excel", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }
                if (products.isNotEmpty()) {
                    db.productDao().insertAll(products)
                    _productCount.value = products.size
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Успешно загружено: ${products.size} товаров", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Файл пуст или не содержит данных", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }
    }

    private fun parseCsv(uri: Uri, products: MutableList<ProductEntity>) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(";")
                    if (parts.size >= 4) {
                        val article = parts[1].trim()
                        val barcode = parts[2].trim()
                        val name = parts[3].trim()
                        val qty = parts.getOrNull(4)?.trim()?.toIntOrNull() ?: 0
                        if (barcode.isNotBlank()) {
                            products.add(ProductEntity(barcode, name, article, qty))
                        }
                    }
                }
            }
        }
    }

    private fun parseExcel(uri: Uri, products: MutableList<ProductEntity>) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val article = getCellValue(row.getCell(1))
                val barcode = getCellValue(row.getCell(2))
                val name = getCellValue(row.getCell(3))
                val qtyStr = getCellValue(row.getCell(4))
                val qty = qtyStr.toIntOrNull() ?: 0
                if (barcode.isNotBlank()) {
                    products.add(ProductEntity(barcode, name, article, qty))
                }
            }
            workbook.close()
        }
    }

    private fun getCellValue(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                val num = cell.numericCellValue
                if (num == num.toLong().toDouble()) num.toLong().toString().trim()
                else num.toString().trim()
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString().trim()
            CellType.FORMULA -> {
                try { cell.numericCellValue.toLong().toString().trim() }
                catch (e: Exception) { cell.stringCellValue.trim() }
            }
            else -> ""
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) result = cursor.getString(nameIndex)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf("/")
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
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
fun HubScreen(plugins: List<PluginInfo>, productCount: Int, onLaunchPlugin: (PluginInfo) -> Unit, onDownloadPlugin: (PluginInfo) -> Unit, onLoadReference: () -> Unit) {
    val context = LocalContext.current
    var installedPlugins by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        installedPlugins = plugins.associate { plugin ->
            plugin.id to try { pm.getPackageInfo(plugin.packageName, 0); true } catch (e: PackageManager.NameNotFoundException) { false }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Инвентаризация") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Выберите режим работы", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))

            Card(modifier = Modifier.fillMaxWidth().clickable { onLoadReference() }.padding(bottom = 16.dp), shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Загрузить справочник", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Поддерживается: CSV, XLS, XLSX\
В базе: $productCount товаров", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
