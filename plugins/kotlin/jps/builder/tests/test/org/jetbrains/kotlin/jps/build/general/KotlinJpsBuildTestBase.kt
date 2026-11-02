// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build.general

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.URLUtil
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.builders.BuildResult
import org.jetbrains.jps.builders.CompileScopeTestBuilder
import org.jetbrains.jps.builders.JpsBuildTestBase
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.incremental.BuilderRegistry
import org.jetbrains.jps.incremental.IncProjectBuilder
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.jetbrains.kotlin.jps.build.KotlinBtaImplementation
import org.jetbrains.kotlin.jps.build.KotlinBuilder
import org.jetbrains.kotlin.jps.build.KotlinJpsTestData
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.io.File

/**
 * The base of the project tests over `testData/general` of the Kotlin repository.
 *
 * A test takes the project directory named after the test, or after [WorkingDir]. The directory comes from
 * [KotlinJpsTestData], so the overlay of this module can replace any of its files.
 */
abstract class KotlinJpsBuildTestBase : JpsBuildTestBase() {
  protected lateinit var workDir: File
  private lateinit var relativePath: String

  /** The project directory of the test, when it differs from the test name. */
  @Target(AnnotationTarget.FUNCTION)
  annotation class WorkingDir(val name: String)

  /** The implementation that the projects compile with. A subclass runs the same tests with another one. */
  protected open val btaImplementation: KotlinBtaImplementation
    get() = KotlinBtaImplementation.CURRENT

  override fun shouldRunTest(): Boolean = super.shouldRunTest() && !btaImplementation.duplicatesCurrent

  enum class LibraryDependency {
    NONE,
    JVM_MOCK_RUNTIME,
    JVM_FULL_RUNTIME,
  }

  @BeforeEach
  fun setUpKotlinJpsBuildTest(testInfo: TestInfo) {
    System.setProperty(GlobalOptions.DEPENDENCY_GRAPH_ENABLED, "true")
    System.setProperty(IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY, "true")
    btaImplementation.install()

    val workingDir = testInfo.testMethod.get().getAnnotation(WorkingDir::class.java)?.name ?: getTestName(false)
    relativePath = "general/$workingDir"
    workDir = File(createTempDirectory("kotlin-jps-build"), workingDir)
    KotlinJpsTestData.materialize(relativePath, workDir.toPath())
    getOrCreateProjectDir()
  }

  override fun doGetProjectDir(): File = workDir

  /** A JDK of the test JVM with `jrt://` roots, as the IDE registers a modular JDK. */
  override fun addJdk(name: String, jdkClassesRoot: String?): JpsSdk<JpsDummyElement> {
    val javaHome = FileUtil.toSystemIndependentName(System.getProperty("java.home"))
    val jdk = myModel.global.addSdk(name, javaHome, System.getProperty("java.version"), JpsJavaSdkType.INSTANCE)
    jdk.addRoot(URLUtil.JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR + javaHome + URLUtil.JAR_SEPARATOR + "java.base", JpsOrderRootType.COMPILED)
    return jdk.properties
  }

  protected fun initProject(libraryDependency: LibraryDependency = LibraryDependency.NONE) {
    addJdk(JDK_NAME)
    loadProject(workDir.absolutePath + File.separator + PROJECT_NAME + ".ipr")

    when (libraryDependency) {
      LibraryDependency.NONE -> {}
      LibraryDependency.JVM_MOCK_RUNTIME, LibraryDependency.JVM_FULL_RUNTIME -> addKotlinStdlibDependency()
    }
  }

  protected fun addKotlinStdlibDependency(modules: Collection<JpsModule> = myProject.modules, exported: Boolean = false): JpsLibrary =
    addDependency(JpsJavaDependencyScope.COMPILE, modules, exported, "kotlin-stdlib", *btaImplementation.stdlibRoots.toTypedArray())

  protected fun addDependency(
    scope: JpsJavaDependencyScope,
    modules: Collection<JpsModule>,
    exported: Boolean,
    libraryName: String,
    vararg roots: File,
  ): JpsLibrary {
    val library = myProject.addLibrary(libraryName, JpsJavaLibraryType.INSTANCE)
    for (root in roots) {
      library.addRoot(root.toPath(), JpsOrderRootType.COMPILED)
    }
    for (module in modules) {
      val dependency = module.dependenciesList.addLibraryDependency(library)
      JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).apply {
        this.scope = scope
        isExported = exported
      }
    }
    return library
  }

  protected fun findModule(name: String): JpsModule =
    myProject.modules.firstOrNull { it.name == name } ?: throw IllegalStateException("Couldn't find module $name")

  protected fun buildCustom(canceledStatus: CanceledStatus, logger: ProjectBuilderLogger, buildResult: BuildResult) {
    val descriptor = createProjectDescriptor(BuildLoggingManager(logger))
    try {
      val builder = IncProjectBuilder(descriptor, BuilderRegistry.getInstance(), myBuildParams, canceledStatus, true)
      builder.addMessageHandler(buildResult)
      builder.build(CompileScopeTestBuilder.make().allModules().build(), false)
    }
    finally {
      descriptor.dataManager.flush(false)
      descriptor.release()
    }
  }

  /** Compares the errors of the build with `errors.txt` of the project directory. */
  protected fun BuildResult.checkErrors() {
    val actual = getMessages(BuildMessage.Kind.ERROR)
      .map { it as CompilerMessage }
      .map { "${it.messageText} at line ${it.line}, column ${it.column}" }
      .sorted()
      .joinToString("\n")
    KotlinJpsTestData.assertGolden(
      relativePath = relativePath,
      caseDir = workDir.toPath(),
      names = KotlinJpsTestData.goldenNames(ERRORS_FILE, btaImplementation.compilerVersion, listOf(ERRORS_FILE)),
      overwriteName = ERRORS_FILE,
      actual = actual,
    )
  }

  protected fun checkWhen(action: Action, pathsToCompile: Array<String>?, pathsToDelete: Array<String>?) {
    checkWhen(arrayOf(action), pathsToCompile, pathsToDelete)
  }

  protected fun checkWhen(actions: Array<Action>, pathsToCompile: Array<String>?, pathsToDelete: Array<String>?) {
    for (action in actions) {
      action.apply()
    }

    buildAllModules().assertSuccessful()

    if (pathsToCompile != null) {
      assertCompiled(KotlinBuilder.KOTLIN_BUILDER_NAME, *pathsToCompile)
    }
    if (pathsToDelete != null) {
      assertDeleted(*pathsToDelete)
    }
  }

  protected fun createTouchAction(path: String): Action = Action.Touch(File(workDir, path).absolutePath)

  protected fun createDeleteAction(path: String): Action = Action.Delete(File(workDir, path).absolutePath)

  sealed class Action(val path: String) {
    abstract fun apply()

    protected fun checkPath() {
      assertTrue(File(path).exists(), "$path does not exist")
    }

    class Touch(path: String) : Action(path) {
      override fun apply() {
        checkPath()
        change(path)
      }
    }

    class Delete(path: String) : Action(path) {
      override fun apply() {
        checkPath()
        assertTrue(File(path).delete(), "Cannot delete $path")
      }
    }
  }

  companion object {
    const val JDK_NAME: String = "IDEA_JDK"
    const val PROJECT_NAME: String = "kotlinProject"
    private const val ERRORS_FILE = "errors.txt"

    @JvmStatic
    protected fun klass(moduleName: String, classFqName: String): String =
      "out/production/$moduleName/" + classFqName.replace('.', '/') + ".class"

    @JvmStatic
    protected fun module(moduleName: String): String = "out/production/$moduleName/META-INF/$moduleName.kotlin_module"

    /**
     * The file facade of [fileName], in the package of [packageClassFqName]. The rule is the one of
     * `PackagePartClassUtils.getDefaultPartFqName`.
     */
    @JvmStatic
    protected fun packagePartClass(moduleName: String, fileName: String, packageClassFqName: String): String {
      val packageName = packageClassFqName.substringBeforeLast('.', "")
      val shortName = fileName.substringAfterLast('/').substringBeforeLast('.')
        .map { if (it.isJavaIdentifierPart()) it else '_' }
        .joinToString("")
        .replaceFirstChar { it.uppercaseChar() } + "Kt"
      return klass(moduleName, if (packageName.isEmpty()) shortName else "$packageName.$shortName")
    }

    @JvmStatic
    protected fun packageClasses(moduleName: String, fileName: String, packageClassFqName: String): Array<String> =
      arrayOf(module(moduleName), packagePartClass(moduleName, fileName, packageClassFqName))

    @JvmStatic
    protected fun findFileInOutputDir(module: JpsModule, relativePath: String): File {
      val outputUrl = JpsJavaExtensionService.getInstance().getOutputUrl(module, false)
      assertNotNull(outputUrl)
      return File(JpsPathUtil.urlToPath(outputUrl), relativePath)
    }

    @JvmStatic
    protected fun assertFilesExistInOutput(module: JpsModule, vararg relativePaths: String) {
      for (path in relativePaths) {
        val outputFile = findFileInOutputDir(module, path)
        assertTrue(outputFile.exists(), "Output not written: ${outputFile.absolutePath}\n Directory contents: \n${dirContents(outputFile.parentFile)}")
      }
    }

    @JvmStatic
    protected fun assertFilesNotExistInOutput(module: JpsModule, vararg relativePaths: String) {
      for (path in relativePaths) {
        val outputFile = findFileInOutputDir(module, path)
        assertFalse(outputFile.exists(), "Output directory \"${outputFile.absolutePath}\" contains \"$path\"")
      }
    }

    private fun dirContents(dir: File): String =
      dir.listFiles()?.joinToString("") { " * ${it.name}\n" } ?: "<not found>"
  }
}
