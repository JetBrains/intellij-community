package com.jetbrains.packagesearch.intellij.plugin.rest

import com.google.gson.GsonBuilder
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.io.origin
import com.intellij.util.net.NetUtils
import com.intellij.util.text.nullize
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackageSearchToolWindowFactory
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.ide.RestService
import java.net.URI
import java.net.URISyntaxException

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
        val data = gson.fromJson<InstallPackageRequest>(createJsonReader(request), InstallPackageRequest::class.java)
        val project = ProjectManager.getInstance().openProjects.find { it.name.equals(data.project, ignoreCase = true) }
        val pkg = data.`package`
        val query = data.query.nullize(true)
        if (project == null || pkg == null) {
            sendStatus(HttpResponseStatus.BAD_REQUEST, HttpUtil.isKeepAlive(request), context.channel())
            return
        }

        AppUIExecutor.onUiThread().execute {
            ProjectUtil.focusProjectWindow(project, true)

            PackageSearchToolWindowFactory.activateToolWindow(project) {
//                project.packageSearchDataService.programmaticSearchQueryStateFlow.tryEmit(query ?: pkg.replace(':', ' '))
//                rootModel.setSelectedPackage(pkg) // TODO preselect proper package

                notify(project, pkg)
            }
        }

        sendOk(request, context)
    }

    override fun isAccessible(request: HttpRequest) = true

    override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
        val origin = request.origin
        val originHost = try {
            if (origin == null) null else URI(origin).host.nullize()
        } catch (ignored: URISyntaxException) {
            return false
        }

        val isTrusted = originHost?.let {
            it == "package-search.jetbrains.com" ||
                it.endsWith("package-search.services.jetbrains.com") ||
                NetUtils.isLocalhost(it)
        } ?: false

        return isTrusted || super.isHostTrusted(request, urlDecoder)
    }

    @Suppress("DialogTitleCapitalization") // It's the Package Search plugin name...
    private fun notify(project: Project, @Nls pkg: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(PluginEnvironment.PACKAGE_SEARCH_NOTIFICATION_GROUP_ID)
            .createNotification(
                PackageSearchBundle.message("packagesearch.title"),
                PackageSearchBundle.message("packagesearch.restService.readyForInstallation"),
                NotificationType.INFORMATION
            )
            .setSubtitle(pkg)
            .notify(project)
    }
}

internal class InstallPackageRequest {
    var project: String? = null
    @NlsSafe var `package`: String? = null
    @NonNls var query: String? = null
}
