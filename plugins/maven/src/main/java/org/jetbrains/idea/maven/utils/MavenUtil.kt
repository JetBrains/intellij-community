// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.execution.configurations.CompositeParameterTargetedValue
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager.Companion.getInstance
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.InvalidJavaHomeException
import com.intellij.openapi.externalSystem.service.execution.InvalidSdkException
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.*
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.upgradeBlocking
import com.intellij.platform.eel.provider.utils.fetchLoginShellEnvVariablesBlocking
import com.intellij.psi.PsiManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.*
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.xml.NanoXmlBuilder
import com.intellij.util.xml.NanoXmlUtil
import icons.ExternalSystemIcons
import org.apache.commons.lang3.StringUtils
import org.jdom.Element
import org.jdom.JDOMException
import org.jdom.Namespace
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.MavenVersionAwareSupportExtension
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenConstants.MODEL_VERSION_4_0_0
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerEmbedder
import org.jetbrains.idea.maven.server.MavenServerManager.Companion.getInstance
import org.jetbrains.idea.maven.server.MavenServerUtil
import org.jetbrains.idea.maven.utils.MavenArtifactUtil.readPluginInfo
import org.jetbrains.idea.maven.utils.MavenEelUtil.resolveLocalRepositoryBlocking
import org.jetbrains.idea.maven.utils.MavenEelUtil.resolveM2Dir
import org.jetbrains.idea.maven.utils.MavenEelUtil.resolveUserSettingsPathBlocking
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.*
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.jar.Attributes
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import java.util.stream.Stream
import java.util.zip.CRC32
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import kotlin.io.path.isDirectory

object MavenUtil {
  interface MavenTaskHandler {
    fun waitFor()
  }

  private val settingsListNamespaces = mutableListOf<String?>(
    "http://maven.apache.org/SETTINGS/1.0.0",
    "http://maven.apache.org/SETTINGS/1.1.0",
    "http://maven.apache.org/SETTINGS/1.2.0"
  )

  private val extensionListNamespaces = mutableListOf<String?>(
    "http://maven.apache.org/EXTENSIONS/1.0.0",
    "http://maven.apache.org/EXTENSIONS/1.1.0",
    "http://maven.apache.org/EXTENSIONS/1.2.0"
  )
  private val runnables: MutableSet<Runnable?> = Collections.newSetFromMap<Runnable?>(IdentityHashMap<Runnable?, Boolean?>())
  const val INTELLIJ_PLUGIN_ID: String = "org.jetbrains.idea.maven"

  @ApiStatus.Experimental
  const val MAVEN_NAME: @NlsSafe String = "Maven"
  @JvmField
  val MAVEN_NAME_UPCASE: @NonNls String = MAVEN_NAME.uppercase(Locale.getDefault())
  @JvmField
  val SYSTEM_ID: ProjectSystemId = ProjectSystemId(MAVEN_NAME_UPCASE)
  const val MAVEN_NOTIFICATION_GROUP: String = MAVEN_NAME
  const val SETTINGS_XML: String = "settings.xml"
  const val DOT_M2_DIR: String = ".m2"
  const val ENV_M2_HOME: String = "M2_HOME"
  const val M2_DIR: String = "m2"
  const val BIN_DIR: String = "bin"
  const val CONF_DIR: String = "conf"
  const val M2_CONF_FILE: String = "m2.conf"
  const val M2_DAEMON_CONF_FILE: String = "mvnd-daemon.conf"
  const val MVN_FILE: String = "mvn"
  const val MVND_FILE: String = "mvnd"
  const val MVND_EXE_FILE: String = "mvnd.exe"
  const val REPOSITORY_DIR: String = "repository"
  const val LIB_DIR: String = "lib"
  const val CLIENT_ARTIFACT_SUFFIX: String = "-client"
  const val CLIENT_EXPLODED_ARTIFACT_SUFFIX: String = CLIENT_ARTIFACT_SUFFIX + " exploded"

  @Deprecated("")
  internal const val PROP_FORCED_M2_HOME: String = "idea.force.m2.home"
  const val MAVEN_REPO_LOCAL: String = "maven.repo.local"


  private val SUPER_POM_PATHS: Array<Pair<Pattern, String>> = arrayOf<Pair<Pattern, String>>(
    Pair.create<Pattern, String>(Pattern.compile("maven-\\d+\\.\\d+\\.\\d+-uber\\.jar"),
                                 "org/apache/maven/project/" + MavenConstants.SUPER_POM_4_0_XML),
    Pair.create<Pattern, String>(Pattern.compile("maven-model-builder-\\d+\\.\\d+\\.\\d+\\.jar"),
                                 "org/apache/maven/model/" + MavenConstants.SUPER_POM_4_0_XML)
  )

  @Volatile
  private var ourPropertiesFromMvnOpts: MutableMap<String, String>? = null

  fun enablePreimport(): Boolean {
    return `is`("maven.preimport.project")
  }

  fun enablePreimportOnly(): Boolean {
    return `is`("maven.preimport.only")
  }

  @JvmStatic
  val propertiesFromMavenOpts: MutableMap<String, String>
    get() {
      var res: MutableMap<String, String>? = ourPropertiesFromMvnOpts
      if (res == null) {
        res = parseMavenProperties(System.getenv("MAVEN_OPTS"))
        ourPropertiesFromMvnOpts = res
      }
      return res
    }

  @JvmStatic
  fun parseMavenProperties(mavenOpts: String?): MutableMap<String, String> {
    if (mavenOpts != null) {
      val mavenOptsList = ParametersList()
      mavenOptsList.addParametersString(mavenOpts)
      return mavenOptsList.getProperties()
    }
    return mutableMapOf<String, String>()
  }


  @JvmStatic
  fun invokeLater(p: Project, r: Runnable) {
    invokeLater(p, ModalityState.defaultModalityState(), r)
  }

  fun invokeLater(p: Project, state: ModalityState, r: Runnable) {
    startTestRunnable(r)
    ApplicationManager.getApplication().invokeLater(Runnable {
      runAndFinishTestRunnable(r)
    }, state, p.getDisposed())
  }


  private fun startTestRunnable(r: Runnable?) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) return
    synchronized(runnables) {
      runnables.add(r)
    }
  }

  private fun runAndFinishTestRunnable(r: Runnable) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      r.run()
      return
    }

    try {
      r.run()
    }
    finally {
      synchronized(runnables) {
        runnables.remove(r)
      }
    }
  }

  @TestOnly
  fun noUncompletedRunnables(): Boolean {
    synchronized(runnables) {
      return runnables.isEmpty()
    }
  }

  fun cleanAllRunnables() {
    synchronized(runnables) {
      runnables.clear()
    }
  }

  @get:TestOnly
  val uncompletedRunnables: MutableList<Runnable?>
    get() {
      val result: MutableList<Runnable?>
      synchronized(runnables) {
        result = ArrayList<Runnable?>(runnables)
      }
      return result
    }

  @JvmStatic
  fun invokeAndWait(p: Project, r: Runnable) {
    invokeAndWait(p, ModalityState.defaultModalityState(), r)
  }

  fun invokeAndWait(p: Project?, state: ModalityState, r: Runnable) {
    startTestRunnable(r)
    ApplicationManager.getApplication().invokeAndWait(DisposeAwareRunnable.create(Runnable { runAndFinishTestRunnable(r) }, p), state)
  }


  @JvmStatic
  fun invokeAndWaitWriteAction(p: Project, r: Runnable) {
    startTestRunnable(r)
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      runAndFinishTestRunnable(r)
    }
    else if (ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().runWriteAction(r)
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(DisposeAwareRunnable.create(
        Runnable { ApplicationManager.getApplication().runWriteAction(Runnable { runAndFinishTestRunnable(r) }) }, p),
                                                        ModalityState.defaultModalityState())
    }
  }

  fun runDumbAware(project: Project, r: Runnable) {
    startTestRunnable(r)
    if (isDumbAware(r)) {
      runAndFinishTestRunnable(r)
    }
    else {
      DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(Runnable { runAndFinishTestRunnable(r) }, project))
    }
  }

  @JvmStatic
  fun runWhenInitialized(project: Project, runnable: Runnable) {
    if (project.isDisposed()) {
      return
    }

    if (project.isInitialized()) {
      runDumbAware(project, runnable)
    }
    else {
      startTestRunnable(runnable)
      StartupManager.getInstance(project).runAfterOpened(Runnable { runAndFinishTestRunnable(runnable) })
    }
  }

  @JvmStatic
  val isInModalContext: Boolean
    get() = LaterInvocator.isInModalContext()

  @JvmStatic
  fun showError(project: Project?, title: @NlsContexts.NotificationTitle String, e: Throwable) {
    MavenLog.LOG.warn(title, e)
    Notifications.Bus.notify(Notification(MAVEN_NOTIFICATION_GROUP, title, e.message!!, NotificationType.ERROR), project)
  }

  @JvmStatic
  fun showError(
    project: Project?,
    title: @NlsContexts.NotificationTitle String,
    message: @NlsContexts.NotificationContent String,
  ) {
    MavenLog.LOG.warn(title)
    Notifications.Bus.notify(Notification(MAVEN_NOTIFICATION_GROUP, title, message, NotificationType.ERROR), project)
  }

  @JvmStatic
  fun getPluginSystemDir(folder: String): Path {
    return appSystemDir.resolve("Maven").resolve(folder)
  }

  @JvmStatic
  fun getBaseDir(file: VirtualFile): Path {
    val virtualBaseDir: VirtualFile = getVFileBaseDir(file)
    return virtualBaseDir.toNioPath()
  }

  fun groupByBasedir(projects: Collection<MavenProject>, tree: MavenProjectsTree): MultiMap<String, MavenProject> {
    return ContainerUtil.groupBy<String, MavenProject>(projects, NullableFunction { getBaseDir(tree.findRootProject(it).directoryFile).toString() })
  }

  @JvmStatic
  fun getVFileBaseDir(file: VirtualFile): VirtualFile {
    var baseDir = if (file.isDirectory() || file.getParent() == null) file else file.getParent()
    var dir = baseDir
    do {
      val child = dir.findChild(".mvn")

      if (child != null && child.isDirectory()) {
        if (MavenLog.LOG.isTraceEnabled()) {
          MavenLog.LOG.trace("found .mvn in " + child)
        }
        baseDir = dir
        break
      }
    }
    while ((dir.getParent().also { dir = it }) != null)
    if (MavenLog.LOG.isTraceEnabled()) {
      MavenLog.LOG.trace("return " + baseDir + " as baseDir")
    }
    return baseDir
  }

  @JvmStatic
  fun findProfilesXmlFile(pomFile: VirtualFile?): VirtualFile? {
    if (pomFile == null) return null
    val parent = pomFile.getParent()
    if (parent == null || !parent.isValid()) return null
    return parent.findChild(MavenConstants.PROFILES_XML)
  }

  fun getProfilesXmlNioFile(pomFile: VirtualFile?): Path? {
    if (pomFile == null) return null
    val parent = pomFile.getParent()
    if (parent == null) return null
    return parent.toNioPath().resolve(MavenConstants.PROFILES_XML)
  }

  @JvmStatic
  fun <T, U> collectFirsts(pairs: List<Pair<T, U>>): List<T> {
    val result = ArrayList<T>(pairs.size)
    for (each in pairs) {
      result.add(each.first)
    }
    return result
  }

  fun <T, U> collectSeconds(pairs: MutableList<out Pair<T?, U?>>): MutableList<U?> {
    val result: MutableList<U?> = ArrayList<U?>(pairs.size)
    for (each in pairs) {
      result.add(each.second)
    }
    return result
  }

  @JvmStatic
  fun collectPaths(files: List<VirtualFile>): List<String> {
    return files.map { file -> file.getPath() }
  }

  @JvmStatic
  fun collectFiles(projects: Collection<MavenProject>): List<VirtualFile> {
    return projects.map { project -> project.file }
  }

  @JvmStatic
  fun <T> equalAsSets(collection1: MutableCollection<T?>, collection2: MutableCollection<T?>): Boolean {
    return toSet<T?>(collection1) == toSet<T?>(collection2)
  }

  private fun <T> toSet(collection: MutableCollection<T?>): MutableCollection<T?> {
    return (if (collection is MutableSet<*>) collection else HashSet<T?>(collection))
  }

  fun <T, U> mapToList(map: MutableMap<T?, U?>): MutableList<Pair<T?, U?>?> {
    return ContainerUtil.map<MutableMap.MutableEntry<T?, U?>?, Pair<T?, U?>?>(map.entries,
                                                                              Function { tuEntry: MutableMap.MutableEntry<T?, U?>? ->
                                                                                Pair.create<T?, U?>(
                                                                                  tuEntry!!.key, tuEntry.value)
                                                                              })
  }

  @JvmStatic
  fun formatHtmlImage(url: URL?): String {
    return "<img src=\"" + url + "\"> "
  }

  @Throws(IOException::class)
  @JvmStatic
  fun runOrApplyMavenProjectFileTemplate(
    project: Project,
    file: VirtualFile,
    projectId: MavenId,
    interactive: Boolean,
  ) {
    runOrApplyMavenProjectFileTemplate(project, file, projectId, null, null, interactive)
  }

  @JvmStatic
  @Throws(IOException::class)
  fun runOrApplyMavenProjectFileTemplate(
    project: Project,
    file: VirtualFile,
    projectId: MavenId,
    parentId: MavenId?,
    parentFile: VirtualFile?,
    interactive: Boolean,
  ) {
    runOrApplyMavenProjectFileTemplate(project, file, projectId, parentId, parentFile, Properties(), Properties(),
                                       MavenFileTemplateGroupFactory.MAVEN_PROJECT_XML_TEMPLATE, interactive)
  }

  @Throws(IOException::class)
  fun runOrApplyMavenProjectFileTemplate(
    project: Project,
    file: VirtualFile,
    projectId: MavenId,
    parentId: MavenId?,
    parentFile: VirtualFile?,
    properties: Properties,
    conditions: Properties,
    template: @NonNls String,
    interactive: Boolean,
  ) {
    properties.setProperty("GROUP_ID", projectId.getGroupId())
    properties.setProperty("ARTIFACT_ID", projectId.getArtifactId())
    properties.setProperty("VERSION", projectId.getVersion())
    properties.setProperty("MODEL_VERSION", MODEL_VERSION_4_0_0)

    if (parentId != null) {
      conditions.setProperty("HAS_PARENT", "true")
      properties.setProperty("PARENT_GROUP_ID", parentId.getGroupId())
      properties.setProperty("PARENT_ARTIFACT_ID", parentId.getArtifactId())
      properties.setProperty("PARENT_VERSION", parentId.getVersion())

      if (parentFile != null) {
        val modulePath = file.getParent()
        val parentModulePath = parentFile.getParent()

        if (!Comparing.equal<VirtualFile?>(modulePath.getParent(), parentModulePath) ||
            !FileUtil.namesEqual(MavenConstants.POM_XML, parentFile.getName())
        ) {
          val relativePath = VfsUtilCore.findRelativePath(file, parentModulePath, '/')
          if (relativePath != null) {
            conditions.setProperty("HAS_RELATIVE_PATH", "true")
            properties.setProperty("PARENT_RELATIVE_PATH", relativePath)
          }
        }
      }
    }
    else {
      //set language level only for root pom
      val sdk = ProjectRootManager.getInstance(project).getProjectSdk()
      if (sdk != null && sdk.getSdkType() is JavaSdk) {
        val javaSdk = sdk.getSdkType() as JavaSdk
        val version: JavaSdkVersion? = javaSdk.getVersion(sdk)
        val description = if (version == null) null else version.description
        val shouldSetLangLevel = version != null && version.isAtLeast(JavaSdkVersion.JDK_1_6)
        conditions.setProperty("SHOULD_SET_LANG_LEVEL", shouldSetLangLevel.toString())
        properties.setProperty("COMPILER_LEVEL_SOURCE", description)
        properties.setProperty("COMPILER_LEVEL_TARGET", description)
      }
    }
    runOrApplyFileTemplate(project, file, template, properties, conditions, interactive)
  }

  @JvmStatic
  @Throws(IOException::class)
  fun runFileTemplate(
    project: Project,
    file: VirtualFile,
    templateName: String,
  ) {
    runOrApplyFileTemplate(project, file, templateName, Properties(), Properties(), true)
  }

  @Throws(IOException::class)
  fun runOrApplyFileTemplate(
    project: Project,
    file: VirtualFile,
    templateName: String,
    properties: Properties,
    conditions: Properties?,
    interactive: Boolean,
  ) {
    val manager = FileTemplateManager.getInstance(project)
    val fileTemplate = manager.getJ2eeTemplate(templateName)
    val allProperties = manager.getDefaultProperties()
    if (!interactive) {
      allProperties.putAll(properties)
    }
    allProperties.putAll(conditions!!)
    var text = fileTemplate.getText(allProperties)
    val pattern = Pattern.compile("\\$\\{(.*?)}")
    val matcher = pattern.matcher(text)
    val builder = StringBuilder()
    while (matcher.find()) {
      matcher.appendReplacement(builder, "\\$" + StringUtil.toUpperCase(matcher.group(1)) + "\\$")
    }
    matcher.appendTail(builder)
    text = builder.toString()

    val template = TemplateManager.getInstance(project).createTemplate("", "", text) as TemplateImpl
    for (i in 0..<template.getSegmentsCount()) {
      if (i == template.getEndSegmentNumber()) continue
      val name = template.getSegmentName(i)
      val value = "\"" + properties.getProperty(name, "") + "\""
      template.addVariable(name, value, value, true)
    }

    if (interactive) {
      val descriptor = OpenFileDescriptor(project, file)
      val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
      editor!!.getDocument().setText("")
      TemplateManager.getInstance(project).startTemplate(editor, template)
    }
    else {
      VfsUtil.saveText(file, template.getTemplateText())

      val psiFile = PsiManager.getInstance(project).findFile(file)
      if (psiFile != null) {
        if (project.isInitialized()) {
          ReformatCodeProcessor(project, psiFile, null, false).run()
        }
      }
    }
  }

  fun collectPattern(text: String, result: MutableList<Pattern>): List<Pattern> {
    val antPattern = FileUtil.convertAntToRegexp(text.trim { it <= ' ' })
    try {
      result.add(Pattern.compile(antPattern))
    }
    catch (ignore: PatternSyntaxException) {
    }
    return result
  }

  fun isIncluded(relativeName: String, includes: MutableList<Pattern>, excludes: MutableList<Pattern>): Boolean {
    var result = false
    for (each in includes) {
      if (each.matcher(relativeName).matches()) {
        result = true
        break
      }
    }
    if (!result) return false
    for (each in excludes) {
      if (each.matcher(relativeName).matches()) return false
    }
    return true
  }

  @Throws(MavenProcessCanceledException::class)
  fun run(title: @NlsContexts.DialogTitle String, task: MavenTask) {
    val canceledEx = arrayOfNulls<Exception>(1)
    val runtimeEx = arrayOfNulls<RuntimeException>(1)
    val errorEx = arrayOfNulls<Error>(1)

    ProgressManager.getInstance().run(object : Task.Modal(null, title, true) {
      override fun run(i: ProgressIndicator) {
        try {
          task.run(MavenProgressIndicator(null, i, null))
        }
        catch (e: MavenProcessCanceledException) {
          canceledEx[0] = e
        }
        catch (e: ProcessCanceledException) {
          canceledEx[0] = e
        }
        catch (e: RuntimeException) {
          runtimeEx[0] = e
        }
        catch (e: Error) {
          errorEx[0] = e
        }
      }
    })
    if (canceledEx[0] is MavenProcessCanceledException) throw canceledEx[0] as MavenProcessCanceledException
    if (canceledEx[0] is ProcessCanceledException) throw MavenProcessCanceledException()

    val runtimeException = runtimeEx[0]
    if (runtimeException != null) throw runtimeException
    val error = errorEx[0]
    if (error != null) throw error
  }

  // used in third-party plugins
  @JvmStatic
  fun runInBackground(
    project: Project,
    title: @NlsContexts.Command String,
    cancellable: Boolean,
    task: MavenTask,
  ): MavenTaskHandler {
    val manager = MavenProjectsManager.getInstanceIfCreated(project)
    val syncConsoleSupplier: Supplier<MavenSyncConsole?>? = if (manager == null) null else Supplier { manager.getSyncConsole() }
    val indicator = MavenProgressIndicator(project, syncConsoleSupplier)

    val runnable = Runnable {
      if (project.isDisposed()) return@Runnable
      try {
        task.run(indicator)
      }
      catch (e: MavenProcessCanceledException) {
        indicator.cancel()
      }
      catch (e: ProcessCanceledException) {
        indicator.cancel()
      }
    }

    val future: Future<*>?
    future = ApplicationManager.getApplication().executeOnPooledThread(runnable)
    val handler: MavenTaskHandler = object : MavenTaskHandler {
      override fun waitFor() {
        try {
          future.get()
        }
        catch (e: InterruptedException) {
          MavenLog.LOG.error(e)
        }
        catch (e: ExecutionException) {
          MavenLog.LOG.error(e)
        }
      }
    }
    invokeLater(project, Runnable {
      if (future.isDone()) return@Runnable
      object : Task.Backgroundable(project, title, cancellable) {
        override fun run(i: ProgressIndicator) {
          indicator.setIndicator(i)
          handler.waitFor()
        }
      }.queue()
    })
    return handler
  }

  @Deprecated(
    """do not use this method, it mixes path to maven home and labels like "Use bundled maven"
  use {@link MavenUtil#getMavenHomePath(StaticResolvedMavenHomeType) getMavenHomePath(StaticResolvedMavenHomeType} instead""")
  @JvmStatic
  fun resolveMavenHomeDirectory(overrideMavenHome: String?): File? {
    if (!isEmptyOrSpaces(overrideMavenHome)) {
      return getMavenHomePath(resolveMavenHomeType(overrideMavenHome).staticOrBundled())!!.toFile()
    }

    val m2home = System.getenv(ENV_M2_HOME)
    if (!isEmptyOrSpaces(m2home)) {
      val homeFromEnv = File(m2home)
      if (isValidMavenHome(homeFromEnv.toPath())) {
        return homeFromEnv
      }
    }

    val mavenHome = System.getenv("MAVEN_HOME")
    if (!isEmptyOrSpaces(mavenHome)) {
      val mavenHomeFile = File(mavenHome)
      if (isValidMavenHome(mavenHomeFile.toPath())) {
        return mavenHomeFile
      }
    }

    val userHome = SystemProperties.getUserHome()
    if (!isEmptyOrSpaces(userHome)) {
      val underUserHome = File(userHome, M2_DIR)
      if (isValidMavenHome(underUserHome.toPath())) {
        return underUserHome
      }
    }

    if (SystemInfo.isMac) {
      var home: Path? = fromBrew(null)
      if (home != null) {
        return home.toFile()
      }

      if ((fromMacSystemJavaTools(null).also { home = it }) != null) {
        return home!!.toFile()
      }
    }
    else if (SystemInfo.isLinux) {
      var home = File("/usr/share/maven")
      if (isValidMavenHome(home.toPath())) {
        return home
      }

      home = File("/usr/share/maven2")
      if (isValidMavenHome(home.toPath())) {
        return home
      }
    }

    return MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toFile()
  }

  @JvmStatic
  @RequiresBackgroundThread
  fun getSystemMavenHomeVariants(project: Project): MutableList<MavenHomeType> {
    val result = ArrayList<MavenHomeType>()

    val eel = project.getEelDescriptor().upgradeBlocking()
    val envs = eel.exec.fetchLoginShellEnvVariablesBlocking()

    val m2home = envs.get(ENV_M2_HOME)
    if (!isEmptyOrSpaces(m2home)) {
      val homeFromEnv = eel.fs.getPath(m2home!!).asNioPath()
      if (isValidMavenHome(homeFromEnv)) {
        result.add(MavenInSpecificPath(m2home))
      }
    }

    val mavenHome = envs.get("MAVEN_HOME")
    if (!isEmptyOrSpaces(mavenHome)) {
      val mavenHomeFile = eel.fs.getPath(mavenHome!!).asNioPath()
      if (isValidMavenHome(mavenHomeFile)) {
        result.add(MavenInSpecificPath(mavenHome))
      }
    }

    val userHome = eel.fs.user.home
    if (!isEmptyOrSpaces(userHome.toString())) {
      val nioUserHome = userHome.asNioPath()
      val underUserHome: Path = nioUserHome.resolve(M2_DIR)
      if (isValidMavenHome(underUserHome)) {
        result.add(MavenInSpecificPath(userHome.toString()))
      }
    }

    // TODO: eel
    if (eel is LocalEelApi && SystemInfo.isMac) {
      var home: Path? = fromBrew(eel)
      if (home != null) {
        result.add(MavenInSpecificPath(home.toAbsolutePath().toString()))
      }

      if ((fromMacSystemJavaTools(eel).also { home = it }) != null) {
        result.add(MavenInSpecificPath(home!!.toAbsolutePath().toString()))
      }
    }
    else if (eel.platform is EelPlatform.Linux) {
      var home = eel.fs.getPath("/usr/share/maven").asNioPath()
      if (isValidMavenHome(home)) {
        result.add(MavenInSpecificPath(home.toAbsolutePath().toString()))
      }

      home = eel.fs.getPath("/usr/share/maven2").asNioPath()
      if (isValidMavenHome(home)) {
        result.add(MavenInSpecificPath(home.toAbsolutePath().toString()))
      }
    }

    result.add(BundledMaven3)
    return result
  }

  @JvmStatic
  fun addEventListener(mavenVersion: String, params: SimpleJavaParameters) {
    if (VersionComparatorUtil.compare(mavenVersion, "3.0.2") < 0) {
      MavenLog.LOG.warn("Maven version less than 3.0.2 are not correctly displayed in Build Window")
      return
    }
    val listenerPath = getInstance().getMavenEventListener().getAbsolutePath()
    val userExtClassPath =
      StringUtils.stripToEmpty(params.getVMParametersList().getPropertyValue(MavenServerEmbedder.MAVEN_EXT_CLASS_PATH))
    val vmParameter = "-D" + MavenServerEmbedder.MAVEN_EXT_CLASS_PATH + "="
    val userListeners = userExtClassPath.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    var targetedValue = CompositeParameterTargetedValue(vmParameter)
      .addPathPart(listenerPath)

    for (path in userListeners) {
      if (StringUtil.isEmptyOrSpaces(path)) continue
      targetedValue = targetedValue.addPathSeparator().addPathPart(path)
    }
    params.getVMParametersList().add(targetedValue)
  }

  private fun fromMacSystemJavaTools(eelApi: EelApi?): Path? {
    val symlinkDir: Path
    if (eelApi == null) {
      symlinkDir = Path.of("/usr/share/maven")
    }
    else {
      symlinkDir = eelApi.fs.getPath("/usr/share/maven").asNioPath()
    }

    if (isValidMavenHome(symlinkDir)) {
      return symlinkDir
    }

    // well, try to search
    val dir: Path

    if (eelApi == null) {
      dir = Path.of("/usr/share/java")
    }
    else {
      dir = eelApi.fs.getPath("/usr/share/java").asNioPath()
    }

    val list: MutableList<Path> = ArrayList<Path>()

    try {
      Files.newDirectoryStream(dir).use { stream ->
        for (path in stream) {
          list.add(path)
        }
      }
    }
    catch (e: IOException) {
      return null
    }

    if (list.isEmpty()) {
      return null
    }

    var home: Path? = null
    val prefix = "maven-"
    val versionIndex = prefix.length
    for (path in list) {
      if (path.startsWith(prefix) &&
          (home == null || StringUtil.compareVersionNumbers(path.toString().substring(versionIndex),
                                                            home.toString().substring(versionIndex)) > 0)
      ) {
        home = path
      }
    }

    if (home != null) {
      val file = dir.resolve(home)
      if (isValidMavenHome(file)) {
        return file
      }
    }

    return null
  }

  private fun fromBrew(eelApi: EelApi?): Path? {
    val brewDir = eelApi?.fs?.getPath("/usr/local/Cellar/maven")?.asNioPath()

    val list: MutableList<Path?> = ArrayList<Path?>()

    try {
      Files.newDirectoryStream(brewDir).use { stream ->
        for (path in stream) {
          list.add(path)
        }
      }
    }
    catch (e: IOException) {
      return null
    }

    if (list.isEmpty()) {
      return null
    }


    if (list.size > 1) {
      list.sortWith(Comparator.comparing<Path?, String?>(java.util.function.Function { obj: Path? -> obj.toString() }))
    }

    val file = brewDir?.resolve(list.get(0).toString() + "/libexec")
    return if (isValidMavenHome(file)) file else null
  }

  fun isEmptyOrSpaces(str: String?): Boolean {
    return str == null || str.isBlank()
  }

  @JvmStatic
  fun isValidMavenDaemon(daemonHome: Path?): Boolean {
    if (daemonHome == null) return false
    return filesInBin(daemonHome).let {
      (it.contains(MVND_FILE) || it.contains(MVND_EXE_FILE)) &&
      (it.contains(M2_DAEMON_CONF_FILE))
    }
  }

  @JvmStatic
  fun extractMvnFromDaemon(daemonHome: Path?): Path? {
    if (daemonHome == null) return null
    val mvnDir = daemonHome.resolve("mvn")
    if (mvnDir.isDirectory() && isValidMavenHome(mvnDir)) return mvnDir
    //macos brew
    val libexecMvnDir = daemonHome.resolve("libexec").resolve("mvn")
    if (libexecMvnDir.isDirectory() && isValidMavenHome(libexecMvnDir)) return libexecMvnDir
    return null
  }



  @JvmStatic
  fun isValidMavenHome(home: Path?): Boolean {
    if (home == null) return false
    return filesInBin(home).let {
      it.contains(M2_CONF_FILE) && it.contains(MVN_FILE)
    }
  }

  private fun filesInBin(home: Path): Set<String> {
    try {
      val binDir: Path = home.resolve(BIN_DIR)
      if (!Files.isDirectory(binDir)) return emptySet()

      Files.newDirectoryStream(binDir).use { stream ->
        val set = HashSet<String>()
        for (entry in stream) {
          set.add(entry.fileName.toString())
        }
        return set
      }
    }
    catch (ignored: Exception) {
    }
    return emptySet()
  }

  @Deprecated("")
  @JvmStatic
  fun getMavenConfFile(mavenHome: File?): File {
    return File(File(mavenHome, BIN_DIR), M2_CONF_FILE)
  }

  @JvmStatic
  fun getMavenConfFilePath(mavenHome: Path): Path {
    return mavenHome.resolve(BIN_DIR).resolve(M2_CONF_FILE)
  }

  @Deprecated("")
  @JvmStatic
  fun getMavenHomeFile(mavenHome: StaticResolvedMavenHomeType): File? {
    return Optional.ofNullable<Path?>(
      getMavenHomePath(mavenHome)).map<File?>(java.util.function.Function { obj: Path? -> obj!!.toFile() }).orElse(null)
  }

  @JvmStatic
  fun getMavenHomePath(mavenHome: StaticResolvedMavenHomeType): Path? {
    if (mavenHome is MavenInSpecificPath) {
      val file = Path.of(mavenHome.mavenHome)
      if (isValidMavenHome(file)) return file
      if (isValidMavenDaemon(file)) return extractMvnFromDaemon(file)
      return null
    }
    for (e in MavenVersionAwareSupportExtension.MAVEN_VERSION_SUPPORT.extensionList) {
      val file = e.getMavenHomeFile(mavenHome)
      if (file != null) return file
    }
    return null
  }


  @JvmStatic
  fun getMavenVersion(mavenHome: Path?): String? {
    if (mavenHome == null) return null
    val libDir = mavenHome.resolve("lib")
    if (!Files.isDirectory(libDir)) {
      MavenLog.LOG.warn("Cannot find lib directory in " + mavenHome)
      return null
    }

    try {
      Files.newDirectoryStream(libDir).use { stream ->
        for (mavenLibPath in stream) {
          val lib = mavenLibPath.getFileName().toString()
          if (lib == "maven-core.jar") {
            MavenLog.LOG.trace("Choosing version by maven-core.jar")
            return getMavenLibVersion(mavenLibPath)
          }
          if (lib.startsWith("maven-core-") && lib.endsWith(".jar")) {
            MavenLog.LOG.trace("Choosing version by maven-core.xxx.jar")
            val version = lib.substring("maven-core-".length, lib.length - ".jar".length)
            return if (version.contains(".x")) getMavenLibVersion(mavenLibPath) else version
          }
          if (lib.startsWith("maven-") && lib.endsWith("-uber.jar")) {
            MavenLog.LOG.trace("Choosing version by maven-xxx-uber.jar")
            return lib.substring("maven-".length, lib.length - "-uber.jar".length)
          }
        }
      }
    }
    catch (e: IOException) {
      MavenLog.LOG.warn("Cannot read lib directory in " + mavenHome, e)
      return null
    }

    MavenLog.LOG.warn("Cannot resolve maven version for " + mavenHome)
    return null
  }

  private fun getMavenLibVersion(file: Path): String? {
    val props = JarUtils.loadProperties(file, "META-INF/maven/org.apache.maven/maven-core/pom.properties")
    return if (props != null)
      StringUtil.nullize(props.getProperty("version"))
    else
      StringUtil.nullize(JarUtils.getJarAttribute(file, null, Attributes.Name.IMPLEMENTATION_VERSION))
  }

  @JvmStatic
  fun getMavenVersion(mavenHome: String): String? {
    return getMavenVersion(Path.of(mavenHome))
  }


  @JvmStatic
  fun getMavenVersion(mavenHomeType: StaticResolvedMavenHomeType): String? {
    return getMavenVersion(getMavenHomePath(mavenHomeType))
  }

  @JvmStatic
  fun resolveGlobalSettingsFile(mavenHomeType: StaticResolvedMavenHomeType): Path? {
    val directory: Path? = getMavenHomePath(mavenHomeType)
    if (directory == null) return null
    return directory.resolve(CONF_DIR).resolve(SETTINGS_XML)
  }

  fun resolveGlobalSettingsFile(mavenHome: Path): Path {
    return mavenHome.resolve(CONF_DIR).resolve(SETTINGS_XML)
  }

  @Deprecated("")
  @JvmStatic
  fun resolveUserSettingsFile(overriddenUserSettingsFile: String?): File {
    return resolveUserSettingsPath(overriddenUserSettingsFile, null).toFile()
  }

  @JvmStatic
  fun resolveUserSettingsPath(overriddenUserSettingsFile: String?, project: Project?): Path {
    return resolveUserSettingsPathBlocking(overriddenUserSettingsFile, project)
  }

  fun resolveM2Dir(project: Project?): Path {
    val eel = if (project != null) project.getEelDescriptor().upgradeBlocking() else null
    return eel.resolveM2Dir()
  }

  @Deprecated(
    """do not use this method, it mixes path to maven home and labels like "Use bundled maven" in overriddenMavenHome variable
  use {@link MavenUtil#resolveLocalRepository(String, StaticResolvedMavenHomeType, String) resolveLocalRepository(String, StaticResolvedMavenHomeType, String)}
  or {@link MavenUtil#resolveDefaultLocalRepository() resolveDefaultLocalRepository()} instead""")
  @JvmStatic
  fun resolveLocalRepository(
    overriddenLocalRepository: String?,
    overriddenMavenHome: String?,
    overriddenUserSettingsFile: String?,
  ): File {
    return resolveLocalRepository(null, overriddenLocalRepository, overriddenMavenHome, overriddenUserSettingsFile).toFile()
  }

  @Deprecated(
    """do not use this method, it mixes path to maven home and labels like "Use bundled maven" in overriddenMavenHome variable
  use {@link MavenUtil#resolveLocalRepository(String, StaticResolvedMavenHomeType, String) resolveLocalRepository(String, StaticResolvedMavenHomeType, String)}
  or {@link MavenUtil#resolveDefaultLocalRepository() resolveDefaultLocalRepository()} instead""")
  fun resolveLocalRepository(
    project: Project?,
    overriddenLocalRepository: String?,
    overriddenMavenHome: String?,
    overriddenUserSettingsFile: String?,
  ): Path {
    val type = resolveMavenHomeType(overriddenMavenHome)
    if (type is StaticResolvedMavenHomeType) {
      return resolveLocalRepository(project, overriddenLocalRepository, type, overriddenUserSettingsFile)
    }
    throw IllegalArgumentException("Cannot resolve local repository for wrapped maven, this API is deprecated")
  }

  /**
   * @param path any path pointing to an environment where the repository should be searched.
   */
  @JvmStatic
  fun resolveDefaultLocalRepository(path: Path?): Path {
    val mavenRepoLocal = System.getProperty(MAVEN_REPO_LOCAL)

    if (mavenRepoLocal != null) {
      MavenLog.LOG.info("using " + MAVEN_REPO_LOCAL + "=" + mavenRepoLocal + " as maven home")
      return Path.of(mavenRepoLocal)
    }

    val forcedM2Home = System.getProperty(PROP_FORCED_M2_HOME)
    if (forcedM2Home != null) {
      MavenLog.LOG.error(PROP_FORCED_M2_HOME + " is deprecated, use maven.repo.local property instead")
      return Path.of(forcedM2Home)
    }

    val api = if (path == null|| path.getEelDescriptor() is LocalEelDescriptor) localEel else path.getEelApiBlocking()
    val result: Path = api.resolveM2Dir().resolve(REPOSITORY_DIR)

    try {
      return result.toRealPath()
    }
    catch (e: IOException) {
      return result
    }
  }

  @JvmStatic
  fun resolveLocalRepository(
    project: Project?,
    overriddenLocalRepository: String?,
    mavenHomeType: StaticResolvedMavenHomeType,
    overriddenUserSettingsFile: String?,
  ): Path {
    return resolveLocalRepositoryBlocking(project, overriddenLocalRepository, mavenHomeType, overriddenUserSettingsFile)
  }

  @JvmStatic
  fun getRepositoryFile(
    project: Project,
    id: MavenId,
    extension: String,
    classifier: String?,
  ): Path? {
    if (id.getGroupId() == null || id.getArtifactId() == null || id.getVersion() == null) {
      return null
    }
    val projectsManager = MavenProjectsManager.getInstance(project)
    return makeLocalRepositoryFile(id, projectsManager.getRepositoryPath(), extension, classifier)
  }

  fun makeLocalRepositoryFile(
    id: MavenId,
    localRepository: Path,
    extension: String,
    classifier: String?,
  ): Path {
    var relPath = id.getGroupId()!!.replace(".", "/")

    relPath += "/" + id.getArtifactId()
    relPath += "/" + id.getVersion()
    relPath += "/" + id.getArtifactId() + "-" + id.getVersion()
    relPath = if (classifier == null) relPath + "." + extension else relPath + "-" + classifier + "." + extension

    return localRepository.resolve(relPath)
  }

  @JvmStatic
  fun getArtifactPath(
    localRepository: Path,
    id: MavenId,
    extension: String,
    classifier: String?,
  ): Path? {
    var localRepository = localRepository
    if (id.getGroupId() == null || id.getArtifactId() == null || id.getVersion() == null) {
      return null
    }
    val artifactPath = id.getGroupId()!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    try {
      for (path in artifactPath) {
        localRepository = localRepository.resolve(path)
      }
      return localRepository
        .resolve(id.getArtifactId())
        .resolve(id.getVersion())
        .resolve(
          id.getArtifactId() + "-" + id.getVersion() + (if (classifier == null) "." + extension else "-" + classifier + "." + extension))
    }
    catch (e: InvalidPathException) {
      return null
    }
  }

  fun getRepositoryParentFile(project: Project, id: MavenId): Path? {
    if (id.getGroupId() == null || id.getArtifactId() == null || id.getVersion() == null) {
      return null
    }
    val projectsManager = MavenProjectsManager.getInstance(project)
    return getParentFile(id, projectsManager.getRepositoryPath())
  }

  private fun getParentFile(id: MavenId, localRepository: Path): Path {
    checkNotNull(id.getGroupId())
    val pathParts = id.getGroupId()!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    var path = Paths.get(localRepository.toAbsolutePath().toString(), *pathParts)
    path = Paths.get(path.toString(), id.getArtifactId(), id.getVersion())
    return path
  }

  internal fun doResolveLocalRepository(userSettingsFile: Path?, globalSettingsFile: Path?): Path? {
    if (userSettingsFile != null) {
      val fromUserSettings: String? = getRepositoryFromSettings(userSettingsFile)
      if (!StringUtil.isEmpty(fromUserSettings)) {
        return Path.of(fromUserSettings)
      }
    }

    if (globalSettingsFile != null) {
      val fromGlobalSettings: String? = getRepositoryFromSettings(globalSettingsFile)
      if (!StringUtil.isEmpty(fromGlobalSettings)) {
        return Path.of(fromGlobalSettings)
      }
    }

    return null
  }

  @JvmStatic
  fun getRepositoryFromSettings(file: Path): String? {
    try {
      val repository: Element? = getRepositoryElement(file)

      if (repository == null) {
        return null
      }
      val text = repository.getText()
      if (isEmptyOrSpaces(text)) {
        return null
      }
      return expandProperties(text!!.trim { it <= ' ' })
    }
    catch (e: Exception) {
      return null
    }
  }

  fun getMirroredUrl(settingsFile: Path?, url: String, id: String?): String {
    try {
      val mirrorParent: Element? = getElementWithRegardToNamespace(getDomRootElement(settingsFile), "mirrors", settingsListNamespaces)
      if (mirrorParent == null) {
        return url
      }
      val mirrors: MutableList<Element> = getElementsWithRegardToNamespace(mirrorParent, "mirror", settingsListNamespaces)
      for (el in mirrors) {
        val mirrorOfElement: Element? = getElementWithRegardToNamespace(el, "mirrorOf", settingsListNamespaces)
        val mirrorUrlElement: Element? = getElementWithRegardToNamespace(el, "url", settingsListNamespaces)
        if (mirrorOfElement == null) continue
        if (mirrorUrlElement == null) continue

        val mirrorOf = mirrorOfElement.getTextTrim()
        val mirrorUrl = mirrorUrlElement.getTextTrim()

        if (StringUtil.isEmptyOrSpaces(mirrorOf) || StringUtil.isEmptyOrSpaces(mirrorUrl)) {
          continue
        }

        if (isMirrorApplicable(mirrorOf!!, url, id)) {
          return mirrorUrl
        }
      }
    }
    catch (ignore: Exception) {
    }

    return url
  }

  private fun isMirrorApplicable(mirrorOf: String, url: String, id: String?): Boolean {
    val patterns = HashSet<String?>(StringUtil.split(mirrorOf, ","))

    if (patterns.contains("!" + id)) {
      return false
    }

    if (patterns.contains("*")) {
      return true
    }
    if (patterns.contains(id)) {
      return true
    }
    if (patterns.contains("external:*")) {
      try {
        val uri = URI.create(url)
        if ("file" == uri.getScheme()) return false
        if ("localhost" == uri.getHost()) return false
        if ("127.0.0.1" == uri.getHost()) return false
        return true
      }
      catch (e: IllegalArgumentException) {
        MavenLog.LOG.warn("cannot parse uri " + url, e)
        return false
      }
    }
    return false
  }

  @Throws(JDOMException::class, IOException::class)
  private fun getRepositoryElement(file: Path): Element? {
    return getElementWithRegardToNamespace(getDomRootElement(file), "localRepository", settingsListNamespaces)
  }

  @Throws(IOException::class, JDOMException::class)
  private fun getDomRootElement(file: Path?): Element? {
    if (file == null) return null
    val reader = InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)
    return JDOMUtil.load(reader)
  }

  private fun getElementWithRegardToNamespace(parent: Element?, childName: String?, namespaces: MutableList<String?>): Element? {
    if (null == parent) return null
    var element = parent.getChild(childName)
    if (element != null) return element
    for (namespace in namespaces) {
      element = parent.getChild(childName, Namespace.getNamespace(namespace))
      if (element != null) return element
    }
    return null
  }

  private fun getElementsWithRegardToNamespace(
    parent: Element,
    childrenName: String?,
    namespaces: MutableList<String?>,
  ): MutableList<Element> {
    var elements = parent.getChildren(childrenName)
    if (!elements.isEmpty()) return elements
    for (namespace in namespaces) {
      elements = parent.getChildren(childrenName, Namespace.getNamespace(namespace))
      if (!elements.isEmpty()) return elements
    }
    return mutableListOf<Element>()
  }

  fun expandProperties(text: String?, props: Properties): String? {
    var text = text
    if (text.isNullOrBlank()) return text
    for (each in props.entries) {
      val `val` = each.value
      text = text?.replace("\${" + each.key + "}", `val` as? String ?: `val`.toString())
    }
    return text
  }

  fun expandProperties(text: String?): String? {
    return expandProperties(text, MavenServerUtil.collectSystemProperties())
  }

  /**
   * Retrieves the effective SuperPOM as a virtual file.
   *
   * @param mavenDistribution A valid Maven distribution.
   * @param superPomName      The name of the POM file. MavenConstants#SUPER_POM_4_0_XML for Maven 3 and either MavenConstants#SUPER_POM_4_0_XML or MavenConstants#SUPER_POM_4_1_XML for Maven 4.
   * @return A [VirtualFile] representing the SuperPOM located inside the jar if found, False otherwise.
   */
  fun resolveSuperPomFile(mavenHome: Path, superPomName: String?): VirtualFile? {
    return doResolveSuperPomFile(mavenHome.resolve(LIB_DIR), superPomName)
  }

  fun resolveSuperPomFile(project: Project, projectFile: VirtualFile): VirtualFile? {
    val distribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(projectFile.getParent().getPath())
    val superPomName: String = resolveMavenSchema(projectFile)
    return resolveSuperPomFile(distribution.mavenHome, superPomName)
  }

  private fun resolveMavenSchema(file: VirtualFile?): String {
    return MavenConstants.SUPER_POM_4_0_XML //todo
  }

  private fun doResolveSuperPomFile(libDir: Path, superPomName: String?): VirtualFile? {
    val libraries: MutableList<Path>?

    try {
      Files.list(libDir).use { pathStream ->
        libraries = pathStream.toList()
      }
    }
    catch (e: IOException) {
      return null
    }

    for (library in libraries!!) {
      if ((library.getFileName().toString().startsWith("maven-model-builder-") && library.getFileName().toString().endsWith(".jar"))) {
        val result: VirtualFile? = tryReadFromLib(library, "org/apache/maven/model/" + superPomName)
        if (result != null) {
          return result
        }
      }
      else if ((library.getFileName().toString().startsWith("maven-") &&
                library.getFileName().getFileName().toString().endsWith("-uber.jar"))
      ) {
        //old maven versions
        val result: VirtualFile? = tryReadFromLib(library, "org/apache/maven/project/" + superPomName)
        if (result != null) {
          return result
        }
      }
    }
    return null
  }

  private fun tryReadFromLib(library: Path, pathInJar: String): VirtualFile? {
    val libraryVirtualFile = LocalFileSystem.getInstance().findFileByNioFile(library)
    if (libraryVirtualFile == null) return null
    val root = JarFileSystem.getInstance().getJarRootForLocalFile(libraryVirtualFile)
    if (root == null) return null
    return root.findFileByRelativePath(pathInJar)
  }

  @JvmStatic
  fun getPhaseVariants(manager: MavenProjectsManager): MutableList<LookupElement?> {
    val goals: MutableSet<String> = HashSet<String>(MavenConstants.PHASES)

    for (mavenProject in manager.getProjects()) {
      for (mavenProjectPluginInfo in mavenProject.pluginInfos) {
        val pluginInfo = readPluginInfo(mavenProjectPluginInfo.artifact)
        if (pluginInfo != null) {
          for (mojo in pluginInfo.getMojos()) {
            goals.add(mojo.getDisplayName())
          }
        }
      }
    }

    val res: MutableList<LookupElement?> = ArrayList<LookupElement?>(goals.size)
    for (goal in goals) {
      res.add(LookupElementBuilder.create(goal).withIcon(ExternalSystemIcons.Task))
    }

    return res
  }

  fun isProjectTrustedEnoughToImport(project: Project): Boolean {
    return ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProject(project, SYSTEM_ID)
  }

  /**
   * @param project   Project required to restart connectors
   * @param wait      if true, then maven server(s) restarted synchronously
   * @param condition only connectors satisfied for this predicate will be restarted
   */
  @JvmOverloads
  @JvmStatic
  fun restartMavenConnectors(
    project: Project,
    wait: Boolean,
    condition: Predicate<MavenServerConnector> = Predicate { c: MavenServerConnector -> java.lang.Boolean.TRUE },
  ) {
    getInstance().restartMavenConnectors(project, wait, condition)
  }

  @JvmStatic
  fun verifyMavenSdkRequirements(jdk: Sdk, mavenVersion: String?): Boolean {
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.3.1") < 0) {
      return true
    }
    val sdkType = jdk.getSdkType()
    if (sdkType is JavaSdk) {
      val version = sdkType.getVersion(jdk)
      if (version == null || version.isAtLeast(JavaSdkVersion.JDK_1_7)) {
        return true
      }
    }
    return false
  }

  @Throws(IOException::class)
  fun crcWithoutSpaces(`in`: InputStream): Int {
    try {
      val crc = CRC32()

      val parser = SAXParserFactory.newDefaultInstance().newSAXParser()
      parser.getXMLReader().setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      parser.parse(`in`, object : DefaultHandler() {
        var textContentOccur: Boolean = false
        var spacesCrc: Int = 0

        fun putString(string: String?) {
          if (string == null) return

          var i = 0
          val end = string.length
          while (i < end) {
            crc.update(string.get(i).code)
            i++
          }
        }

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: org.xml.sax.Attributes) {
          textContentOccur = false

          crc.update(1)
          putString(qName)

          for (i in 0..<attributes.getLength()) {
            putString(attributes.getQName(i))
            putString(attributes.getValue(i))
          }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
          textContentOccur = false

          crc.update(2)
          putString(qName)
        }

        fun processTextOrSpaces(ch: CharArray, start: Int, length: Int) {
          var i = start
          val end = start + length
          while (i < end) {
            val a = ch[i]

            if (Character.isWhitespace(a)) {
              if (textContentOccur) {
                spacesCrc = spacesCrc * 31 + a.code
              }
            }
            else {
              if (textContentOccur && spacesCrc != 0) {
                crc.update(spacesCrc)
                crc.update(spacesCrc shr 8)
              }

              crc.update(a.code)

              textContentOccur = true
              spacesCrc = 0
            }
            i++
          }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
          processTextOrSpaces(ch, start, length)
        }

        override fun ignorableWhitespace(ch: CharArray, start: Int, length: Int) {
          processTextOrSpaces(ch, start, length)
        }

        override fun processingInstruction(target: String?, data: String?) {
          putString(target)
          putString(data)
        }

        override fun skippedEntity(name: String?) {
          putString(name)
        }

        override fun error(e: SAXParseException?) {
          crc.update(100)
        }
      })

      return crc.getValue().toInt()
    }
    catch (e: ParserConfigurationException) {
      throw RuntimeException(e)
    }
    catch (e: SAXException) {
      return -1
    }
  }

  fun getSdkPath(sdk: Sdk?): String? {
    if (sdk == null) return null

    var homeDirectory = sdk.getHomeDirectory()
    if (homeDirectory == null) return null

    if ("jre" != homeDirectory.getName()) {
      val jreDir = homeDirectory.findChild("jre")
      if (jreDir != null) {
        homeDirectory = jreDir
      }
    }

    return homeDirectory.getPath()
  }

  @JvmStatic
  fun getModuleJreHome(mavenProjectsManager: MavenProjectsManager, mavenProject: MavenProject): String? {
    return getSdkPath(getModuleJdk(mavenProjectsManager, mavenProject))
  }

  @JvmStatic
  fun getModuleJavaVersion(mavenProjectsManager: MavenProjectsManager, mavenProject: MavenProject): String? {
    val sdk: Sdk? = getModuleJdk(mavenProjectsManager, mavenProject)
    if (sdk == null) return null

    return sdk.getVersionString()
  }

  fun getModuleJdk(mavenProjectsManager: MavenProjectsManager, mavenProject: MavenProject): Sdk? {
    val module = mavenProjectsManager.findModule(mavenProject)
    if (module == null) return null

    return ModuleRootManager.getInstance(module).getSdk()
  }

/*    @JvmStatic
  fun <K, V : MutableMap<*, *>?> getOrCreate(map: MutableMap<K?, V?>, key: K?): V {
    var res = map.get(key)
    if (res == null) {
      res = HashMap<Any?, Any?>() as V
      map.put(key, res)
    }

    return res
  }*/

  @JvmStatic
  fun isMavenModule(module: Module?): Boolean {
    return module != null && MavenProjectsManager.getInstance(module.getProject()).isMavenizedModule(module)
  }

  fun getArtifactName(packaging: String?, moduleName: String?, exploded: Boolean): String {
    return moduleName + ":" + packaging + (if (exploded) " exploded" else "")
  }

  fun getEjbClientArtifactName(moduleName: String?, exploded: Boolean): String {
    return moduleName + ":ejb" + (if (exploded) CLIENT_EXPLODED_ARTIFACT_SUFFIX else CLIENT_ARTIFACT_SUFFIX)
  }

  @JvmStatic
  fun getIdeaVersionToPassToMavenProcess(): String = ApplicationInfoImpl.getShadowInstance().getMajorVersion() + "." + ApplicationInfoImpl.getShadowInstance().getMinorVersion()

  @JvmStatic
  fun isPomFileName(fileName: String): Boolean {
    return fileName == MavenConstants.POM_XML ||
           fileName.endsWith(".pom") || fileName.startsWith("pom.") ||
           fileName == MavenConstants.SUPER_POM_4_0_XML
  }

  @JvmStatic
  fun isPotentialPomFile(nameOrPath: String): Boolean {
    return ArrayUtil.contains(FileUtilRt.getExtension(nameOrPath), *MavenConstants.POM_EXTENSIONS)
  }

  @JvmStatic
  fun isPomFile(file: VirtualFile?): Boolean {
    return isPomFile(null, file)
  }

  @JvmStatic
  fun isPomFile(project: Project?, file: VirtualFile?): Boolean {
    if (file == null) return false

    val name = file.getName()
    if (isPomFileName(name)) return true
    if (!isPotentialPomFile(name)) return false

    return isPomFileIgnoringName(project, file)
  }


  @JvmStatic
  fun containsDeclaredExtension(extensionFile: Path, mavenId: MavenId): Boolean {
    try {
      val extensions: Element? = getDomRootElement(extensionFile)
      if (extensions == null) return false
      if (extensions.getName() != "extensions") return false
      for (extension in getElementsWithRegardToNamespace(extensions, "extension", extensionListNamespaces)) {
        val groupId: Element? = getElementWithRegardToNamespace(extension, "groupId", extensionListNamespaces)
        val artifactId: Element? = getElementWithRegardToNamespace(extension, "artifactId", extensionListNamespaces)
        val version: Element? = getElementWithRegardToNamespace(extension, "version", extensionListNamespaces)

        if (groupId != null &&
            groupId.getTextTrim() == mavenId.getGroupId() && artifactId != null &&
            artifactId.getTextTrim() == mavenId.getArtifactId() && version != null &&
            version.getTextTrim() == mavenId.getVersion()
        ) {
          return true
        }
      }
    }
    catch (e: IOException) {
      return false
    }
    catch (e: JDOMException) {
      return false
    }
    return false
  }

  @JvmStatic
  fun isPomFileIgnoringName(project: Project?, file: VirtualFile): Boolean {
    if (project == null || !project.isInitialized()) {
      if (!FileUtilRt.extensionEquals(file.getName(), "xml")) return false
      try {
        file.getInputStream().use { `in` ->
          val isPomFile = AtomicBoolean(false)
          val reader: Reader = BufferedReader(InputStreamReader(`in`, StandardCharsets.UTF_8))
          NanoXmlUtil.parse(reader, object : NanoXmlBuilder {
            @Throws(Exception::class)
            override fun startElement(name: String, nsPrefix: String?, nsURI: String?, systemID: String, lineNr: Int) {
              if ("project" == name) {
                isPomFile.set(nsURI?.startsWith("http://maven.apache.org/POM/") == true)
              }
              NanoXmlBuilder.stop()
            }
          })
          return isPomFile.get()
        }
      }
      catch (ignore: IOException) {
        return false
      }
    }

    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    if (mavenProjectsManager.findProject(file) != null) return true

    return ReadAction.compute<Boolean?, RuntimeException?>(ThrowableComputable {
      if (project.isDisposed()) return@ThrowableComputable false
      val psiFile = PsiManager.getInstance(project).findFile(file)
      if (psiFile == null) return@ThrowableComputable false
      MavenDomUtil.isProjectFile(psiFile)
    })
  }

  @JvmStatic
  fun streamPomFiles(project: Project?, root: VirtualFile?): Stream<VirtualFile?> {
    if (root == null) return Stream.empty<VirtualFile?>()
    return Stream.of<VirtualFile?>(*root.getChildren()).filter { file: VirtualFile? -> isPomFile(project, file) }
  }

  fun restartConfigHighlighting(projects: Collection<MavenProject>) {
    val configFiles = getConfigFiles(projects)
    ApplicationManager.getApplication().invokeLater(Runnable {
      FileContentUtilCore.reparseFiles(*configFiles)
    })
  }

  fun getConfigFiles(projects: Collection<MavenProject>): Array<VirtualFile> {
    val result = SmartList<VirtualFile>()
    for (project in projects) {
      val file = getConfigFile(project, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH)
      if (file != null) {
        result.add(file)
      }
    }
    if (result.isEmpty()) {
      return VirtualFile.EMPTY_ARRAY
    }
    return result.toTypedArray()
  }

  fun getConfigFile(mavenProject: MavenProject, fileRelativePath: String): VirtualFile? {
    val baseDir: VirtualFile = getVFileBaseDir(mavenProject.directoryFile)
    return baseDir.findFileByRelativePath(fileRelativePath)
  }

  @JvmStatic
  fun toPath(mavenProject: MavenProject?, path: String): MavenPathWrapper {
    var path = path
    if (!Paths.get(path).isAbsolute()) {
      requireNotNull(mavenProject) { "Project should be not-nul for non-absolute paths" }
      path = Path.of(mavenProject.directory, path).toString()
    }
    return MavenPathWrapper(path)
  }

  internal fun MavenServerConnector.isCompatibleWith(project: Project, jdk: Sdk, multimoduleDirectory: String): Boolean {
    if (Registry.`is`("maven.server.per.idea.project")) return true
    if (this.project != project) return false

    val cache = MavenDistributionsCache.getInstance(project)
    val distribution = cache.getMavenDistribution(multimoduleDirectory)
    val vmOptions = cache.getVmOptions(multimoduleDirectory)

    if (!this.mavenDistribution.compatibleWith(distribution)) {
      return false
    }
    if (!StringUtil.equals(this.jdk.name, jdk.name)) {
      return false
    }
    return StringUtil.equals(this.vmOptions, vmOptions)
  }

  internal fun getJdkForImporter(project: Project): Sdk {
    val settings = MavenWorkspaceSettingsComponent.getInstance(project).settings
    val jdkForImporterName = settings.importingSettings.jdkForImporter
    var jdk: Sdk
    try {
      jdk = getJdk(project, jdkForImporterName)
    }
    catch (_: ExternalSystemJdkException) {
      jdk = getJdk(project, MavenRunnerSettings.USE_PROJECT_JDK)
      MavenProjectsManager.getInstance(project).syncConsole.addWarning(
        SyncBundle.message("importing.jdk.changed"),
        SyncBundle.message("importing.jdk.changed.description", jdkForImporterName, jdk.name)
      )
    }
    if (JavaSdkVersionUtil.isAtLeast(jdk, JavaSdkVersion.JDK_1_8)) {
      return jdk
    }
    else {
      MavenLog.LOG.info("Selected jdk [" + jdk.name + "] is not JDK1.8+ Will use internal jdk instead")
      return JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk
    }
  }

  private fun getJdk(project: Project, name: String): Sdk {
    if (name == MavenRunnerSettings.USE_INTERNAL_JAVA || project.isDefault()) {
      return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk()
    }

    if (name == MavenRunnerSettings.USE_PROJECT_JDK) {
      val res = ProjectRootManager.getInstance(project).getProjectSdk()

      if (res != null && res.getSdkType() is JavaSdkType) {
        return res
      }
      return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk()
    }

    if (name == MavenRunnerSettings.USE_JAVA_HOME) {
      val javaHome = ExternalSystemJdkUtil.getJavaHome()
      if (StringUtil.isEmptyOrSpaces(javaHome)) {
        throw InvalidJavaHomeException(javaHome)
      }
      try {
        return JavaSdk.getInstance().createJdk("", javaHome!!)
      }
      catch (e: IllegalArgumentException) {
        throw InvalidJavaHomeException(javaHome)
      }
    }

    val projectJdk: Sdk? = getSdkByExactName(name)
    if (projectJdk != null) return projectJdk
    throw InvalidSdkException(name)
  }

  private fun getSdkByExactName(name: String): Sdk? {
    for (projectJdk in ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getName() == name) {
        if (projectJdk.getSdkType() is JavaSdkType) {
          return projectJdk
        }
      }
    }
    return null
  }

  @JvmStatic
  val mavenPluginParentFile: Path
    get() = Paths.get(PathManager.getCommunityHomePath(), "plugins", "maven")

  @JvmStatic
  @ApiStatus.Internal
  fun isMavenUnitTestModeEnabled(): Boolean {
    if (shouldRunTasksAsynchronouslyInTests()) {
      return false
    }
    return ApplicationManager.getApplication().isUnitTestMode()
  }

  private fun shouldRunTasksAsynchronouslyInTests(): Boolean {
    return java.lang.Boolean.getBoolean("maven.unit.tests.remove")
  }

  fun getCompilerPluginVersion(mavenProject: MavenProject): String {
    val plugin = mavenProject.findPlugin("org.apache.maven.plugins", "maven-compiler-plugin")
    return plugin?.version ?: ""
  }

  @JvmStatic
  fun isWrapper(settings: MavenGeneralSettings): Boolean {
    return settings.getMavenHomeType() is MavenWrapper
  }

  fun suggestProjectSdk(project: Project): Sdk? {
    val projectJdkTable = ProjectJdkTable.getInstance()
    val sdkType = ExternalSystemJdkUtil.getJavaSdkType()
    return projectJdkTable.getSdksOfType(sdkType)
      .filterNotNull()
      .filter { it: Sdk -> JdkUtil.isCompatible(it, project) }
      .filter { it: Sdk -> it.homeDirectory?.toNioPath()?.let { JdkUtil.checkForJdk(it) } == true }
      .maxWithOrNull(sdkType.versionComparator())
  }

  @JvmStatic
  fun isMavenizedModule(m: Module): Boolean {
    try {
      return !m.isDisposed() && getInstance(m).isMavenized()
    }
    catch (e: AlreadyDisposedException) {
      return false
    }
  }

  @ApiStatus.Internal
  fun shouldResetDependenciesAndFolders(readingProblems: Collection<MavenProjectProblem>): Boolean {
    if (`is`("maven.always.reset")) return true
    val unrecoverable = readingProblems.find { it.isError() }
    return unrecoverable == null
  }

  @ApiStatus.Internal
  fun shouldKeepPreviousResolutionResults(readingProblems: Collection<MavenProjectProblem>): Boolean {
    return !shouldResetDependenciesAndFolders(readingProblems)
  }

  @Deprecated("use MavenUtil.resolveSuperPomFile")
  fun getEffectiveSuperPom(project: Project, workingDir: String): VirtualFile? {
    val distribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(workingDir)
    return resolveSuperPomFile(distribution.mavenHome, MavenConstants.SUPER_POM_4_0_XML)
  }


  @JvmStatic
  @Deprecated("use MavenUtil.resolveSuperPomFile")
  fun getEffectiveSuperPomWithNoRespectToWrapper(project: Project): VirtualFile? {
    val distribution = MavenDistributionsCache.getInstance(project).getSettingsDistribution()
    return resolveSuperPomFile(distribution.mavenHome, MavenConstants.SUPER_POM_4_0_XML)
  }

  fun createModelReadHelper(project: Project): MavenProjectModelReadHelper {
    return MavenProjectModelReadHelper.getInstance(project)
  }

  @JvmStatic
  fun collectClasspath(classes: MutableCollection<Class<*>>): MutableCollection<Path?> {
    val result = ArrayList<Path?>()
    for (c in classes) {
      result.add(Path.of(PathUtil.getJarPathForClass(c)))
    }
    return result
  }
}
