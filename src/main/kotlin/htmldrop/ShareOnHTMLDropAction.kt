package htmldrop

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class ShareOnHTMLDropAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file?.extension?.lowercase() == "html"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file    = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val fileSize = file.length
        if (fileSize > MAX_UPLOAD_BYTES) {
            val sizeMb = "%.1f".format(fileSize / 1024.0 / 1024.0)
            Messages.showErrorDialog(project, "${file.name} is ${sizeMb} MB — exceeds the 3 MB limit.", "HTML Drop")
            return
        }

        val password = PasswordDialog(file.name, fileSize).getPassword() ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Sharing on HTML Drop…", false) {
            override fun run(indicator: ProgressIndicator) {
                runCatching {
                    val html = file.contentsToByteArray().toString(Charsets.UTF_8)
                    indicator.text = "Uploading…"
                    val url = upload(html, password)
                    ApplicationManager.getApplication().invokeLater { copyToClipboard(url) }
                    val msg = if (password.isNotEmpty()) "URL copied (password protected)" else "URL copied to clipboard"
                    notifyWithLink(project, "Shared on HTML Drop", msg, url, NotificationType.INFORMATION)
                }.onFailure { ex ->
                    notify(project, "HTML Drop Failed", ex.message ?: "Unknown error", NotificationType.ERROR)
                }
            }
        })
    }

    private fun upload(html: String, password: String): String {
        val json = buildString {
            append("{\"html\":"); append(jsonString(html))
            append(",\"ttl\":\"3d\"")
            if (password.isNotEmpty()) { append(",\"password\":"); append(jsonString(password)) }
            append("}")
        }
        val client  = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(UPLOAD_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        val retryDelays = listOf(5_000L, 15_000L, 45_000L)
        for ((attempt, delay) in (listOf(0L) + retryDelays).withIndex()) {
            if (delay > 0) Thread.sleep(delay)
            val res = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() == 429) {
                if (attempt < retryDelays.size) continue
                error("Rate limited — please try again later")
            }
            check(res.statusCode() in 200..299) { "HTML Drop ${res.statusCode()}: ${res.body()}" }
            return Regex(""""url"\s*:\s*"([^"]+)"""").find(res.body())?.groupValues?.get(1)
                ?: error("No URL in response: ${res.body()}")
        }
        error("Upload failed after retries")
    }

    private fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) when (ch) {
            '"'  -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
        }
        return sb.append('"').toString()
    }

    private fun copyToClipboard(text: String) {
        StringSelection(text).let { Toolkit.getDefaultToolkit().systemClipboard.setContents(it, it) }
    }

    private fun notifyWithLink(project: Project?, title: String, content: String, url: String, type: NotificationType) {
        val n = Notification("HTML Drop", title, content, type)
        n.addAction(object : AnAction("Open in Browser") {
            override fun actionPerformed(e: AnActionEvent) { BrowserUtil.browse(java.net.URI(url)) }
        })
        Notifications.Bus.notify(n, project)
    }

    private fun notify(project: Project?, title: String, content: String, type: NotificationType) {
        Notifications.Bus.notify(Notification("HTML Drop", title, content, type), project)
    }
}
