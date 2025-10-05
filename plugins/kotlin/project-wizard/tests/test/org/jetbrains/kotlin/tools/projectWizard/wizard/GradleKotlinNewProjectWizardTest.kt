// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.KOTLIN
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.withProjectAsync
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.annotations.CsvCrossProductSource
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.io.path.walk

class GradleKotlinNewProjectWizardTest : GradleKotlinNewProjectWizardTestCase() {

    @ParameterizedTest
    @EnumSource(GradleDsl::class)
    fun testSimpleProject(gradleDsl: GradleDsl): Unit = runBlocking {
        createProjectByWizard(KOTLIN) {
            setGradleWizardData("project", gradleDsl = gradleDsl)
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                withKotlinBuildFile()
                withKotlinSettingsFile()
            })
        }.closeProjectAsync()
    }

    @Test
    @RegistryKey("gradle.daemon.jvm.criteria.new.project", "true")
    fun testSimpleProjectUsingDaemonJvmCriteria(): Unit = runBlocking {
        createProjectByWizard(KOTLIN) {
            setGradleWizardData("project", gradleDsl = GradleDsl.KOTLIN)
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", GradleDsl.KOTLIN) {
                withKotlinBuildFile()
                withKotlinSettingsFile()
            })
            assertDaemonJvmProperties(project)
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @CsvCrossProductSource("KOTLIN,GROOVY", "true,false")
    fun testMultiModuleProject(gradleDsl: GradleDsl, addSampleCode: Boolean): Unit = runBlocking {
        createProjectByWizard(KOTLIN) {
            setGradleWizardData("project", gradleDsl = gradleDsl, addSampleCode = addSampleCode, generateMultipleModules = true)
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                if (gradleDsl == GradleDsl.KOTLIN) {
                    modulesPerSourceSet.clear() // no build script for a root module
                    moduleInfo("project.app", "app")
                    moduleInfo("project.utils", "utils")
                    moduleInfo("project.buildSrc", "buildSrc")
                }
            })
        }.withProjectAsync { project ->
            val hasKotlinFiles = project.projectRoot.walk()
                .any { it.extension == "kt" && !it.pathString.contains("project/buildSrc/build/generated-sources/") }
            if (addSampleCode) {
                Assertions.assertTrue(hasKotlinFiles) {
                    "Project with sample code should contain Kotlin files"
                }
            } else {
                Assertions.assertFalse(hasKotlinFiles) {
                    "Project without sample code should not contain Kotlin files"
                }
            }
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @CsvCrossProductSource("KOTLIN,GROOVY", "true,false")
    fun testSampleCode(gradleDsl: GradleDsl, renderedTips: Boolean): Unit = runBlocking {
        Registry.get("doc.onboarding.tips.render").setValue(renderedTips, asDisposable())
        createProjectByWizard(KOTLIN) {
            setGradleWizardData("project", gradleDsl = gradleDsl, addSampleCode = true)
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                withKotlinBuildFile()
                withKotlinSettingsFile()
            })
        }.withProjectAsync {
            // The onboarding tips have to be handled a bit more manually because the rendered text depends on the OS of the system
            // because shortcuts are OS specific
            val mainFileContent = getMainFileContent("project")
            Assertions.assertTrue(mainFileContent.contains(ONBOARDING_TIPS_SEARCH_STR)) {
                "Main file did not contain onboarding tips"
            }
            Assertions.assertEquals(renderedTips, mainFileContent.contains("//TIP")) {
                "Main file should contain rendered onboarding tips"
            }
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @CsvCrossProductSource("KOTLIN,GROOVY", "KOTLIN,GROOVY")
    fun testNewModuleInJavaProject(gradleDslInJava: GradleDsl, gradleDslInKotlin: GradleDsl): Unit = runBlocking {
        initProject(projectInfo("project", gradleDslInJava) {
            withJavaBuildFile()
            withKotlinSettingsFile()
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                val projectNode = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, project.projectPath)!!
                setGradleWizardData("module", "project/module", gradleDslInKotlin, parentData = projectNode.data)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDslInJava) {
                withJavaBuildFile()
                withKotlinSettingsFile { include("module") }
                moduleInfo("project.module", "module", gradleDslInKotlin) {
                    withKotlinBuildFile()
                }
            })
        }.withProjectAsync { project ->
            Assertions.assertFalse(project.projectRoot.resolve("module/gradle.properties").exists()) {
                "Gradle properties file should not exist in modules"
            }
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @EnumSource(GradleDsl::class)
    fun testNewModuleInKotlinProject(gradleDsl: GradleDsl): Unit = runBlocking {
        initProject(projectInfo("project", gradleDsl) {
            withKotlinBuildFile()
            withKotlinSettingsFile()
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                val projectNode = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, project.projectPath)!!
                setGradleWizardData("module", "project/module", gradleDsl, parentData = projectNode.data)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                withKotlinBuildFile()
                withKotlinSettingsFile { include("module") }
                moduleInfo("project.module", "module") {
                    withKotlinBuildFile(kotlinJvmPluginVersion = null)
                }
            })
        }.withProjectAsync { project ->
            Assertions.assertFalse(project.projectRoot.resolve("module/gradle.properties").exists()) {
                "Gradle properties file should not exist in modules"
            }
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @EnumSource(GradleDsl::class)
    fun testNewModuleInKotlinProjectIndependentHierarchy(gradleDsl: GradleDsl): Unit = runBlocking {
        initProject(projectInfo("project", gradleDsl) {
            withKotlinBuildFile()
            withKotlinSettingsFile()
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                setGradleWizardData("module", "project/module", gradleDsl, parentData = null)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                withKotlinBuildFile()
                withKotlinSettingsFile()
            }, projectInfo("project/module", gradleDsl) {
                withKotlinBuildFile()
                withKotlinSettingsFile()
            })
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @EnumSource(GradleDsl::class)
    fun testNoMultiModuleProjectForNewModules(gradleDsl: GradleDsl): Unit = runBlocking {
        initProject(projectInfo("project", gradleDsl) {
            withKotlinBuildFile()
            withKotlinSettingsFile()
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                val projectNode = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, project.projectPath)!!
                setGradleWizardData("module", "project/module", gradleDsl, parentData = projectNode.data)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                withKotlinBuildFile()
                withKotlinSettingsFile { include("module") }
                moduleInfo("project.module", "module") {
                    withKotlinBuildFile(kotlinJvmPluginVersion = null)
                }
            })
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @EnumSource(GradleDsl::class)
    fun testOtherKotlinModule(gradleDsl: GradleDsl): Unit = runBlocking {
        val kotlinJvmPluginVersion = "1.9.25"
        initProject(projectInfo("project", gradleDsl) {
            withJavaBuildFile()
            withKotlinSettingsFile {
                include("other_module")
            }
            moduleInfo("project.other_module", "other_module") {
                withKotlinBuildFile(kotlinJvmPluginVersion)
            }
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                val projectNode = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, project.projectPath)!!
                setGradleWizardData("module", "project/module", gradleDsl, parentData = projectNode.data)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                withJavaBuildFile()
                withKotlinSettingsFile {
                    include("other_module")
                    include("module")
                }
                moduleInfo("project.other_module", "other_module") {
                    withKotlinBuildFile(kotlinJvmPluginVersion)
                }
                moduleInfo("project.module", "module") {
                    withKotlinBuildFile(kotlinJvmPluginVersion)
                }
            })
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @CsvCrossProductSource("KOTLIN,GROOVY", "true,false")
    fun testNewModuleWithVersionCatalog(gradleDsl: GradleDsl, addBuildSrcVersionCatalogDependency: Boolean): Unit = runBlocking {
        val kotlinJvmPluginVersion = "2.2.0"
        val versionTomlContent = """
            |[versions]
            |kotlin = "$kotlinJvmPluginVersion"
            |
            |[libraries]
            |kotlinGradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
         """.trimMargin()
        val versionCatalogContent = """
            |dependencyResolutionManagement {
            |
            |    // Use Maven Central and Gradle Plugin Portal for resolving dependencies in the shared build logic ("buildSrc") project
            |    @Suppress("UnstableApiUsage")
            |    repositories {
            |        mavenCentral()
            |    }
            |
            |    // Re-use the version catalog from the main build
            |    versionCatalogs {
            |        create("libs") {
            |            from(files("../gradle/libs.versions.toml"))
            |        }
            |    }
            |}
        """.trimMargin()
        initProject(projectInfo("project") {
            withFile("gradle/libs.versions.toml", versionTomlContent)
            moduleInfo("project.buildSrc", "buildSrc") {
                withBuildFile {
                    withKotlinDsl()
                    if (addBuildSrcVersionCatalogDependency) {
                        addImplementationDependency(code("libs.kotlinGradlePlugin"))
                    }
                }
                withSettingsFile {
                    addCode(versionCatalogContent)
                }
            }
            modulesPerSourceSet.clear() // no build script for a root module
            withKotlinSettingsFile()
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                val projectNode = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, project.projectPath)!!
                setGradleWizardData("module", "project/module", gradleDsl, parentData = projectNode.data)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project") {
                withFile("gradle/libs.versions.toml", versionTomlContent)
                moduleInfo("project.buildSrc", "buildSrc") {
                    withSettingsFile {
                        addCode(versionCatalogContent)
                    }
                    withBuildFile {
                        withKotlinDsl()
                        if (addBuildSrcVersionCatalogDependency) {
                            addImplementationDependency(code("libs.kotlinGradlePlugin"))
                        }
                    }
                }
                modulesPerSourceSet.clear() // no build script for a root module
                withKotlinSettingsFile { include("module") }
                moduleInfo("project.module", "module", gradleDsl) {
                    if (addBuildSrcVersionCatalogDependency) {
                        // It should not specify an explicit version because it is defined in the version catalog
                        withKotlinBuildFile(kotlinJvmPluginVersion = null)
                    } else {
                        withKotlinBuildFile(kotlinJvmPluginVersion = kotlinJvmPluginVersion)
                    }
                }
            })
        }.closeProjectAsync()
    }
}