// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.testFramework

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.*
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.perf.util.ProfileTools.Companion.initDefaultProfile
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.test.KotlinRoot
import org.jetbrains.kotlin.utils.addToStdlib.cast
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractSource {
    protected val body = StringBuilder()

    protected open fun newLineAfterCodeBlock(): Boolean = true

    protected open fun intendLevel(): Int = 0

    private fun intendPrefix() = " ".repeat(2 * intendLevel())

    fun body(code: Any) {
        this.body.append(code.toString().prependIndent(intendPrefix()))
        if (newLineAfterCodeBlock()) this.body.append('\n')
    }

    override fun toString(): String = body.toString()
}

abstract class AbstractClassSource(val name: String) : AbstractSource() {
    override fun intendLevel(): Int = 1

    fun function(name: String, funSource: FunSource.() -> Unit) {
        super.body(FunSource(name).apply(funSource))
    }
}

class CompanionObjectSource : AbstractClassSource("") {
    override fun toString(): String = "companion object {\n$body}"
}

enum class Visibility {
    PUBLIC,
    PRIVATE,
    INTERNAL
}

data class Parameter(
    val name: String,
    val type: String,
    val mutable: Boolean = false,
    val visibility: Visibility = Visibility.PUBLIC,
    val defaultValueExpression: String? = null,
) {
    override fun toString(): String {
        val visibilityString = if (visibility == Visibility.PUBLIC) "" else visibility.name.lowercase() + " "
        val valueExpression = defaultValueExpression?.let { " = $it" } ?: ""
        return "$visibilityString${if (mutable) "var" else "val"} $name: $type$valueExpression"
    }
}

class ClassSource(name: String, private val topLevel: Boolean = true) : AbstractClassSource(name) {

    private var dataClass: Boolean = false
    private var openClass: Boolean = false
    private val ctorParameters = mutableListOf<Parameter>()

    private var superClass: String? = null
    private val interfaces = mutableListOf<String>()

    fun superClass(superClass: String) {
        check(this.superClass == null) { "super class is already specified for $name: ${this.superClass}" }
        this.superClass = superClass
    }

    fun interfaces(iface: String) {
        interfaces.add(iface)
    }

    fun openClass() {
        openClass(true)
    }

    private fun openClass(openClass: Boolean) {
        this.openClass = openClass
    }

    fun dataClass() {
        dataClass(true)
    }

    private fun dataClass(dataClass: Boolean) {
        this.dataClass = dataClass
    }

    fun ctorParameter(parameter: Parameter) {
        ctorParameters.add(parameter)
    }

    fun companionObject(companionObjectSource: CompanionObjectSource.() -> Unit) {
        check(topLevel) { "companion object is allowed only in top-level classes, wrong class: $name" }
        val source = CompanionObjectSource().apply(companionObjectSource)
        super.body(source)
    }

    private fun classModifier(): String {
        val open = if (!openClass) "" else "open "
        val data = if (!dataClass) "" else "data "
        return open + data
    }

    private fun superAndInterfaces(): String {
        val superStr = if (superClass != null) {
            ": $superClass()"
        } else ""

        return if (interfaces.isEmpty()) superStr
        else {
            val interfacesStr = interfaces.joinToString()
            if (superStr.isEmpty()) ": $interfacesStr" else "$superStr, $interfacesStr"
        }
    }

    private fun constructorParameters(): String = if (ctorParameters.isEmpty()) {
        ""
    } else {
        "(" + ctorParameters.joinToString(", ") + ")"
    }

    override fun toString(): String = "${classModifier()}class $name ${constructorParameters()} ${superAndInterfaces()} {\n$body}"
}

class FunSource(val name: String) : AbstractSource() {
    private val params = mutableMapOf<String, String>()
    private var openFunction: Boolean = false
    private var returnType: String? = null

    override fun intendLevel(): Int = 1

    fun openFunction() {
        this.openFunction = true
    }

    fun param(name: String, type: String) {
        this.params[name] = type
    }

    fun returnType(returnType: String) {
        this.returnType = returnType
    }

    private fun modifiers(): String {
        val s = if (openFunction) "open" else ""
        return if (s.isEmpty()) s else "$s "
    }

    private fun retType(): String {
        returnType ?: return ""
        return ": $returnType "
    }

    override fun toString(): String =
        "${modifiers()}fun $name(${params.map { it.key + ": " + it.value }.joinToString()}) ${retType()}{\n$body}"
}

class ModuleDescription(val moduleName: String) {
    private val modules = mutableListOf<ModuleDescription>()
    private val libraries = mutableListOf<LibraryDescription>()
    private val kotlinFiles = mutableListOf<Pair<String, KotlinFileSource>>()

    private lateinit var modulePath: Path
    private var src: String = "src"
    private var jdk: Sdk? = null

    fun module(moduleName: String, moduleDescription: ModuleDescription.() -> Unit) {
        val description = ModuleDescription(moduleName).apply(moduleDescription)
        modules.add(description)
    }

    fun jdk(jdk: Sdk) {
        this.jdk = jdk
    }

    fun src(path: String) {
        src = path
    }

    internal fun setUpJdk(jdk: Sdk) {
        if (this.jdk != null) return
        this.jdk = jdk
        modules.forEach {
            it.setUpJdk(jdk)
        }
    }

    fun library(name: String, mavenArtefact: String) {
        libraries.add(MavenLibraryDescription(name, mavenArtefact))
    }

    fun kotlinStandardLibrary() {
        libraries.add(SpecialLibraryDescription("Kotlin Runtime", SpecialLibraryDescription.SpecialLibrary.KOTLIN_STDLIB))
    }

    fun kotlinFile(name: String, kotlinFileSource: KotlinFileSource.() -> Unit) {
        val source = KotlinFileSource().apply(kotlinFileSource)
        kotlinFiles.add(Pair(name, source))
    }

    internal fun generateFiles(path: Path) {
        modulePath = path
        path.createDirectories()

        for (module in modules) {
            val srcDir = path.resolve(module.moduleName)
            module.generateFiles(srcDir)
        }

        val srcDir = path.resolve(src)
        srcDir.createDirectories()

        kotlinFiles.forEach { (name, source) ->
            val packageDir = source.pkg?.let { pkg ->
                val pkgPath = srcDir.resolve(pkg.replace('.', '/'))
                pkgPath.createDirectories()
                pkgPath
            } ?: srcDir
            packageDir.resolve("$name.kt").toFile().writeText(source.toString())
        }
    }

    fun createModule(project: Project) {
        val moduleVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(modulePath) ?: error("unable to find ${modulePath}")
        runWriteAction {
            val moduleManager = ModuleManager.getInstance(project)
            val module = with(moduleManager.modifiableModel) {
                val imlPath = modulePath.resolve("$moduleName${ModuleFileType.DOT_DEFAULT_EXTENSION}")
                val module = newModule(imlPath, ModuleTypeId.JAVA_MODULE)
                PsiTestUtil.addSourceRoot(module, moduleVirtualFile.findFileByRelativePath(src) ?: error("no '$src' in $this"))
                commit()
                module
            }

            ConfigLibraryUtil.configureSdk(module, jdk ?: error("jdk is not specified for $this"))

            for (library in libraries) {
                library.addToModule(project, module)
            }

        }
    }

    override fun toString(): String = "module '${moduleName}'"
}

abstract class LibraryDescription(val name: String, val scope: DependencyScope = DependencyScope.COMPILE) {
    abstract fun addToModule(project: Project, module: Module)
}

class MavenLibraryDescription(name: String, val mavenArtefact: String, scope: DependencyScope = DependencyScope.COMPILE): LibraryDescription(name, scope) {
    override fun addToModule(project: Project, module: Module) {
        val (libraryGroupId, libraryArtifactId, version) = mavenArtefact.split(":").also {
            assert(it.size == 3) { "mavenArtefact is expected to be 'groupId:artifactId:version', actual is '$mavenArtefact'" }
        }
        val libraryProperties = RepositoryLibraryProperties(libraryGroupId, libraryArtifactId, version, true, emptyList())
        val orderRoots = JarRepositoryManager.loadDependenciesModal(project, libraryProperties, false, false, null, null)

        ConfigLibraryUtil.addLibrary(module, name, kind = REPOSITORY_LIBRARY_KIND) {
            val modifiableLibrary = cast<LibraryEx.ModifiableModelEx>()
            val realLibraryProperties = modifiableLibrary.properties.cast<RepositoryLibraryProperties>()
            realLibraryProperties.mavenId = libraryProperties.mavenId
            modifiableLibrary.properties = realLibraryProperties
            for (orderRoot in orderRoots) {
                addRoot(orderRoot.file, orderRoot.type)
            }
        }
    }

    override fun toString(): String {
        return "Maven library '$mavenArtefact'"
    }
}
class SpecialLibraryDescription(name: String, private val library: SpecialLibrary, scope: DependencyScope = DependencyScope.COMPILE): LibraryDescription(name, scope) {
    override fun addToModule(project: Project, module: Module) {
        when(library) {
            SpecialLibrary.KOTLIN_STDLIB ->
                ConfigLibraryUtil.addLibrary(module, name) {
                    addRoot(KotlinArtifacts.instance.kotlinStdlib, OrderRootType.CLASSES)
                }
        }
    }

    enum class SpecialLibrary {
        KOTLIN_STDLIB
    }
}

class KotlinFileSource : AbstractSource() {

    internal var pkg: String? = null

    fun pkg(pkg: String) {
        check(this.pkg == null) { "package directive is already specified: ${this.pkg}" }
        this.pkg = pkg
        super.body("package $pkg\n")
    }

    fun import(fqName: String) {
        super.body("import $fqName\n")
    }

    fun importStar(pkgName: String) {
        super.body("import $pkgName.*\n")
    }

    fun topClass(name: String, clsSource: ClassSource.() -> Unit) {
        super.body(ClassSource(name).apply(clsSource))
    }

    fun topFunction(name: String, funSource: FunSource.() -> Unit) {
        super.body(FunSource(name).apply(funSource))
    }
}

class ProjectBuilder {
    internal lateinit var name: String
    internal var initDefaultProfile: Boolean = true
    private var buildGradleKts: String? = null

    private val modules = mutableListOf<ModuleDescription>()

    fun module(moduleName: String = ProjectOpenAction.SIMPLE_JAVA_MODULE.name, moduleDescription: ModuleDescription.() -> Unit) {
        val description = ModuleDescription(moduleName).apply(moduleDescription)
        modules.add(description)
    }

    fun name(name: String) {
        this.name = name
    }

    fun initDefaultProfile() {
        this.initDefaultProfile = true
    }

    fun initDefaultProfile(initDefaultProfile: Boolean) {
        this.initDefaultProfile = initDefaultProfile
    }

    fun buildGradleKtsTemplate(buildGradle: File) {
        val target = if (buildGradle.exists()) buildGradle else KotlinRoot.DIR.resolve(buildGradle)
        this.buildGradleKts = target.absolutePath
    }

    private fun setUpJdk(jdk: Sdk) {
        modules.forEach {
            it.setUpJdk(jdk)
        }
    }

    private fun generateFiles(): Path {
        val projectPath = Files.createTempDirectory(name)!!
        Runtime.getRuntime().addShutdownHook(Thread {
            projectPath.delete(true)
        })
        val buildGradleKtsPath = buildGradleKts?.let { Paths.get(it) }
        buildGradleKtsPath?.let { buildGradlePath ->
            when {
                buildGradlePath.isFile() -> buildGradlePath.copy(projectPath)
                buildGradlePath.isDirectory() -> {
                    val buildGradleFile = listOf("build.gradle.kts", "build.gradle").map { buildGradlePath.resolve(it) }
                        .firstOrNull { it.exists() }
                        ?: error("neither build.gradle.kts nor build.gradle found at $buildGradlePath")

                    buildGradleFile.copy(projectPath.resolve(buildGradleFile.fileName))
                }
                else -> error("illegal type of build gradle path: $buildGradlePath")
            }
        }

        if (buildGradleKtsPath != null) {
            val module = modules.singleOrNull() ?: error("Gradle model supports only a single module")
            module.generateFiles(projectPath.resolve("src/main/java"))
        } else {
            when(modules.size) {
                1 -> {
                    modules[0].generateFiles(projectPath)
                }
                else -> {
                    for (module in modules) {
                        val moduleDir = projectPath.resolve(module.moduleName)
                        module.generateFiles(moduleDir)
                    }
                }
            }

        }
        return projectPath
    }

    private fun createModules(project: Project) {
        if (buildGradleKts != null) return
        for (module in modules) {
            module.createModule(project)
        }
    }

    fun openProjectOperation(): OpenProjectOperation {
        val builder = this

        val jdkTableImpl = JavaAwareProjectJdkTableImpl.getInstanceEx()
        val homePath = if (jdkTableImpl.internalJdk.homeDirectory!!.name == "jre") {
            jdkTableImpl.internalJdk.homeDirectory!!.parent.path
        } else {
            jdkTableImpl.internalJdk.homePath!!
        }
        val javaSdk = JavaSdk.getInstance()
        val jdk18 = javaSdk.createJdk("1.8", homePath)

        setUpJdk(jdk18)
        val projectPath = generateFiles()

        val openAction =
            when {
                buildGradleKts != null -> ProjectOpenAction.GRADLE_PROJECT
                modules.size == 1 -> ProjectOpenAction.SIMPLE_JAVA_MODULE
                modules.size > 1 -> ProjectOpenAction.EXISTING_IDEA_PROJECT
                else -> error("at least one module has to be specified")
            }
        val openProject = OpenProject(
            projectPath = projectPath.toRealPath().toString(),
            projectName = name,
            jdk = jdk18,
            projectOpenAction = openAction
        )

        return object : OpenProjectOperation {
            override fun openProject() = ProjectOpenAction.openProject(openProject)

            override fun postOpenProject(project: Project) {
                createModules(project)

                openAction.postOpenProject(project = project, openProject = openProject)
                if (builder.initDefaultProfile) {
                    project.initDefaultProfile()
                }

                PlatformTestUtil.saveProject(project, true)
            }

        }
    }
}

interface OpenProjectOperation {
    fun openProject(): Project

    fun postOpenProject(project: Project)
}

fun openProject(initializer: ProjectBuilder.() -> Unit): Project {
    val projectBuilder = ProjectBuilder().apply(initializer)
    val openProject = projectBuilder.openProjectOperation()

    return openProject.openProject().also {
        openProject.postOpenProject(it)
    }
}