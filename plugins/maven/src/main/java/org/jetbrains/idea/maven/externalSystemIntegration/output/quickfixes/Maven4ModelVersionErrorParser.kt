// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.Cancellation.checkCancelled
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenBuildIssueHandler
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.externalSystemIntegration.output.*
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.MavenImportLoggedEventParser
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenEventType
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.io.path.Path
import kotlin.io.path.exists

@ApiStatus.Internal
class Maven4ModelVersionErrorParser(
  val eventHandlerProvider: (Project) -> MavenBuildIssueHandler,
  val pathChecker: (Path) -> Boolean,
  val triggers: List<Regex>,
) : MavenLoggedEventParser, MavenSpyLoggedEventParser, MavenImportLoggedEventParser {

  constructor() : this({ MavenProjectsManager.getInstance(it).syncConsole },
                       { it.exists() },
                       if (SystemInfo.isWindows) TRIGGER_LINES_WINDOWS else TRIGGER_LINES_UNIX
  )
  override fun supportsType(type: LogMessageType?): Boolean {
    return true;
  }

  override fun checkLogLine(parentId: Any, parsingContext: MavenParsingContext, logLine: MavenLogEntryReader.MavenLogEntry, logEntryReader: MavenLogEntryReader, messageConsumer: Consumer<in BuildEvent>): Boolean {

    return processLogLine(parentId, parsingContext, logLine.line, messageConsumer)
  }

  override fun supportsType(type: MavenEventType): Boolean {
    return true;
  }

  private fun getModelFromPath(project: Project, path: Path): Pair<String, Int>? {
    val virtualFile = VfsUtil.findFile(path, false) ?: return null
    return runBlockingMaybeCancellable {
      readAction {
        val modelVersion = MavenDomUtil.getMavenDomProjectModel(project, virtualFile)?.modelVersion
                           ?: return@readAction null

        val value = modelVersion.value ?: return@readAction null
        val offset = modelVersion.xmlElement?.navigationElement?.textOffset ?: 0
        return@readAction value to offset
      }
    }
  }

  override fun processLogLine(parentId: Any, parsingContext: MavenParsingContext, logLine: String, messageConsumer: Consumer<in BuildEvent>): Boolean {
    val buildIssue = createBuildIssue(logLine, parsingContext.ideaProject) ?: return false
    messageConsumer.accept(BuildIssueEventImpl(parentId, buildIssue, MessageEvent.Kind.ERROR))
    return true
  }

  private fun createBuildIssue(
    logLine: String,
    project: Project,
  ): BuildIssue? {
    for (trigger in triggers) {
      val match = trigger.find(logLine)
      if (match == null) continue

      val fileName = match.groupValues[1]
      val path = Path(fileName)
      if (pathChecker(path)) {
        val modelAndOffset = getModelFromPath(project, path)
        if (modelAndOffset == null || modelAndOffset.first == "4.0.0")
          return newBuildIssue(logLine, path, modelAndOffset?.second)
      }
    }
    return null
  }

  private fun newBuildIssue(line: String, path: Path, offset: Int?): BuildIssue {
    return object : BuildIssue {
      override val title: @BuildEventsNls.Title String
        get() = SyncBundle.message("maven.sync.incorrect.model.version")
      override val description: @BuildEventsNls.Description String
        get() = SyncBundle.message("maven.sync.incorrect.model.version.desc", line.trim(), UpdateVersionQuickFix.ID)
      override val quickFixes: List<BuildIssueQuickFix>
        get() = listOf(UpdateVersionQuickFix(path))

      override fun getNavigatable(project: Project) = offset?.let { PathNavigatable(project, path, it) }
    }
  }

  override fun processLogLine(project: Project, logLine: String, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>): Boolean {
    val buildIssue = createBuildIssue(logLine, project) ?: return false
    val console = eventHandlerProvider(project)
    val kind = MessageEvent.Kind.WARNING
    console.addBuildIssue(buildIssue, kind)
    return true
  }
}

class UpdateVersionQuickFix(val path: Path) : BuildIssueQuickFix {
  override val id: String = ID


  private suspend fun collectFiles(projectsManager: MavenProjectsManager, project: Project): List<VirtualFile> {
    val virtualFile = VfsUtil.findFile(path, false) ?: return emptyList()
    val mavenProject = projectsManager.findProject(virtualFile)
    if (mavenProject != null) {
      return withBackgroundProgress(project, MavenProjectBundle.message("maven.project.updating.model")) {
        val filesToUpdate = LinkedHashSet<VirtualFile>()
        reportRawProgress { reporter ->
          reporter.fraction(null)
          val rootProject = projectsManager.findRootProject(mavenProject) ?: return@withBackgroundProgress listOf(virtualFile)
          val queue = ArrayDeque<MavenProject>()
          queue.add(rootProject)
          while (queue.isNotEmpty() && isActive) {
            val curr = queue.removeFirst()
            if (filesToUpdate.add(curr.file)) {
              projectsManager.getModules(curr).forEach { queue.add(it) }
            }
            reporter.text(MavenProjectBundle.message("maven.project.updating.model.collectFiles", filesToUpdate.size))
          }
        }
        return@withBackgroundProgress filesToUpdate.toList()
      }
    }
    else return listOf(virtualFile)
  }

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val projectsManager = MavenProjectsManager.getInstance(project)
    val future = CompletableFuture<Void>()
    val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
    cs.launch(Dispatchers.IO) {
      try {
        val filesToUpdate = collectFiles(projectsManager, project)
        updateFiles(project, filesToUpdate)
        withContext(Dispatchers.EDT) {
          FileDocumentManager.getInstance().saveAllDocuments()
          MavenProjectsManager.getInstance(project).scheduleUpdateAllMavenProjects(MavenSyncSpec.full("Update model version quick fix", true))
        }
        future.complete(null)
      }
      catch (e: Throwable) {
        future.completeExceptionally(e)
      }
    }
    return future
  }

  private suspend fun updateFiles(
    project: Project,
    filesToUpdate: List<VirtualFile>,
  ) {
    withBackgroundProgress(project, MavenProjectBundle.message("maven.project.updating.model")) {
      reportRawProgress { reporter ->
        var processed = 0
        for (file in filesToUpdate) {
          checkCancelled()
          reporter.text(MavenProjectBundle.message("maven.project.updating.model.updatingFiles", file.parent.name))
          writeAction {
            if (!file.isValid()) return@writeAction
            file.refresh(false, false)
            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile == null) return@writeAction
            if (psiFile !is XmlFile) return@writeAction
            val documentManager = PsiDocumentManager.getInstance(project)
            val document = documentManager.getDocument(psiFile) ?: return@writeAction

            val model = MavenDomUtil.getMavenDomModel(psiFile, MavenDomProjectModel::class.java) ?: return@writeAction
            val modelVersion = model.modelVersion
            executeCommand(project, MavenProjectBundle.message("maven.project.updating.model.command.name")) {
              if (modelVersion.exists()) {
                modelVersion.stringValue = MavenConstants.MODEL_VERSION_4_1_0
              }
              val rootTag = psiFile.document?.rootTag ?: return@executeCommand
              if (rootTag.getAttribute("xmlns:xsi") == null) {
                rootTag.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
              }
              rootTag.setAttribute("xmlns", NEW_XMLNS)
              rootTag.setAttribute("xsi:schemaLocation", NEW_SCHEMA_LOCATION)
            }

            documentManager.doPostponedOperationsAndUnblockDocument(document)
            documentManager.commitDocument(document)


          }
          processed++
          reporter.fraction(processed.toDouble() / filesToUpdate.size)
        }
      }
    }
  }

  companion object {
    internal const val ID = "maven_model_ver_update_410"
    private const val NEW_XMLNS = "http://maven.apache.org/POM/4.1.0"
    private const val NEW_SCHEMA_LOCATION = "http://maven.apache.org/POM/4.1.0 https://maven.apache.org/xsd/maven-4.1.0.xsd"
  }
}

@ApiStatus.Internal
val TRIGGER_LINES_UNIX: List<Regex> = listOf(
  "'subprojects' unexpected subprojects element @ [^,]*, (.*)",
  "'subprojects' unexpected subprojects element at (.*?)[:,$]",
  "the model contains elements that require a model version of 4.1.0 @ .*? file://(.*?)[:,$]",
  "the model contains elements that require a model version of 4.1.0 at file://(.*?)[:,$]",
).map { it.toRegex() };

@ApiStatus.Internal
val TRIGGER_LINES_WINDOWS: List<Regex> = listOf(
  "'subprojects' unexpected subprojects element @ [^,]*, (.*)",
  "'subprojects' unexpected subprojects element at ([A-Za-z][:].*?)[:,$]",
  "the model contains elements that require a model version of 4.1.0 @ .*? file://([A-Za-z][:].*?.*?)[:,$]",
  "the model contains elements that require a model version of 4.1.0 at file://([A-Za-z][:].*?.*?)[:,$]",
).map { it.toRegex() };


