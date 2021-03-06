// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.google.common.primitives.Bytes
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.compiler.progress.BuildIssueContributor
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.jrt.JrtFileSystem
import com.intellij.pom.Navigatable
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.concurrency.asCompletableFuture
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader.MavenLogEntry
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.importing.MavenModuleImporter
import org.jetbrains.idea.maven.importing.MavenProjectModelModifier
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class SourceOptionQuickFix : MavenLoggedEventParser {
  override fun supportsType(type: LogMessageType?): Boolean {
    return type == LogMessageType.ERROR
  }

  override fun checkLogLine(parentId: Any,
                            parsingContext: MavenParsingContext,
                            logLine: MavenLogEntry,
                            logEntryReader: MavenLogEntryReader,
                            messageConsumer: Consumer<in BuildEvent?>): Boolean {
    if (logLine.line.startsWith("Source option 5 is no longer supported. Use 6 or later")) {
      val targetLine = logEntryReader.readLine()

      if (targetLine != null && !targetLine.line.startsWith("Target option 1.5 is no longer supported. Use 1.6 or later.")) {
        logEntryReader.pushBack()
      }
      val failedProject = parsingContext.projectsInReactor.last()
      messageConsumer.accept(
        BuildIssueEventImpl(parentId, Source5BuildIssue(parsingContext.ideaProject, failedProject), MessageEvent.Kind.ERROR));
      return true
    }

    return false
  }

}

class Source5BuildIssue(project: Project, private val failedProjectId: String) : BuildIssue {

  override val quickFixes: List<UpdateSourceLevelQuickFix> = prepareQuickFixes(project, failedProjectId)
  override val title = MavenProjectBundle.message("maven.source.5.not.supported.title")
  override val description = createDescription()

  private fun createDescription() = quickFixes.map {
    HtmlChunk.link(it.id, MavenProjectBundle.message("maven.source.5.not.supported.update", it.mavenProject.displayName))
      .toString()
  }.joinToString("\n<br/>", prefix = MavenProjectBundle.message("maven.source.5.not.supported.description"))


  override fun getNavigatable(project: Project): Navigatable? {
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId(failedProjectId))
    return mavenProject?.file?.let { OpenFileDescriptor(project, it) }
  }

  companion object {
    private fun prepareQuickFixes(project: Project, failedProjectId: String): List<UpdateSourceLevelQuickFix> {
      var mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId(failedProjectId));
      val result = ArrayList<UpdateSourceLevelQuickFix>()
      while (mavenProject != null) {
        result.add(UpdateSourceLevelQuickFix(mavenProject))
        val parentId = mavenProject.parentId
        mavenProject = parentId?.let { MavenProjectsManager.getInstance(project).findProject(parentId) }
      }
      return result
    }
  }
}

typealias MessagePredicate = (String) -> Boolean

class JpsReleaseVersion5QuickFix : BuildIssueContributor {
  override fun createBuildIssue(project: Project,
                                moduleNames: Collection<String>,
                                title: String,
                                message: String,
                                kind: MessageEvent.Kind,
                                virtualFile: VirtualFile?,
                                navigatable: Navigatable?): BuildIssue? {
    val manager = MavenProjectsManager.getInstance(project);
    if (!manager.isMavenizedProject) return null

    if (moduleNames.size != 1) {
      return null
    }
    val moduleName = moduleNames.firstOrNull() ?: return null
    val predicates = CacheForCompilerErrorMessages.getPredicatesToCheck(project, moduleName);
    if (!message.contains("release version") || !message.contains("not supported")) return null

    val module = ModuleManager.getInstance(project).findModuleByName(moduleName) ?: return null
    val failedId = MavenProjectsManager.getInstance(project).findProject(module)?.mavenId ?: return null

    if (predicates.any { it(message) }) return Source5BuildIssue(project, failedId.displayString)
    return null
  }


}

class UpdateSourceLevelQuickFix(val mavenProject: MavenProject) : BuildIssueQuickFix {
  override val id = ID + mavenProject.mavenId.displayString
  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {

    val languageLevel = MavenModuleImporter.getLanguageLevel(mavenProject)
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_6)) {
      Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "",
                   MavenProjectBundle.message("maven.quickfix.cannot.update.source.level.already.1.6", mavenProject.displayName),
                   NotificationType.INFORMATION).notify(project)
      return CompletableFuture.completedFuture(null)
    }
    val module = MavenProjectsManager.getInstance(project).findModule(mavenProject)
    if (module == null) {
      Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "",
                   MavenProjectBundle.message("maven.quickfix.cannot.update.source.level.module.not.found", mavenProject.displayName),
                   NotificationType.INFORMATION).notify(project)
      return CompletableFuture.completedFuture(null)
    }

    val moduleJdk = MavenUtil.getModuleJdk(MavenProjectsManager.getInstance(project), mavenProject)
    if (moduleJdk == null) {
      Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "",
                   MavenProjectBundle.message("maven.quickfix.cannot.update.source.level.module.not.found", mavenProject.displayName),
                   NotificationType.INFORMATION).notify(project)
      return CompletableFuture.completedFuture(null)
    }

    val promise = MavenProjectModelModifier(project).changeLanguageLevel(module, LanguageLevel.parse(moduleJdk.versionString)!!)
    if (promise == null) {
      return CompletableFuture.completedFuture(null)
    }
    OpenFileDescriptor(project, mavenProject.file).navigate(true)
    return promise.asCompletableFuture()
  }

  companion object {
    val ID = "maven_quickfix_source_level_"
  }

}


object CacheForCompilerErrorMessages {
  private val key = "compiler.err.unsupported.release.version".encodeToByteArray()
  private val delimiter = ByteArray(2)

  init {
    delimiter[0] = 1 // SOH
    delimiter[1] = 0 // NUL byte
  }

  private val DEFAULT_CHECK = listOf<MessagePredicate>(
    { it.contains("release version") && it.contains("not supported") }, //en
    {
      it.contains("\u30EA\u30EA\u30FC\u30B9\u30FB\u30D0\u30FC\u30B8\u30E7\u30F3")
      && it.contains("\u306F\u30B5\u30DD\u30FC\u30C8\u3055\u308C\u3066\u3044\u307E\u305B\u3093")
    }, //ja
    { it.contains("\u4E0D\u652F\u6301\u53D1\u884C\u7248\u672C") } //zh_CN
  )
  private val map = WeakHashMap<String, List<MessagePredicate>>()

  @JvmStatic
  fun connectToJdkListener(myProject: Project, disposable: Disposable) {
    myProject.messageBus.connect(disposable).subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
      override fun jdkAdded(jdk: Sdk) {
        synchronized(map){map.remove(jdk.name)}
      }

      override fun jdkRemoved(jdk: Sdk) {
        synchronized(map){map.remove(jdk.name)}
      }

      override fun jdkNameChanged(jdk: Sdk, previousName: String) {
        synchronized(map){
          val list = map[previousName]
          if(list!=null){
            map[jdk.name] = list
          }
        }
      }
    })
  }

  fun getPredicatesToCheck(project: Project, moduleName: String): List<MessagePredicate> {
    val module = ModuleManager.getInstance(project).findModuleByName(moduleName) ?: return DEFAULT_CHECK;
    val sdk = ModuleRootManager.getInstance(module).sdk ?: return DEFAULT_CHECK;
    return synchronized(map) { map.getOrPut(sdk.name) { readFrom(sdk) } }
  }

  private fun readFrom(sdk: Sdk): List<MessagePredicate> {
    val version = JavaSdk.getInstance().getVersion(sdk);
    if (version == null || !version.isAtLeast(JavaSdkVersion.JDK_1_9)) {
      return DEFAULT_CHECK;
    }

    try {
      val jrtLocalFile = sdk.homeDirectory?.let { JrtFileSystem.getInstance().getRootByLocal(it) } ?: return DEFAULT_CHECK
      val list =
        jrtLocalFile.findFileByRelativePath("jdk.compiler/com/sun/tools/javac/resources")
          ?.children
          ?.filter { it.name.startsWith("compiler") && it.name.endsWith(".class") }
          ?.mapNotNull { readFromBinaryFile(it) }
          ?.toList()
      if (list.isNullOrEmpty()) return DEFAULT_CHECK else return list

    }
    catch (e: Throwable) {
      MavenLog.LOG.warn(e);
      return DEFAULT_CHECK;
    }
  }


  private fun readFromBinaryFile(file: VirtualFile?): MessagePredicate? {
    if (file == null) return null
    try {
      val allBytes = VfsUtil.loadBytes(file)
      val indexKey = Bytes.indexOf(allBytes, key)
      if (indexKey == -1) return null
      val startFrom = indexKey + key.size + 3;
      val endIndex = allBytes.findNextSOH(startFrom)
      if (endIndex == -1) return null
      val message = String(allBytes, startFrom, endIndex - startFrom, StandardCharsets.UTF_8)
      return toMessagePredicate(message);
    }
    catch (e: Throwable) {
      MavenLog.LOG.warn(e);
      return null
    }

  }

  private fun toMessagePredicate(message: String): MessagePredicate? {
    val first = message.substringBefore("{0}")
    val second = message.substringAfter("{0}")
    return { it.contains(first) && it.contains(second) }
  }

  private fun ByteArray.findNextSOH(startFrom: Int): Int {
    if (startFrom == -1) return -1
    var i = startFrom
    while (i < this.size - 1) {
      if (this[i] == delimiter[0] && this[i + 1] == delimiter[1]) {
        return i;
      }
      i++
    }
    return -1
  }

}

