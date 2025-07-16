// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.kotlinBuildSystemData
import org.jetbrains.kotlin.tools.projectWizard.gradle.GradleKotlinNewProjectWizardData.Companion.kotlinGradleData
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.setup.GradleNewProjectWizardTestCase
import org.jetbrains.plugins.gradle.testFramework.util.ModuleInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

abstract class GradleKotlinNewProjectWizardTestCase : GradleNewProjectWizardTestCase() {

    @AfterEach
    fun tearDown() {
        runAll({ KotlinSdkType.removeKotlinSdkInTests() })
    }

    val Project.projectPath: String get() = basePath!!
    val Project.projectRoot: Path get() = Path.of(projectPath)

    fun NewProjectWizardStep.setGradleWizardData(
        name: String,
        path: String = name,
        gradleDsl: GradleDsl = GradleDsl.GROOVY,
        addSampleCode: Boolean = false,
        generateMultipleModules: Boolean = false,
        parentData: ProjectData? = null,
        groupId: String = "org.example",
        artifactId: String = name,
        version: String = "1.0-SNAPSHOT",
    ) {
        baseData!!.name = name
        baseData!!.path = testPath.resolve(path).normalize().parent.toCanonicalPath()
        kotlinBuildSystemData!!.buildSystem = GRADLE
        kotlinGradleData!!.gradleDsl = gradleDsl
        kotlinGradleData!!.addSampleCode = addSampleCode
        kotlinGradleData!!.generateMultipleModules = generateMultipleModules
        kotlinGradleData!!.parentData = parentData
        kotlinGradleData!!.groupId = groupId
        kotlinGradleData!!.artifactId = artifactId
        kotlinGradleData!!.version = version
    }

    fun ModuleInfo.Builder.withKotlinSettingsFile(configure: GradleSettingScriptBuilder<*>.() -> Unit = {}) {
        withSettingsFile {
            withFoojayPlugin()
            setProjectName(name)
            configure()
        }
    }

    fun ModuleInfo.Builder.withKotlinBuildFile(
        kotlinJvmPluginVersion: String? = "2.2.10",
        configure: GradleBuildScriptBuilder<*>.() -> Unit = {}
    ) {
        withBuildFile {
            addGroup(groupId)
            addVersion(version)
            withKotlinJvmPlugin(kotlinJvmPluginVersion)
            withKotlinTest()
            withKotlinJvmToolchain(gradleJvmInfo.version.feature)
            configure()
        }
    }

    fun ModuleInfo.Builder.withJavaBuildFile() {
        withBuildFile {
            addGroup(groupId)
            addVersion(version)
            withJavaPlugin()
            withJUnit()
        }
    }

    fun getMainFileContent(modulePath: String): String {
        val path = testPath.resolve(modulePath).resolve("src/main/kotlin/Main.kt")
        Assertions.assertTrue(path.exists()) {
            "Could not find Build file at $path"
        }
        return path.readText()
    }
}