// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("FunctionName", "SpellCheckingInspection")

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.xiaoyv.utilcode.util.ConvertUtils
import com.xiaoyv.utilcode.util.FileUtils
import com.xiaoyv.utilcode.util.ZipUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import org.jsoup.Jsoup
import java.awt.Desktop
import java.io.File
import java.net.URI


class DesktopApplication : CoroutineScope by MainScope() {
    lateinit var rootWindowState: WindowState

    private val errorHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        coroutineContext.cancel()
        throwable.printStackTrace()
        isDownloading = false
    }

    /**
     * 调度器
     */
    private val dispatcherIO = Dispatchers.IO + errorHandler
    private val dispatcherMain = Dispatchers.IO + errorHandler

    private var isDownloading = false

    private val zipDir = System.getProperty("user.dir") + File.separator + "yuzu"
    private val dataDir = System.getProperty("user.dir") + File.separator + "data"

    @Composable
    @Preview
    fun onCreate() {
        val githubUrl = "https://github.com/pineappleEA/pineapple-src"

        // 更新按钮文案
        val updateBtnText = mutableStateOf("Checking update...")

        // 更新按钮状态
        val updateBtnEnable: MutableState<Boolean> = mutableStateOf(false)
        val updateBtnColor: MutableState<Color> = mutableStateOf(Color(0xFF5555FF))
        val updateProgress: MutableState<Float> = mutableStateOf(0f)
        val updateProgressText: MutableState<String> = mutableStateOf("")
        val showDialog = mutableStateOf(false)
        val scaffoldState = rememberScaffoldState()

        // 更新检测
        fun checkYuzuNewVersion() {

            launch(dispatcherMain) {
                // 最新版地址
                val (name, url) = withContext(Dispatchers.IO) {
                    runCatching {
                        val connect = Jsoup.connect("https://pineappleea.github.io").apply {
                            timeout(10000)
                        }
                        val document = connect.get()
                        val scrollboxs = document.select("div.scrollbox > a")
                        // 最新的一条
                        val element = scrollboxs.firstOrNull()
                        val versionName = element?.text().orEmpty()
                        val sourceUrl = element?.attr("href").orEmpty()
                        return@withContext versionName to sourceUrl
                    }
                    return@withContext "" to ""
                }
                if (name.isBlank() || url.isBlank()) {
                    updateBtnColor.value = Color(0xFFFF6E6E)
                    updateBtnText.value = "Recheck for updates"
                    updateBtnEnable.value = true

                    scaffoldState.snackbarHostState.showSnackbar(
                        message = "Check update fail, please try again!", duration = SnackbarDuration.Short
                    )
                    return@launch
                }
                updateBtnText.value = "Downloading..."
                updateBtnEnable.value = false

                // 下载
                downloadYuzu(url = url, progress = { progress, curr ->
                    updateProgress.value = progress
                    updateProgressText.value = String.format("%.2f%%", progress * 100)
                }, fail = {
                    scaffoldState.snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
                }, success = {
                    val memorySize = ConvertUtils.byte2FitMemorySize(FileUtils.getFileLength(it.absolutePath), 1)
                    updateBtnText.value = "Update success: $memorySize"
                    updateBtnEnable.value = false
                })
            }
        }


        MaterialTheme {
            Scaffold(scaffoldState = scaffoldState) {
                Column(
                    modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.size(60.dp))
                    Image(
                        painter = painterResource("icon/ic_app.webp"),
                        contentDescription = "",
                        modifier = Modifier.size(150.dp),
                    )
                    Text(
                        text = "Yuzu Early Access Updater", style = MaterialTheme.typography.body1
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Row {
                        Text(
                            text = "Source from",
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray,
                        )
                        Spacer(modifier = Modifier.size(4.dp))

                        ClickableText(
                            text = buildAnnotatedString {
                                append(
                                    AnnotatedString(
                                        text = githubUrl,
                                        paragraphStyle = MaterialTheme.typography.caption.toParagraphStyle(),
                                        spanStyle = SpanStyle(color = Color.Gray, textDecoration = TextDecoration.Underline)
                                    )
                                )
                            }, style = MaterialTheme.typography.caption
                        ) {
                            Desktop.getDesktop().browse(URI.create(githubUrl))
                        }
                    }
                    Spacer(modifier = Modifier.size(60.dp))

                    Button(modifier = Modifier.width(250.dp), colors = ButtonDefaults.buttonColors(
                        backgroundColor = remember { updateBtnColor }.value, contentColor = Color.White
                    ), enabled = remember { updateBtnEnable }.value, onClick = {
                        updateBtnEnable.value = false
                        updateBtnText.value = "Checking update..."
                        scaffoldState.snackbarHostState.currentSnackbarData?.dismiss()

                        checkYuzuNewVersion()
                    }) {
                        Text(remember { updateBtnText }.value)
                    }
                    Spacer(modifier = Modifier.size(5.dp))
                    Button(modifier = Modifier.width(250.dp), colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF88EE88), contentColor = Color.White
                    ), onClick = click@{
                        if (isDownloading) {
                            launch {
                                scaffoldState.snackbarHostState.currentSnackbarData?.dismiss()
                                scaffoldState.snackbarHostState.showSnackbar("Downloading, please wait!")
                            }
                            return@click
                        }
                        openZip(scaffoldState, showDialog)
                    }) {
                        Text("Launch Yuzu")
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            text = remember { updateProgressText }.value, style = MaterialTheme.typography.caption, color = Color.Gray
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                    }
                    Spacer(modifier = Modifier.size(5.dp))
                    // 进度条
                    LinearProgressIndicator(
                        progress = remember { updateProgress }.value, color = Color(0xFF88EE88), modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                }

                // Loading
                if (remember { showDialog }.value) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(
                            contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp).background(color = Color(0xFF666666), shape = RoundedCornerShape(8.dp))
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
        }


        checkYuzuNewVersion()
    }

    private fun openZip(scaffoldState: ScaffoldState, showDialog: MutableState<Boolean>) {
        launch(dispatcherMain) {
            showDialog.value = true

            val yuzuExe = withContext(dispatcherIO) {
                runCatching {
                    val dataDir = File(dataDir)
                    dataDir.mkdirs()

                    // 删除
                    FileUtils.deleteAllInDir(dataDir)

                    // 解压
                    val time = System.currentTimeMillis()
                    val newZipFile = File(zipDir).listFiles()?.minByOrNull { it.lastModified() }
                    ZipUtils.unzipFile(newZipFile, dataDir)
                    println("解压耗时：${System.currentTimeMillis() - time} ms")

                    val dirName = dataDir.listFiles()?.firstOrNull()?.name.orEmpty()
                    val yuzuPath = dataDir.absolutePath + File.separator + dirName + File.separator + "yuzu.exe"
                    return@withContext FileUtils.getFileByPath(yuzuPath)
                }.onFailure {
                    showDialog.value = false
                    scaffoldState.snackbarHostState.currentSnackbarData?.dismiss()
                    scaffoldState.snackbarHostState.showSnackbar(it.message.toString())
                }.getOrNull()
            }
            showDialog.value = false
            println("yuzuExe: $yuzuExe")
            runCatching { Desktop.getDesktop().open(yuzuExe) }
        }
    }

    /**
     * 下载
     */
    private fun downloadYuzu(
        url: String,
        progress: suspend (Float, Long) -> Unit = { _, _ -> },
        fail: suspend (String) -> Unit = {},
        success: suspend (File) -> Unit = {},
    ) {
        File(zipDir).mkdirs()

        isDownloading = true

        launch(dispatcherMain) {
            val file = withContext(dispatcherIO) {
                runCatching {
                    println("Tag: $url")
                    val tag = url.substring(url.lastIndexOf("/") + 1)
                    val downloadUrl = String.format(
                        "https://github.com/pineappleEA/pineapple-src/releases/download/%s/Windows-Yuzu-%s.zip", tag, tag
                    )

                    // 文件名
                    val fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"))
                    println("Donwload url: $downloadUrl")

                    val zipFile = File(zipDir + File.separator + fileName)
                    callbackFlow {
                        val download = CharByteKit.download(downloadUrl, zipDir, fileName) { progress, cur, total ->
                            trySend(progress to cur)
                            if (progress == 1f) {
                                close()
                            }
                        }
                        println("Download result: $download")
                        awaitClose()
                    }.buffer(
                        capacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST
                    ).collect { p: Pair<Float, Long> ->
                        withContext(dispatcherMain) {
                            progress.invoke(p.first, p.second)
                        }
                    }
                    isDownloading = false
                    return@withContext zipFile
                }
                return@withContext null
            }
            if (file == null) {
                isDownloading = false
                fail.invoke("Download error: $url")
            } else {
                isDownloading = false
                success.invoke(file)
            }
        }
    }
}


fun main() = application {
    val windowState = rememberWindowState()

    val app = DesktopApplication()
    app.rootWindowState = windowState

    Window(state = windowState.apply {
        position = WindowPosition(alignment = Alignment.Center)
    }, onCloseRequest = {
        app.cancel()
        // 退出
        exitApplication()
    }, title = "Yuzu Early Access Updater", icon = painterResource("icon/ic_launcher.svg"), undecorated = false, focusable = true, resizable = false
    ) {
        app.onCreate()
    }
}
