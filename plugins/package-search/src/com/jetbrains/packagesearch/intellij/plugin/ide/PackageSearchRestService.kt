package com.jetbrains.packagesearch.intellij.plugin.ide

import com.google.gson.GsonBuilder
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.io.origin
import com.intellij.util.net.NetUtils
import com.intellij.util.text.nullize
import com.jetbrains.packagesearch.intellij.plugin.PACKAGE_SEARCH_NOTIFICATION_GROUP_ID
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackageSearchToolWindowFactory
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.QueryStringDecoder
import java.net.URI
import java.net.URISyntaxException
import org.jetbrains.ide.RestService

internal class PackageSearchRestService : RestService() {
    override fun getServiceName() = "packageSearch"

    override fun isMethodSupported(method: HttpMethod) = method === HttpMethod.GET || method === HttpMethod.POST

    override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
        when (getStringParameter("action", urlDecoder)) {
            "projects" -> listProjects(request, context)
            "install" -> installPackage(request, context)
            else -> sendStatus(HttpResponseStatus.NOT_FOUND, HttpUtil.isKeepAlive(request), context.channel())
        }

        return null
    }

    private fun listProjects(request: FullHttpRequest, context: ChannelHandlerContext) {
        val out = BufferExposingByteArrayOutputStream()
        val name = ApplicationNamesInfo.getInstance().productName
        val build = ApplicationInfo.getInstance().build

        createJsonWriter(out).apply {
            beginObject()

            name("name").value(name)
            name("buildNumber").value(build.asString())
            name("projects")
            beginArray()
            ProjectManager.getInstance().openProjects.forEach { value(it.name) }
            endArray()
            endObject()

            close()
        }

        send(out, request, context)
    }

    private fun installPackage(
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ) {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val data: InstallPackageRequest = gson.fromJson(createJsonReader(request), InstallPackageRequest::class.java)
        val project = ProjectManager.getInstance().openProjects.find { it.name.toLowerCase() == data.project?.toLowerCase() }
        val pkg = data.`package`
        val query = data.query.nullize(true)
        if (project == null || pkg == null) {
            sendStatus(HttpResponseStatus.BAD_REQUEST, HttpUtil.isKeepAlive(request), context.channel())
            return
        }

        ApplicationManager.getApplication().invokeLater {
            ProjectUtil.focusProjectWindow(project, true)

            PackageSearchToolWindowFactory.activateToolWindow(project) {
                project.getUserData(PackageSearchToolWindowFactory.ToolWindowModelKey)?.let {
                    it.selectedPackage.set(pkg)
                    it.searchTerm.set(query ?: pkg.replace(':', ' '))

                    notify(project, pkg)
                }
            }
        }

        sendOk(request, context)
    }

    override fun isAccessible(request: HttpRequest) = true

    override fun isHostTrusted(request: FullHttpRequest): Boolean {
        val origin = request.origin
        val originHost = try {
            if (origin == null) null else URI(origin).host.nullize()
        } catch (ignored: URISyntaxException) {
            return false
        }

        return (originHost != null &&
            (originHost == "package-search.jetbrains.com" ||
                originHost.endsWith("package-search.services.jetbrains.com") ||
                NetUtils.isLocalhost(originHost))) ||
            super.isHostTrusted(request)
    }

    private fun notify(project: Project, pkg: String) = NotificationGroup.balloonGroup(PACKAGE_SEARCH_NOTIFICATION_GROUP_ID)
        .createNotification(
            PackageSearchBundle.message("packagesearch.title"),
            pkg,
            PackageSearchBundle.message("packagesearch.restService.readyForInstallation"),
            NotificationType.INFORMATION
        )
        .notify(project)
}

internal class InstallPackageRequest {
    var project: String? = null
    var `package`: String? = null
    var query: String? = null
}
