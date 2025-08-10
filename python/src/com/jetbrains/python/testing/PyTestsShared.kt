// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.execution.*
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.targetPath
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.JDOMExternalizerUtil.readField
import com.intellij.openapi.util.JDOMExternalizerUtil.writeField
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.QualifiedName
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.remote.PathMappingProvider
import com.intellij.remote.RemoteSdkAdditionalData
import com.intellij.util.ThreeState
import com.jetbrains.python.PyBundle
import com.jetbrains.python.extensions.*
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.hasInstalledPackageSnapshot
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.reflection.DelegationProperty
import com.jetbrains.python.reflection.Properties
import com.jetbrains.python.reflection.Property
import com.jetbrains.python.reflection.getProperties
import com.jetbrains.python.run.*
import com.jetbrains.python.run.PythonScriptCommandLineState.getExpandedWorkingDir
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import com.jetbrains.python.run.targetBasedConfiguration.TargetWithVariant
import com.jetbrains.python.run.targetBasedConfiguration.createRefactoringListenerIfPossible
import com.jetbrains.python.run.targetBasedConfiguration.targetAsPsiElement
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.testing.doctest.PythonDocTestUtil
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.TestStdErr
import jetbrains.buildServer.messages.serviceMessages.TestStdOut
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.nio.file.Path
import java.util.regex.Matcher

fun getFactoryById(id: String): PyAbstractTestFactory<*>? =
  // user may have "pytest" because it was used instead of py.test (old id) for some time
  PythonTestConfigurationType.getInstance().typedFactories.toTypedArray().firstOrNull { it.id == if (id == "pytest") PyTestFactory.id else id }

fun getFactoryByIdOrDefault(id: String): PyAbstractTestFactory<*> = getFactoryById(id)
                                                                    ?: PythonTestConfigurationType.getInstance().autoDetectFactory

/**
 * Accepts text that may be wrapped in TC message. Unwraps it and removes TC escape code.
 * Regular text is unchanged
 */
fun processTCMessage(text: String): String {
  val parsedMessage = ServiceMessage.parse(text.trim()) ?: return text // Not a TC message
  return when (parsedMessage) {
    is TestStdOut -> parsedMessage.stdOut // TC with stdout
    is TestStdErr -> parsedMessage.stdErr // TC with stderr
    else -> "" // TC with out of any output
  }
}

internal fun getAdditionalArgumentsProperty() = PyAbstractTestConfiguration::additionalArguments

/**
 * Checks if element could be test target
 * @param testCaseClassRequired see [PythonUnitTestDetectorsBasedOnSettings] docs
 */
fun isTestElement(element: PsiElement, testCaseClassRequired: ThreeState, typeEvalContext: TypeEvalContext): Boolean = when (element) {
  is PyFile -> PythonUnitTestDetectorsBasedOnSettings.isTestFile(element, testCaseClassRequired, typeEvalContext)
  is PsiDirectory -> isTestFolder(element, testCaseClassRequired, typeEvalContext)
  is PyFunction -> PythonUnitTestDetectorsBasedOnSettings.isTestFunction(element,
                                                                         testCaseClassRequired, typeEvalContext)
  is PyClass -> {
    PythonUnitTestDetectorsBasedOnSettings.isTestClass(element, testCaseClassRequired, typeEvalContext)
  }
  else -> false
}

/**
 * If element is a subelement of the folder excplicitly marked as test root -- use it
 */
private fun getExplicitlyConfiguredTestRoot(element: PsiFileSystemItem): VirtualFile? {
  val vfDirectory = element.virtualFile
  val module = ModuleUtil.findModuleForPsiElement(element) ?: return null
  return ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.TEST_SOURCE).firstOrNull {
    VfsUtil.isAncestor(it, vfDirectory, false)
  }
}

private fun isTestFolder(element: PsiDirectory,
                         testCaseClassRequired: ThreeState,
                         typeEvalContext: TypeEvalContext): Boolean {
  return (getExplicitlyConfiguredTestRoot(element) != null) || element.name.contains("test", true) || element.children.any {
    it is PyFile && PythonUnitTestDetectorsBasedOnSettings.isTestFile(it, testCaseClassRequired, typeEvalContext)
  }
}


/**
 * Since runners report names of tests as qualified name, no need to convert it to PSI and back to string.
 * We just save its name and provide it again to rerun
 * @param metainfo additional info provided by test runner, in case of pytest it is test name with parameters (if test is parametrized)
 */
private class PyTargetBasedPsiLocation(val target: ConfigurationTarget,
                                       element: PsiElement,
                                       val metainfo: String?) : PsiLocation<PsiElement>(element) {
  override fun equals(other: Any?): Boolean {
    if (other is PyTargetBasedPsiLocation) {
      return target == other.target && metainfo == other.metainfo
    }
    return false
  }

  override fun hashCode(): Int {
    return target.hashCode()
  }
}


/**
 * @return factory chosen by user in "test runner" settings
 */
private fun findConfigurationFactoryFromSettings(module: Module): ConfigurationFactory =
  TestRunnerService.getInstance(module).selectedFactory


// folder provided by python side. Resolve test names versus it
private val PATH_URL = java.util.regex.Pattern.compile("^python<([^<>]+)>$")


private fun Sdk.getMapping(project: Project) = (sdkAdditionalData as? RemoteSdkAdditionalData)?.let { data ->
  PathMappingProvider.getSuitableMappingProviders(data).flatMap { it.getPathMappingSettings(project, data).pathMappings }
} ?: emptyList()

private fun getFolderFromMatcher(matcher: Matcher, module: Module): String? {
  if (!matcher.matches()) {
    return null
  }
  val folder = matcher.group(1)
  val sdk = module.getSdk()
  if (sdk != null && PythonSdkUtil.isRemote(sdk)) {
    return sdk.getMapping(module.project).find { it.canReplaceRemote(folder) }?.mapToLocal(folder)
  }
  else {
    return folder
  }
}

private fun getElementByUrl(protocol: String,
                            path: String,
                            module: Module,
                            evalContext: TypeEvalContext,
                            matcher: Matcher = PATH_URL.matcher(protocol),
                            metainfo: String? = null): Location<out PsiElement>? = runReadAction {
  val folder = getFolderFromMatcher(matcher, module)?.let { LocalFileSystem.getInstance().findFileByPath(it) }

  val qualifiedName = QualifiedName.fromDottedString(path)
  // Assume qname id good and resolve it directly
  val element = qualifiedName.resolveToElement(QNameResolveContext(ModuleBasedContextAnchor(module),
                                                                   evalContext = evalContext,
                                                                   folderToStart = folder,
                                                                   allowInaccurateResult = true))
  if (element != null) {
    // Path is qualified name of python test according to runners protocol
    // Parentheses are part of generators / parametrized tests
    // Until https://github.com/JetBrains/teamcity-messages/issues/121 they are disabled,
    // so we cut them out of path not to provide unsupported targets to runners
    val pathNoParentheses = QualifiedName.fromComponents(
      qualifiedName.components.filter { !it.contains('(') }).toString()
    PyTargetBasedPsiLocation(ConfigurationTarget(pathNoParentheses, PyRunTargetVariant.PYTHON), element, metainfo)
  }
  else {
    null
  }
}


object PyTestsLocator : SMTestLocator {

  override fun getLocation(protocol: String,
                           path: String,
                           metainfo: String?,
                           project: Project,
                           scope: GlobalSearchScope): List<Location<out PsiElement>> = getLocationInternal(protocol, path, project,
                                                                                                           metainfo, scope)

  override fun getLocation(protocol: String, path: String, project: Project, scope: GlobalSearchScope): List<Location<out PsiElement>> {
    return getLocationInternal(protocol, path, project, null, scope)
  }

  private fun getLocationInternal(protocol: String,
                                  path: String,
                                  project: Project,
                                  metainfo: String?,
                                  scope: GlobalSearchScope): List<Location<out PsiElement>> {
    if (scope !is ModuleWithDependenciesScope) {
      return listOf()
    }
    val matcher = PATH_URL.matcher(protocol)
    if (!matcher.matches()) {
      // special case: setup.py runner uses unittest configuration but different (old) protocol
      // delegate to old protocol locator until setup.py moved to separate configuration
      val oldLocation = PythonUnitTestTestIdUrlProvider.INSTANCE.getLocation(protocol, path, project, scope)
      if (oldLocation.isNotEmpty()) {
        return oldLocation
      }
    }

    return getElementByUrl(protocol, path, scope.module, TypeEvalContext.codeAnalysis(project, null), matcher, metainfo)?.let {
      listOf(it)
    } ?: listOf()
  }
}


abstract class PyTestExecutionEnvironment<T : PyAbstractTestConfiguration>(configuration: T,
                                                                           environment: ExecutionEnvironment)
  : PythonTestCommandLineStateBase<T>(configuration, environment) {

  override fun getTestLocator(): SMTestLocator = PyTestsLocator

  /**
   * *To be deprecated. The part of the legacy implementation based on [GeneralCommandLine].*
   */
  override fun getTestSpecs(): List<String> = configuration.getTestSpec()

  override fun getTestSpecs(request: TargetEnvironmentRequest): List<TargetEnvironmentFunction<String>> = configuration.getTestSpec(request)

  override fun generateCommandLine(): GeneralCommandLine {
    val line = super.generateCommandLine()
    line.workDirectory = java.io.File(configuration.workingDirectorySafe)
    return line
  }
}


abstract class PyAbstractTestSettingsEditor(private val sharedForm: PyTestSharedForm)
  : SettingsEditor<PyAbstractTestConfiguration>() {


  override fun resetEditorFrom(s: PyAbstractTestConfiguration) {
    // usePojoProperties is true because we know that Form is java-based
    AbstractPythonRunConfiguration.copyParams(s, sharedForm.optionsForm)
    s.copyTo(getProperties(sharedForm, usePojoProperties = true))
  }

  override fun applyEditorTo(s: PyAbstractTestConfiguration) {
    AbstractPythonRunConfiguration.copyParams(sharedForm.optionsForm, s)
    s.copyFrom(getProperties(sharedForm, usePojoProperties = true))
  }

  override fun createEditor(): javax.swing.JComponent = sharedForm.panel
}

/**
 * Default target path (run all tests ion project folder)
 */
private const val DEFAULT_PATH = ""

/**
 * Target depends on target type. It could be path to file/folder or python target
 */
data class ConfigurationTarget(@ConfigField("runcfg.python_tests.config.target") override var target: String,
                               @ConfigField(
                                 "runcfg.python_tests.config.targetType") override var targetType: PyRunTargetVariant) : TargetWithVariant {
  fun copyTo(dst: ConfigurationTarget) {
    // TODO:  do we have such method it in Kotlin?
    dst.target = target
    dst.targetType = targetType
  }

  /**
   * Validates configuration and throws exception if target is invalid
   */
  fun checkValid() {
    if (targetType != PyRunTargetVariant.CUSTOM && target.isEmpty()) {
      throw RuntimeConfigurationWarning(PyBundle.message("python.testing.target.not.provided"))
    }
    if (targetType == PyRunTargetVariant.PYTHON && !isWellFormed()) {
      throw RuntimeConfigurationError(PyBundle.message("python.testing.provide.qualified.name"))
    }
  }

  fun asPsiElement(configuration: PyAbstractTestConfiguration): PsiElement? =
    asPsiElement(configuration, configuration.getWorkingDirectoryAsVirtual())

  /**
   * *To be deprecated. The part of the legacy implementation based on [GeneralCommandLine].*
   */
  fun generateArgumentsLine(configuration: PyAbstractTestConfiguration): List<String> =
    when (targetType) {
      PyRunTargetVariant.CUSTOM -> emptyList()
      PyRunTargetVariant.PYTHON -> getArgumentsForPythonTarget(configuration)
      PyRunTargetVariant.PATH -> listOf("--path", target.trim())
    }

  fun generateArgumentsLine(request: TargetEnvironmentRequest,
                            configuration: PyAbstractTestConfiguration): List<TargetEnvironmentFunction<String>> =
    when (targetType) {
      PyRunTargetVariant.CUSTOM -> emptyList()
      PyRunTargetVariant.PYTHON -> getArgumentsForPythonTarget(configuration).map(::constant)
      PyRunTargetVariant.PATH -> listOf(constant("--path"), targetPath(Path.of(target.trim())))
    }

  private fun getArgumentsForPythonTarget(configuration: PyAbstractTestConfiguration): List<String> = runReadAction ra@{
    val element = asPsiElement(configuration) ?: throw ExecutionException(PyBundle.message("python.testing.cant.resolve", target))

    if (element is PsiDirectory) {
      // Directory is special case: we can't run it as package for now, so we run it as path
      return@ra listOf("--path", element.virtualFile.path)
    }

    val context = TypeEvalContext.userInitiated(configuration.project, null)
    val qNameResolveContext = QNameResolveContext(
      contextAnchor = ModuleBasedContextAnchor(configuration.module!!),
      evalContext = context,
      folderToStart = LocalFileSystem.getInstance().findFileByPath(configuration.workingDirectorySafe),
      allowInaccurateResult = true
    )
    val qualifiedNameParts = QualifiedName.fromDottedString(target.trim()).tryResolveAndSplit(qNameResolveContext)
                             ?: throw ExecutionException(PyBundle.message("python.testing.cant.find.where.declared", target))

    // We can't provide element qname here: it may point to parent class in case of inherited functions,
    // so we make fix file part, but obey element(symbol) part of qname

    if (!configuration.shouldSeparateTargetPath()) {
      // Here generate qname instead of file/path::element_name

      // Try to set path relative to work dir (better than path from closest root)
      // If we can resolve element by this path relative to working directory then use it
      val qNameInsideOfDirectory = qualifiedNameParts.getElementNamePrependingFile()
      val elementAndName = qNameInsideOfDirectory.getElementAndResolvableName(qNameResolveContext.copy(allowInaccurateResult = false))
      if (elementAndName != null) {
        // qNameInsideOfDirectory may contain redundant elements like subtests so we use name that was really resolved
        // element.qname can't be used because inherited test resolves to parent
        return@ra generatePythonTarget(elementAndName.name.toString(), configuration)
      }
      // Use "full" (path from closest root) otherwise
      val name = (element.containingFile as? PyFile)?.getQName()?.append(qualifiedNameParts.elementName) ?: throw ExecutionException(
        PyBundle.message("python.testing.cant.get.importable.name", element.containingFile))

      return@ra generatePythonTarget(name.toString(), configuration)
    }
    else {

      // Here generate file/path::element_name
      val pyTarget = qualifiedNameParts.elementName

      val elementFile = element.containingFile.virtualFile
      val workingDir = elementFile.fileSystem.findFileByPath(configuration.workingDirectorySafe)

      val fileSystemPartOfTarget = (if (workingDir != null) VfsUtil.getRelativePath(elementFile, workingDir)
      else null)
                                   ?: elementFile.path

      if (pyTarget.componentCount == 0) {
        // If python part is empty we are launching file. To prevent junk like "foo.py::" we run it as file instead
        return@ra listOf("--path", fileSystemPartOfTarget)
      }

      return@ra generatePythonTarget("$fileSystemPartOfTarget::$pyTarget", configuration)

    }
  }

  private fun generatePythonTarget(target: String, configuration: PyAbstractTestConfiguration) =
    listOf("--target", target + configuration.pythonTargetAdditionalParams)

  /**
   * @return directory which target is situated
   */
  fun getElementDirectory(configuration: PyAbstractTestConfiguration): VirtualFile? {
    if (target == DEFAULT_PATH) {
      //This means "current directory", so we do not know where is it
      // getting vitualfile for it may return PyCharm working directory which is not what we want
      return null
    }
    val fileOrDir = asVirtualFile() ?: asPsiElement(configuration)?.containingFile?.virtualFile ?: return null
    return if (fileOrDir.isDirectory) fileOrDir else fileOrDir.parent
  }
}


/**
 * To prevent legacy configuration options  from clashing with new names, we add prefix
 * to use for writing/reading xml
 */
private val Property.prefixedName: String
  get() = "_new_" + this.getName()


/**
 * "Custom symbol" is mode when test runner uses [PyRunTargetVariant.CUSTOM] and has
 * [PyAbstractTestConfiguration.additionalArguments] that points to some symbol inside of file i.e.:
 * ``/foo/bar.py::SomeTest.some_method``
 *
 * This mode is framework-specific.
 * It is used for cases like ``PY-25586``
 */
internal interface PyTestConfigurationWithCustomSymbol {
  /**
   * Separates file part and symbol
   */
  val fileSymbolSeparator: String

  /**
   * Separates parts of symbol name
   */
  val symbolSymbolSeparator: String

  fun createAdditionalArguments(parts: QualifiedNameParts) = with(parts) {
    file.virtualFile.path + fileSymbolSeparator + elementName.components.joinToString(symbolSymbolSeparator)
  }
}

/**
 * Parent of all new test configurations.
 * All config-specific fields are implemented as properties. They are saved/restored automatically and passed to GUI form.
 *
 */
abstract class PyAbstractTestConfiguration(project: Project,
                                           private val testFactory: PyAbstractTestFactory<*>)
  : AbstractPythonTestRunConfiguration<PyAbstractTestConfiguration>(project, testFactory, testFactory.packageRequired),
    PyRerunAwareConfiguration,
    RefactoringListenerProvider,
    SMRunnerConsolePropertiesProvider {

  override fun createTestConsoleProperties(executor: Executor): SMTRunnerConsoleProperties =
    PythonTRunnerConsoleProperties(this, executor, true, PyTestsLocator).also { properties ->
      if (isIdTestBased) properties.makeIdTestBased()
    }

  /**
   * Additional parameters added to the test name for parametrized tests
   */
  open val pythonTargetAdditionalParams: String = ""

  /**
   * Args after it passed to test runner itself
   */
  protected val rawArgumentsSeparator = "--"

  @DelegationProperty
  val target: ConfigurationTarget = ConfigurationTarget(DEFAULT_PATH, PyRunTargetVariant.PATH)

  @ConfigField("runcfg.python_tests.config.additionalArguments")
  var additionalArguments: String = ""

  val testFrameworkName: String = testFactory.name


  fun isTestClassRequired(): ThreeState {
    val sdk = sdk ?: return ThreeState.UNSURE
    return if (testFactory.onlyClassesAreSupported(project, sdk)) {
      ThreeState.YES
    }
    else {
      ThreeState.NO
    }
  }


  /**
   * For real launch use [getWorkingDirectorySafe] instead
   */
  internal fun getWorkingDirectoryAsVirtual(): VirtualFile? {
    if (!workingDirectory.isNullOrEmpty()) {
      return LocalFileSystem.getInstance().findFileByPath(workingDirectory)
    }
    return null
  }

  override fun getWorkingDirectorySafe(): String {
    workingDirectory?.takeIf { it.isNotEmpty() }?.let {
      return getExpandedWorkingDir(this)
    }

    return ApplicationManager.getApplication().runReadAction<String> {
      target.getElementDirectory(this)?.path ?: super.getWorkingDirectorySafe()
    }
  }

  override fun getRefactoringElementListener(element: PsiElement?): RefactoringElementListener? {
    if (element == null) return null
    var renamer = CompositeRefactoringElementListener(PyWorkingDirectoryRenamer(getWorkingDirectoryAsVirtual(), this))
    createRefactoringListenerIfPossible(element, target.asPsiElement(this), target.asVirtualFile(), { target.target = it })?.let {
      renamer = renamer.plus(it)
    }
    return renamer
  }

  override fun checkConfiguration() {
    super.checkConfiguration()
    target.checkValid()
  }


  override fun isIdTestBased(): Boolean = true

  /**
   * *To be deprecated. The part of the legacy implementation based on [GeneralCommandLine].*
   */
  private fun getPythonTestSpecByLocation(location: Location<*>): List<String> {

    if (location is PyTargetBasedPsiLocation) {
      return location.target.generateArgumentsLine(this)
    }

    if (location !is PsiLocation) {
      return emptyList()
    }
    if (location.psiElement !is PyQualifiedNameOwner) {
      return emptyList()
    }
    val qualifiedName = (location.psiElement as PyQualifiedNameOwner).qualifiedName ?: return emptyList()

    // Resolve name as python qname as last resort
    return ConfigurationTarget(qualifiedName, PyRunTargetVariant.PYTHON).generateArgumentsLine(this)
  }

  private fun getPythonTestSpecByLocation(request: TargetEnvironmentRequest,
                                          location: Location<*>): List<TargetEnvironmentFunction<String>> {
    if (location is PyTargetBasedPsiLocation) {
      return location.target.generateArgumentsLine(request, this)
    }

    if (location !is PsiLocation) {
      return emptyList()
    }
    if (location.psiElement !is PyQualifiedNameOwner) {
      return emptyList()
    }
    val qualifiedName = (location.psiElement as PyQualifiedNameOwner).qualifiedName ?: return emptyList()

    // Resolve name as python qname as last resort
    return ConfigurationTarget(qualifiedName, PyRunTargetVariant.PYTHON).generateArgumentsLine(request, this)
  }

  final override fun getTestSpec(location: Location<*>,
                                 failedTest: AbstractTestProxy): String? {
    val list = getPythonTestSpecByLocation(location)
    if (list.isEmpty()) {
      return null
    }
    else {
      return list.joinToString(" ")
    }
  }

  /**
   * *To be deprecated. The part of the legacy implementation based on [GeneralCommandLine].*
   */
  override fun getTestSpecsForRerun(scope: GlobalSearchScope,
                                    locations: List<Pair<Location<*>, AbstractTestProxy>>): List<String> =
    // Set used to remove duplicate targets
    locations
      .map { it.first }
      .distinctBy { it.psiElement }
      .flatMap { getPythonTestSpecByLocation(it) } + generateRawArguments(true)

  override fun getTestSpecsForRerun(request: TargetEnvironmentRequest,
                                    scope: GlobalSearchScope,
                                    locations: List<Pair<Location<*>, AbstractTestProxy>>): List<TargetEnvironmentFunction<String>> =
    // Set used to remove duplicate targets
    locations
      .map { it.first }
      .distinctBy { it.psiElement }
      .flatMap { getPythonTestSpecByLocation(request, it) } + generateRawArguments(true).map(::constant)

  /**
   * *To be deprecated. The part of the legacy implementation based on [GeneralCommandLine].*
   */
  fun getTestSpec(): List<String> = target.generateArgumentsLine(this) + generateRawArguments()

  fun getTestSpec(request: TargetEnvironmentRequest): List<TargetEnvironmentFunction<String>> =
    target.generateArgumentsLine(request, this) + generateRawArguments().map(::constant)

  /**
   * raw arguments to be added after "--" and passed to runner directly
   */
  private fun generateRawArguments(forRerun: Boolean = false): List<String> {
    val rawArguments = additionalArguments + " " + getCustomRawArgumentsString(forRerun)
    if (rawArguments.isNotBlank()) {
      return listOf(rawArgumentsSeparator) + com.intellij.util.execution.ParametersListUtil.parse(rawArguments, false, true)
    }
    return emptyList()
  }

  /**
   * If true, then framework name must be used as part of the run configuration name i.e "pytest: spam.eggs"
   */
  protected open val useFrameworkNameInConfiguration = true

  override fun suggestedName(): String {
    val testFrameworkName = if (useFrameworkNameInConfiguration) testFrameworkName else PyBundle.message("runcfg.test.display_name")
    return when (target.targetType) {
      PyRunTargetVariant.PATH -> {
        val name = target.asVirtualFile()?.name
        PyBundle.message("runcfg.test.suggest.name.in.path", testFrameworkName, (name ?: target.target))
      }
      PyRunTargetVariant.PYTHON -> {
        PyBundle.message("runcfg.test.suggest.name.in.python", testFrameworkName, target.target)
      }
      else -> {
        testFrameworkName
      }
    }
  }


  /**
   * @return configuration-specific arguments
   */
  protected open fun getCustomRawArgumentsString(forRerun: Boolean = false): String = ""

  fun reset() {
    target.target = DEFAULT_PATH
    target.targetType = PyRunTargetVariant.PATH
    additionalArguments = ""
  }

  fun copyFrom(src: Properties) {
    src.copyTo(getConfigFields())
  }

  fun copyTo(dst: Properties) {
    getConfigFields().copyTo(dst)
  }


  override fun writeExternal(element: org.jdom.Element) {
    super.writeExternal(element)

    val gson = com.google.gson.Gson()

    getConfigFields().properties.forEach {
      val value = it.get()
      if (value != null) {
        // No need to write null since null is default value
        writeField(element, it.prefixedName, gson.toJson(value))
      }
    }
  }

  override fun readExternal(element: org.jdom.Element) {
    super.readExternal(element)

    val gson = com.google.gson.Gson()

    getConfigFields().properties.forEach {
      val fromJson: Any? = gson.fromJson(readField(element, it.prefixedName), it.getType())
      if (fromJson != null) {
        it.set(fromJson)
      }
    }
  }


  private fun getConfigFields() = getProperties(this, ConfigField::class.java)

  /**
   * Checks if element could be test target for this config.
   * Function is used to create tests by context.
   *
   * If yes, and element is [PsiElement] then it is [PyRunTargetVariant.PYTHON].
   * If file then [PyRunTargetVariant.PATH]
   */
  fun couldBeTestTarget(element: PsiElement): Boolean {

    // TODO: PythonUnitTestUtil logic is weak. We should give user ability to launch test on symbol since user knows better if folder
    // contains tests etc
    val context = TypeEvalContext.userInitiated(element.project, element.containingFile)
    return isTestElement(element, isTestClassRequired(), context)
  }

  /**
   * There are 2 ways to provide target to runner:
   * * As full qname (package1.module1.Class1.test_foo)
   * * As filesystem path (package1/module1.py::Class1.test_foo) full or relative to working directory
   *
   *  Second approach is prefered if this flag is set. It is generally better because filesystem path does not need __init__.py
   */
  internal open fun shouldSeparateTargetPath(): Boolean = true

  /**
   * @param metaInfo String "metainfo" field provided by test runner.
   * Pytest reports test name with parameters here
   */
  open fun setMetaInfo(metaInfo: String) {

  }

  /**
   * @return Boolean if metainfo and target produces same configuration
   */
  open fun isSameAsLocation(target: ConfigurationTarget, metainfo: String?): Boolean = target == this.target
}

abstract class PyAbstractTestFactory<out CONF_T : PyAbstractTestConfiguration>(type: PythonTestConfigurationType)
  : PythonConfigurationFactoryBase(type) {
  abstract override fun createTemplateConfiguration(project: Project): CONF_T

  // Several insances of the same class point to the same factory
  override fun equals(other: Any?): Boolean = ((other as? PyAbstractTestFactory<*>))?.id == id
  override fun hashCode(): Int = id.hashCode()

  /**
   * Only UnitTest inheritors are supported
   */
  abstract fun onlyClassesAreSupported(project: Project, sdk: Sdk): Boolean

  /**
   * Test framework needs package to be installed
   */
  open val packageRequired: String? = null

  open fun isFrameworkInstalled(project: Project, sdk: Sdk): Boolean {
    val requiredPackage = packageRequired ?: return true // No package required
    val isInstalled = PythonPackageManager.forSdk(project, sdk).hasInstalledPackageSnapshot(requiredPackage)
    return isInstalled
  }
}


internal sealed class PyTestTargetForConfig(val configurationTarget: ConfigurationTarget,
                                            val workingDirectory: VirtualFile,
                                            val targetElement: PsiElement) {
  class PyTestPathTarget(target: String, workingDirectory: VirtualFile, targetElement: PsiElement) :
    PyTestTargetForConfig(ConfigurationTarget(target, PyRunTargetVariant.PATH), workingDirectory, targetElement)

  class PyTestPythonTarget(target: String, workingDirectory: VirtualFile, val namePaths: QualifiedNameParts, targetElement: PsiElement) :
    PyTestTargetForConfig(ConfigurationTarget(target, PyRunTargetVariant.PYTHON), workingDirectory, targetElement)
}

/**
 * Only one producer is registered with EP, but it uses factory configured by user to produce different configs
 */
internal class PyTestsConfigurationProducer : AbstractPythonTestConfigurationProducer<PyAbstractTestConfiguration>() {
  companion object {
    /**
     * Creates [ConfigurationTarget] to make  configuration work with provided element.
     * Also reports working dir what should be set to configuration to work correctly and target PsiElement
     * @return [targetPath, workingDirectory, targetPsiElement]
     */
    internal fun getTargetForConfig(configuration: PyAbstractTestConfiguration,
                                    baseElement: PsiElement): PyTestTargetForConfig? {
      var element = baseElement
      // Go up until we reach top of the file
      // asking configuration about each element if it is supported or not
      // If element is supported -- set it as configuration target
      do {
        val isDoctestApplicable = isDoctestApplicable(element, configuration.module)
        if (isDoctestApplicable || configuration.couldBeTestTarget(element)) {
          val target = createPyTestPythonTarget(element, configuration)
          if (target != null) {
            if (isDoctestApplicable) {
              configuration.additionalArguments += DOCTEST_MODULES_ARG
            }
            return target
          }
        }
        element = element.parent ?: break
      }
      while (element !is PsiDirectory) // if parent is folder, then we are at file level
      return null
    }

    private fun createPyTestPythonTarget(element: PsiElement, configuration: PyAbstractTestConfiguration): PyTestTargetForConfig? {
      when (element) {
        is PyQualifiedNameOwner -> { // Function, class, method

          val module = configuration.module ?: return null

          val elementFile = element.containingFile as? PyFile ?: return null
          val workingDirectory = getDirectoryForFileToBeImportedFrom(elementFile, module) ?: return null
          val context = QNameResolveContext(ModuleBasedContextAnchor(module),
                                            evalContext = TypeEvalContext.userInitiated(configuration.project,
                                                                                        null),
                                            folderToStart = workingDirectory.virtualFile)
          val parts = element.tryResolveAndSplit(context) ?: return null
          val qualifiedName = parts.getElementNamePrependingFile(workingDirectory)
          return PyTestTargetForConfig.PyTestPythonTarget(qualifiedName.toString(), workingDirectory.virtualFile, parts, element)
        }
        is PsiFileSystemItem -> {
          val virtualFile = element.virtualFile

          val workingDirectory: VirtualFile = when (element) {
                                                is PyFile -> getDirectoryForFileToBeImportedFrom(element, configuration.module)?.virtualFile
                                                is PsiDirectory -> virtualFile
                                                else -> return null
                                              } ?: return null
          return PyTestTargetForConfig.PyTestPathTarget(virtualFile.path, workingDirectory, element)
        }
        else -> return null
      }
    }

    /**
     * Returns test root for this file. Either it is specified explicitly or calculated using following strategy:
     * Inspect file relative imports, find farthest and return folder with imported file
     */
    private fun getDirectoryForFileToBeImportedFrom(file: PyFile, module: Module?): PsiDirectory? {
      getExplicitlyConfiguredTestRoot(file)?.let {
        return PsiManager.getInstance(file.project).findDirectory(it)
      }

      module?.baseDir?.let {
        return file.manager.findDirectory(it)
      }

      val maxRelativeLevel = file.fromImports.map { it.relativeLevel }.maxOrNull() ?: 0
      var elementFolder = file.parent ?: return null
      for (i in 1..maxRelativeLevel) {
        elementFolder = elementFolder.parent ?: return null
      }
      return elementFolder
    }

    private fun isDoctestApplicable(element: PsiElement, module: Module?): Boolean =
      Registry.`is`("python.run.doctest.via.pytest.configuration") &&
      TestRunnerService.getInstance(module).selectedFactory is PyTestFactory &&
      hasDoctestExpression(element)

    private fun hasDoctestExpression(element: PsiElement): Boolean =
      when (element) {
        is PyFunction -> {
          PythonDocTestUtil.isDocTestFunction(element)
        }
        is PyClass -> {
          PythonDocTestUtil.isDocTestClass(element)
        }
        is PyFile -> {
          PythonDocTestUtil.getDocTestCasesFromFile(element).isNotEmpty()
        }
        else -> false
      }

    private fun isDoctestConfiguration(configuration: RunConfiguration): Boolean =
      configuration.name.contains(DOCTEST_PREFIX) ||
      (configuration as? PyAbstractTestConfiguration)?.additionalArguments?.contains(DOCTEST_MODULES_ARG) == true

    private const val DOCTEST_MODULES_ARG = "--doctest-modules"
    private const val DOCTEST_PREFIX = "Doctest via "
  }

  override fun getConfigurationFactory() = PythonTestConfigurationType.getInstance().configurationFactories[0]

  override val configurationClass: Class<PyAbstractTestConfiguration> = PyAbstractTestConfiguration::class.java

  override fun createLightConfiguration(context: ConfigurationContext): RunConfiguration? {
    val module = context.module ?: return null
    val project = context.project ?: return null
    val configuration =
      findConfigurationFactoryFromSettings(module).createTemplateConfiguration(project) as? PyAbstractTestConfiguration
      ?: return null
    if (!setupConfigurationFromContext(configuration, context, Ref(context.psiLocation))) return null
    return configuration
  }

  override fun cloneTemplateConfiguration(context: ConfigurationContext): RunnerAndConfigurationSettings {
    val module = context.module ?: throw IllegalArgumentException("Module should not be null")
    return cloneTemplateConfigurationStatic(context, findConfigurationFactoryFromSettings(module))
  }

  override fun createConfigurationFromContext(context: ConfigurationContext): ConfigurationFromContext? {
    // Since we need module, no need to even try to create config with out of it
    context.module ?: return null
    return super.createConfigurationFromContext(context)
  }

  // test configuration is always prefered over regular one
  override fun shouldReplace(self: ConfigurationFromContext,
                             other: ConfigurationFromContext): Boolean = other.configuration is PythonRunConfiguration ||
                                                                         isDoctestConfiguration(self.configuration)

  override fun isPreferredConfiguration(self: ConfigurationFromContext?,
                                        other: ConfigurationFromContext): Boolean = other.configuration is PythonRunConfiguration ||
                                                                                    self?.configuration?.let { isDoctestConfiguration(it) } == true

  override fun setupConfigurationFromContext(configuration: PyAbstractTestConfiguration,
                                             context: ConfigurationContext,
                                             sourceElement: Ref<PsiElement>): Boolean {
    val element = sourceElement.get() ?: return false

    if (element.containingFile !is PyFile && element !is PsiDirectory) {
      return false
    }

    val location = context.location
    configuration.module = context.module
    configuration.isUseModuleSdk = true
    if (location is PyTargetBasedPsiLocation) {
      location.target.copyTo(configuration.target)
      location.metainfo?.let { configuration.setMetaInfo(it) }
    }
    else {
      val targetForConfig = getTargetForConfig(configuration, element) ?: return false
      targetForConfig.configurationTarget.copyTo(configuration.target)
      // Directory may be set in Default configuration. In that case no need to rewrite it.
      if (configuration.workingDirectory.isNullOrEmpty()) {
        configuration.workingDirectory = targetForConfig.workingDirectory.path
      }
      else {
        // Template has working directory set
        if (targetForConfig is PyTestTargetForConfig.PyTestPythonTarget) {
          configuration.target.asPsiElement(configuration)?.containingFile?.let { file ->
            val namePaths = targetForConfig.namePaths
            //This working directory affects resolving process making it point to different file
            if (file != namePaths.file && configuration is PyTestConfigurationWithCustomSymbol) {
              /***
               * Use "Custom symbol" ([PyTestConfigurationWithCustomSymbol]) mode
               */
              configuration.target.target = ""
              configuration.target.targetType = PyRunTargetVariant.CUSTOM
              configuration.additionalArguments = configuration.createAdditionalArguments(namePaths)
            }
          }
        }
      }
    }
    configuration.setGeneratedName()
    if (isDoctestConfiguration(configuration)) {
      configuration.name = DOCTEST_PREFIX + configuration.name
    }

    return true
  }

  override fun isConfigurationFromContext(configuration: PyAbstractTestConfiguration, context: ConfigurationContext): Boolean {
    if (PyTestConfigurationSelector.EP.extensionList.find { it.isFromContext(configuration, context) } != null) {
      return true
    }

    val location = context.location
    if (location is PyTargetBasedPsiLocation) {
      // With derived classes several configurations for same element may exist
      return configuration.isSameAsLocation(location.target, location.metainfo)
    }

    val psiElement = context.psiLocation ?: return false
    val configurationFromContext = createConfigurationFromContext(context)?.configuration as? PyAbstractTestConfiguration ?: return false
    if (configuration.target != configurationFromContext.target) {
      return false
    }

    if (configuration.target.targetType == PyRunTargetVariant.CUSTOM) {
      /**
       * Two "custom symbol" ([PyTestConfigurationWithCustomSymbol]) configurations are same when additional args are same
       */
      return (configuration is PyTestConfigurationWithCustomSymbol
              && configuration.additionalArguments == configurationFromContext.additionalArguments
              && configuration.fileSymbolSeparator in configuration.additionalArguments)
    }

    //Even of both configurations have same targets, it could be that both have same qname which is resolved
    // to different elements due to different working folders.
    // Resolve them and check files
    if (configuration.target.targetType != PyRunTargetVariant.PYTHON) return true

    val targetPsi = targetAsPsiElement(configuration.target.targetType, configuration.target.target, configuration,
                                       configuration.getWorkingDirectoryAsVirtual()) ?: return true
    return targetPsi.containingFile == psiElement.containingFile
  }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
/**
 * Mark run configuration field with it to enable saving, restoring and form iteraction
 */
annotation class ConfigField(@param:PropertyKey(resourceBundle = PyBundle.BUNDLE) val localizedName: String)
