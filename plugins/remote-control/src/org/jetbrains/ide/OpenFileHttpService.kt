// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.impl.ProjectUtil.focusProjectWindow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectForContentFile
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.ui.AppUIUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.io.systemIndependentPath
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.builtInWebServer.WebServerPathToFileManager
import org.jetbrains.builtInWebServer.checkAccess
import org.jetbrains.concurrency.*
import org.jetbrains.io.send
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.regex.Pattern
import javax.swing.SwingUtilities
import kotlin.io.path.exists

private val NOT_FOUND = createError("not found")
private val LINE_AND_COLUMN = Pattern.compile("^(.*?)(?::(\\d+))?(?::(\\d+))?$")

/**
 * @api {get} /file Open file
 * @apiName file
 * @apiGroup Platform
 *
 * @apiParam {String} file The path of the file. Relative (to project base dir, VCS root, module source or content root) or absolute.
 * @apiParam {Integer} [line] The line number of the file (1-based).
 * @apiParam {Integer} [column] The column number of the file (1-based).
 * @apiParam {Boolean} [focused=true] Whether to focus project window.
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
internal class OpenFileHttpService : RestService() {
  @Volatile private var refreshSessionId: Long = 0
  private val requests = ConcurrentLinkedQueue<OpenFileTask>()

  override fun getServiceName() = "file"

  override fun isMethodSupported(method: HttpMethod) = method === HttpMethod.GET || method === HttpMethod.POST

  override fun isOriginAllowed(request: HttpRequest) = OriginCheckResult.ASK_CONFIRMATION

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val keepAlive = HttpUtil.isKeepAlive(request)
    val channel = context.channel()

    val apiRequest: OpenFileRequest
    if (request.method() === HttpMethod.POST) {
      apiRequest = gson.fromJson(createJsonReader(request), OpenFileRequest::class.java)
    }
    else {
      apiRequest = OpenFileRequest()
      apiRequest.file = getStringParameter("file", urlDecoder).takeIf { !it.isNullOrBlank() }
      apiRequest.line = getIntParameter("line", urlDecoder)
      apiRequest.column = getIntParameter("column", urlDecoder)
      apiRequest.focused = getBooleanParameter("focused", urlDecoder, true)
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

    val promise = openFile(apiRequest, context, request) ?: return null
    promise
      .onSuccess {
        sendOk(request, context)
      }
      .onError {
        if (it === NOT_FOUND) {
          // don't expose file status
          sendStatus(HttpResponseStatus.NOT_FOUND.orInSafeMode(HttpResponseStatus.OK), keepAlive, channel)
          LOG.warn("File ${apiRequest.file} not found")
        }
        else {
          // todo send error
          sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR, keepAlive, channel)
          LOG.error(it)
        }
      }
    return null
  }

  private fun openFile(request: OpenFileRequest, context: ChannelHandlerContext, httpRequest: HttpRequest?): Promise<Void?>? {
    val systemIndependentPath = FileUtil.toSystemIndependentName(FileUtil.expandUserHome(request.file!!))
    val file = Paths.get(FileUtil.toSystemDependentName(systemIndependentPath))
    if (file.isAbsolute) {
      if (!file.exists()) {
        return rejectedPromise(NOT_FOUND)
      }

      var isAllowed = checkAccess(file)
      if (isAllowed && com.intellij.ide.impl.ProjectUtil.isRemotePath(systemIndependentPath)) {
        // invokeAndWait is added to avoid processing many requests in this place: e.g. to prevent abuse of opening many remote files
        SwingUtilities.invokeAndWait {
          isAllowed = com.intellij.ide.impl.ProjectUtil.confirmLoadingFromRemotePath(systemIndependentPath, "warning.load.file.from.share", "title.load.file.from.share")
        }
      }

      if (isAllowed) {
        return openAbsolutePath(file, request)
      }
      else {
        HttpResponseStatus.FORBIDDEN.orInSafeMode(HttpResponseStatus.OK).send(context.channel(), httpRequest)
        return null
      }
    }

    // we don't want to call refresh for each attempt on findFileByRelativePath call, so, we do what ourSaveAndSyncHandlerImpl does on frame activation
    val queue = RefreshQueue.getInstance()
    queue.cancelSession(refreshSessionId)
    val mainTask = OpenFileTask(FileUtil.toCanonicalPath(systemIndependentPath, '/'), request)
    requests.offer(mainTask)
    val clientId = ClientId.ownerId
    val session = queue.createSession(true, true, {
      while (true) {
        val task = requests.poll() ?: break
        task.promise.catchError {
          if (openRelativePath(task.path, task.request, clientId)) {
            task.promise.setResult(null)
          }
          else {
            task.promise.setError(NOT_FOUND)
          }
        }
      }
    }, ModalityState.NON_MODAL)

    session.addAllFiles(*ManagingFS.getInstance().localRoots)
    refreshSessionId = session.id
    session.launch()
    return mainTask.promise
  }
}

internal class OpenFileRequest {
  var file: String? = null
  // The line number of the file (1-based)
  var line = 0
  // The column number of the file (1-based)
  var column = 0

  var focused = true
}

private class OpenFileTask(val path: String, val request: OpenFileRequest) {
  internal val promise = AsyncPromise<Void?>()
}

private fun navigate(project: Project?, file: VirtualFile, request: OpenFileRequest) {
  val effectiveProject = project ?: RestService.getLastFocusedOrOpenedProject() ?: ProjectManager.getInstance().defaultProject
  // OpenFileDescriptor line and column number are 0-based.
  OpenFileDescriptor(effectiveProject, file, Math.max(request.line - 1, 0), Math.max(request.column - 1, 0)).navigate(true)
  if (request.focused) {
    focusProjectWindow(project, true)
  }
}

// path must be normalized
private fun openRelativePath(path: String, request: OpenFileRequest, clientId: ClientId): Boolean {
  ClientId.withClientId(clientId) {
    return openRelativePath(path, request)
  }
}

private fun openRelativePath(path: String, request: OpenFileRequest): Boolean {
  var virtualFile: VirtualFile? = null
  var project: Project? = null

  val projects = ProjectManager.getInstance().openProjects
  for (openedProject in projects) {
    openedProject.baseDir?.let {
      virtualFile = it.findFileByRelativePath(path)
    }

    if (virtualFile == null) {
      virtualFile = WebServerPathToFileManager.getInstance(openedProject).findVirtualFile(path)
    }
    if (virtualFile != null) {
      project = openedProject
      break
    }
  }

  return virtualFile?.let {
    AppUIUtil.invokeLaterIfProjectAlive(project!!, Runnable { navigate(project, it, request) })
    true
  } ?: false
}

private fun openAbsolutePath(file: Path, request: OpenFileRequest): Promise<Void?> {
  val promise = AsyncPromise<Void?>()
  val task = Runnable {
    promise.catchError {
      val virtualFile = runWriteAction {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(file.systemIndependentPath)
      }
      if (virtualFile == null) {
        promise.setError(NOT_FOUND)
      }
      else {
        navigate(guessProjectForContentFile(virtualFile), virtualFile, request)
        promise.setResult(null)
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
  return promise
}
