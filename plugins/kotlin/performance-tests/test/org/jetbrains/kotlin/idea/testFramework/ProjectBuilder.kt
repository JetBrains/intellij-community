// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.util.io.*
import org.jetbrains.kotlin.idea.perf.util.ProfileTools.Companion.initDefaultProfile
import org.jetbrains.kotlin.idea.testFramework.OpenProject
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction
import org.jetbrains.kotlin.test.KotlinRoot
import java.io.File
import java.nio.file.Files
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

    fun openClass(openClass: Boolean) {
        this.openClass = openClass
    }

    fun dataClass() {
        dataClass(true)
    }

    fun dataClass(dataClass: Boolean) {
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
    internal var buildGradle: String? = null
    internal lateinit var name: String
    internal var initDefaultProfile: Boolean = true
    private val kotlinFiles = mutableListOf<Pair<String, KotlinFileSource>>()

    fun buildGradle(buildGradle: File) {
        val target = if (buildGradle.exists()) buildGradle else KotlinRoot.DIR.resolve(buildGradle)
        this.buildGradle = target.absolutePath
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

    fun kotlinFile(name: String, kotlinFileSource: KotlinFileSource.() -> Unit) {
        val source = KotlinFileSource().apply(kotlinFileSource)
        kotlinFiles.add(Pair(name, source))
    }

    internal fun generateFiles(): String {
        val targetDirectory = Files.createTempDirectory(name)
        Runtime.getRuntime().addShutdownHook(Thread {
            targetDirectory.delete(true)
        })

        val javaMainSrcDir = buildGradle?.let { Paths.get(it) }?.let { buildGradlePath ->
            when {
                buildGradlePath.isFile() -> buildGradlePath.copy(targetDirectory)
                buildGradlePath.isDirectory() -> {
                    val buildGradleFile = listOf("build.gradle.kts", "build.gradle").map { buildGradlePath.resolve(it) }
                        .firstOrNull { it.exists() }
                        ?: error("neither build.gradle.kts nor build.gradle found at $buildGradlePath")

                    buildGradleFile.copy(targetDirectory.resolve(buildGradleFile.fileName))
                }
                else -> error("illegal type of build gradle path: $buildGradlePath")
            }

            targetDirectory.resolve("src/main/java")
        } ?: targetDirectory.resolve("src")

        javaMainSrcDir.createDirectories()
        kotlinFiles.forEach { (name, source) ->
            val srcDir = source.pkg?.let { pkg ->
                val path = javaMainSrcDir.resolve(pkg.replace('.', '/'))
                path.createDirectories()
                path
            } ?: javaMainSrcDir
            srcDir.resolve("$name.kt").toFile().writeText(source.toString())
        }
        //
        return targetDirectory.toRealPath().toString()
    }

    fun openProjectOperation(): OpenProjectOperation {
        val builder = this
        val projectPath = generateFiles()

        val jdkTableImpl = JavaAwareProjectJdkTableImpl.getInstanceEx()
        val homePath = if (jdkTableImpl.internalJdk.homeDirectory!!.name == "jre") {
            jdkTableImpl.internalJdk.homeDirectory!!.parent.path
        } else {
            jdkTableImpl.internalJdk.homePath!!
        }
        val javaSdk = JavaSdk.getInstance()
        val jdk18 = javaSdk.createJdk("1.8", homePath)

        val openAction = if (buildGradle != null) ProjectOpenAction.GRADLE_PROJECT else ProjectOpenAction.SIMPLE_JAVA_MODULE
        val openProject = OpenProject(
            projectPath = projectPath,
            projectName = name,
            jdk = jdk18,
            projectOpenAction = openAction
        )

        return object : OpenProjectOperation {

            override fun openProject() = ProjectOpenAction.openProject(openProject)

            override fun postOpenProject(project: Project) {
                openAction.postOpenProject(project = project, openProject = openProject)
                if (builder.initDefaultProfile) {
                    project.initDefaultProfile()
                }
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