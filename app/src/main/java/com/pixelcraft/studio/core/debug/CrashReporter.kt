package com.pixelcraft.studio.core.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/** Saves the reason of the last crash so it can be shown on the next launch. */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 8000

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = buildString {
                    append("Android ").append(Build.VERSION.SDK_INT)
                    append(" | ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    append("\nThread: ").append(thread.name)
                    append("\n\n").append(Log.getStackTraceString(error))
                }
                File(appContext.filesDir, FILE_NAME).writeText(report.take(MAX_CHARS))
            } catch (_: Throwable) {
                // Never let the reporter itself hide the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Returns the saved crash report once, then deletes it. */
    fun readAndClear(context: Context): String? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) {
                null
            } else {
                val text = file.readText()
                file.delete()
                text.ifBlank { null }
            }
        } catch (_: Throwable) {
            null
        }
    }
}

@Composable
fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعطل التطبيق في المرة السابقة") },
        text = {
            Text(
                text = report.take(3000),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("crash", report))
                    Toast.makeText(context, "تم نسخ التقرير", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("نسخ التقرير")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
