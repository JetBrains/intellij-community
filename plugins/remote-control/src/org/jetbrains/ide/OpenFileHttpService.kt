// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectForContentFile
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.builtInWebServer.WebServerPathToFileManager
import org.jetbrains.builtInWebServer.checkAccess
import org.jetbrains.io.send
import java.nio.file.Path
import java.util.regex.Pattern
import javax.swing.SwingUtilities
import kotlin.io.path.exists
import kotlin.math.max

/**
 * @api {get} /file Open file
 * @apiName file
 * @apiGroup Platform
 *
 * @apiParam {String} file The path of the file. Relative (to project base dir, VCS root, module source or content root) or absolute.
 * @apiParam {Integer} [line] The line number of the file (1-based).
 * @apiParam {Integer} [column] The column number of the file (1-based).
 * @apiParam {Boolean} [focused=true] Whether to focus a project window.
 *
 * @apiExample {curl} Absolute path
 * curl http://localhost:63342/api/file//absolute/path/to/file.kt
 *
 * @apiExample {curl} Relative path
 * curl http://localhost:63342/api/file/relative/to/module/root/path/to/file.kt
 *
 * @apiExample {curl} With line and column
 * curl http://localhost:63342/api/file/relative/to/module/root/path/to/file.kt:100:34
 *
 * @apiExample {curl} Query parameters
 * curl http://localhost:63342/api/file?file=path/to/file.kt&line=100&column=34
 */
@Suppress("KDocUnresolvedReference")
internal class OpenFileHttpService : RestService() {
  private val LINE_AND_COLUMN = Pattern.compile("^(.*?)(?::(\\d+))?(?::(\\d+))?$")

  override fun getServiceName() = "file"

  override fun isMethodSupported(method: HttpMethod) = method === HttpMethod.GET || method === HttpMethod.POST

  override fun isOriginAllowed(request: HttpRequest) = OriginCheckResult.ASK_CONFIRMATION

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val keepAlive = HttpUtil.isKeepAlive(request)
    val channel = context.channel()

    val apiRequest = if (request.method() === HttpMethod.POST) {
      gson.fromJson(createJsonReader(request), OpenFileRequest::class.java)
    }
    else {
      OpenFileRequest().apply {
        file = getStringParameter("file", urlDecoder).takeIf { !it.isNullOrBlank() }
        line = getIntParameter("line", urlDecoder)
        column = getIntParameter("column", urlDecoder)
        focused = getBooleanParameter("focused", urlDecoder, true)
      }
    }

    val prefixLength = 1 + PREFIX.length + 1 + getServiceName().length + 1
    val path = urlDecoder.path()
    if (path.length > prefixLength) {
      val matcher = LINE_AND_COLUMN.matcher(path).region(prefixLength, path.length)
      LOG.assertTrue(matcher.matches())
      if (apiRequest.file == null) {
        apiRequest.file = matcher.group(1).trim { it <= ' ' }
      }
      if (apiRequest.line == -1) {
        apiRequest.line = StringUtilRt.parseInt(matcher.group(2), 1)
      }
      if (apiRequest.column == -1) {
        apiRequest.column = StringUtilRt.parseInt(matcher.group(3), 1)
      }
    }

    val requestedFile = apiRequest.file
    if (requestedFile == null) {
      return parameterMissedErrorMessage("file")
    }
    if (PathUtilRt.startsWithSeparatorSeparator(FileUtil.toSystemIndependentName(requestedFile))) {
      return "UNC paths are not supported"
    }

    val vfsPath = FileUtil.toSystemIndependentName(FileUtil.expandUserHome(requestedFile))
    val file = Path.of(FileUtil.toSystemDependentName(vfsPath))
    val fileAndProject = if (!file.isAbsolute) {
      findByRelativePath(FileUtil.toCanonicalPath(vfsPath, '/'))
    }
    else if (file.exists()) {
      var isAllowed = checkAccess(file)
      if (isAllowed && ProjectUtil.isRemotePath(vfsPath)) {
        // `invokeAndWait` is added to avoid processing many requests in this place: e.g., to prevent abuse of opening many remote files
        SwingUtilities.invokeAndWait {
          isAllowed = ProjectUtil.confirmLoadingFromRemotePath(vfsPath, "warning.load.file.from.share", "title.load.file.from.share")
        }
      }
      if (!isAllowed) {
        HttpResponseStatus.FORBIDDEN.orInSafeMode(HttpResponseStatus.OK).send(context.channel(), request)
        return null
      }
      findByAbsolutePath(file)
    }
    else null

    if (fileAndProject == null) {
      // don't expose file status
      sendStatus(HttpResponseStatus.NOT_FOUND.orInSafeMode(HttpResponseStatus.OK), keepAlive, channel)
      LOG.warn("File ${requestedFile} not found")
    }
    else {
      val (virtualFile, project) = fileAndProject
      navigate(project, virtualFile, apiRequest)
      sendOk(request, context)
    }
    return null
  }

  internal class OpenFileRequest {
    var file: String? = null
    var line = 0  // The line number of the file (1-based)
    var column = 0  // The column number of the file (1-based)
    var focused = true
  }

  private fun findByAbsolutePath(file: Path): Pair<VirtualFile, Project?>? {
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
    return if (virtualFile != null) virtualFile to runReadAction { guessProjectForContentFile(virtualFile) } else null
  }

  private fun findByRelativePath(path: String): Pair<VirtualFile, Project?>? {
    for (project in ProjectManager.getInstance().openProjects) {
      @Suppress("DEPRECATION") val file =
        project.baseDir?.findFileByRelativePath(path) ?: WebServerPathToFileManager.getInstance(project).findVirtualFile(path)
      if (file != null) {
        return file to project
      }
    }
    return null
  }

  private fun navigate(project: Project?, file: VirtualFile, request: OpenFileRequest) {
    val clientId = ClientId.ownerId
    val task = Runnable {
      ClientId.withClientId(clientId) {
        val effectiveProject = project ?: getLastFocusedOrOpenedProject() ?: ProjectManager.getInstance().defaultProject
        // OpenFileDescriptor line and column number are 0-based.
        OpenFileDescriptor(effectiveProject, file, max(request.line - 1, 0), max(request.column - 1, 0)).navigate(true)
        if (request.focused) {
          ProjectUtil.focusProjectWindow(project, true)
        }
      }
    }
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode) {
      app.invokeAndWait(task)
    }
    else {
      app.invokeLater(task)
    }
  }
}
