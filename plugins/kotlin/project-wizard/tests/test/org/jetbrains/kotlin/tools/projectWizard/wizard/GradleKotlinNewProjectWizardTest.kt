// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.KOTLIN
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.test.compileModules
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.withProjectAsync
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.getCurrentProcessJdkHome
import org.jetbrains.kotlin.tools.projectWizard.gradle.isLessOrEqualToMaxJvmTarget
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.annotations.CsvCrossProductSource
import org.jetbrains.plugins.gradle.testFramework.projectInfo.buildFile
import org.jetbrains.plugins.gradle.testFramework.projectInfo.file
import org.jetbrains.plugins.gradle.testFramework.projectInfo.settingsFile
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleJavaRootModuleInfo
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.io.path.walk

class GradleKotlinNewProjectWizardTest : GradleKotlinNewProjectWizardTestCase() {
    private fun Project.compileModules(vararg moduleNames: String) {
        assertModules(this, *moduleNames)
        compileModules(this, true, *moduleNames)
    }

    private fun lookupAndRegisterExternalSystemJdk(project: Project) {
        val processJdkHome = getCurrentProcessJdkHome()
        ExternalSystemJdkUtil.findJdkInSdkTableByPath(project, processJdkHome.path)?.let { sdk ->
            val table = ProjectJdkTable.getInstance()
            Disposer.register(parentDisposable, Disposable {
                WriteAction.computeAndWait(ThrowableComputable {
                    table.removeJdk(sdk)
                })
            })
        }
    }

    @ParameterizedTest
    @EnumSource(GradleDsl::class)
    fun testSimpleProject(gradleDsl: GradleDsl): Unit = runBlocking {
        createProjectByWizard(KOTLIN) {
            setGradleWizardData("project", gradleDsl = gradleDsl)
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                simpleKotlinSettingsFile()
                simpleKotlinRootModuleInfo()
            })
            project.compileModules("project", "project.main", "project.test")
        }.closeProjectAsync()
    }

    @Test
    @RegistryKey("gradle.daemon.jvm.criteria.new.project", "true")
    fun testSimpleProjectUsingDaemonJvmCriteria(): Unit = runBlocking {
        createProjectByWizard(KOTLIN) {
            setGradleWizardData("project", gradleDsl = GradleDsl.KOTLIN)
        }.withProjectAsync { project ->
            lookupAndRegisterExternalSystemJdk(project)

            assertProjectState(project, projectInfo("project", GradleDsl.KOTLIN) {
                simpleKotlinSettingsFile()
                simpleKotlinRootModuleInfo()
            })
            assertDaemonJvmProperties(project)
            project.compileModules("project", "project.main", "project.test")
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @CsvCrossProductSource("KOTLIN", "true,false")
    fun testMultiModuleProject(gradleDsl: GradleDsl, addSampleCode: Boolean): Unit = runBlocking {
        createProjectByWizard(KOTLIN) {
            setGradleWizardData("project", gradleDsl = gradleDsl, addSampleCode = addSampleCode, generateMultipleModules = true)
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                assertEquals(GradleDsl.KOTLIN, gradleDsl, "only Kotlin DSL multi-module project is supported")

                moduleInfo("project.app", "app") {
                    sourceSetInfo("main")
                    sourceSetInfo("test")
                }
                moduleInfo("project.utils", "utils") {
                    sourceSetInfo("main")
                    sourceSetInfo("test")
                }
                moduleInfo("project.buildSrc", "buildSrc") {
                    sourceSetInfo("main")
                    sourceSetInfo("test")
                }
            })
            project.compileModules(
                "project",
                "project.app", "project.app.main", "project.app.test",
                "project.utils", "project.utils.main", "project.utils.test",
                "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test")
        }.withProjectAsync { project ->
            val hasKotlinFiles = project.projectRoot.walk()
                .any { it.extension == "kt" && !it.pathString.contains("project/buildSrc/build/generated-sources/") }
            if (addSampleCode) {
                assertTrue(hasKotlinFiles) {
                    "Project with sample code should contain Kotlin files"
                }
            } else {
                assertFalse(hasKotlinFiles) {
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
                simpleKotlinSettingsFile()
                simpleKotlinRootModuleInfo()
            })
            project.compileModules("project", "project.main", "project.test")
        }.withProjectAsync {
            // The onboarding tips have to be handled a bit more manually because the rendered text depends on the OS of the system
            // because shortcuts are OS specific
            val mainFileContent = getMainFileContent("project")
            assertTrue(mainFileContent.contains(ONBOARDING_TIPS_SEARCH_STR)) {
                "Main file did not contain onboarding tips"
            }
            assertEquals(renderedTips, mainFileContent.contains("//TIP")) {
                "Main file should contain rendered onboarding tips"
            }
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @CsvCrossProductSource("KOTLIN,GROOVY", "KOTLIN,GROOVY")
    fun testNewModuleInJavaProject(gradleDslInJava: GradleDsl, gradleDslInKotlin: GradleDsl): Unit = runBlocking {
        initProject(projectInfo("project", gradleDslInJava) {
            simpleKotlinSettingsFile()
            simpleJavaRootModuleInfo()
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                val projectNode = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, project.projectPath)!!
                setGradleWizardData("module", "project/module", gradleDslInKotlin, parentData = projectNode.data)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDslInJava) {
                simpleKotlinSettingsFile {
                    include("module")
                }
                simpleJavaRootModuleInfo()
                simpleKotlinModuleInfo("project.module", "module", gradleDslInKotlin)
            })
            project.compileModules(
                "project", "project.main", "project.test",
                "project.module", "project.module.main", "project.module.test"
            )
        }.withProjectAsync { project ->
            assertFalse(project.projectRoot.resolve("module/gradle.properties").exists()) {
                "Gradle properties file should not exist in modules"
            }
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @EnumSource(GradleDsl::class)
    fun testNewModuleInKotlinProject(gradleDsl: GradleDsl): Unit = runBlocking {
        initProject(projectInfo("project", gradleDsl) {
            simpleKotlinSettingsFile()
            simpleKotlinRootModuleInfo()
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                val projectNode = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, project.projectPath)!!
                setGradleWizardData("module", "project/module", gradleDsl, parentData = projectNode.data)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                simpleKotlinSettingsFile {
                    include("module")
                }
                simpleKotlinRootModuleInfo()
                simpleKotlinModuleInfo("project.module", "module", kotlinJvmPluginVersion = null)
            })
            project.compileModules(
                "project", "project.main", "project.test",
                "project.module", "project.module.main", "project.module.test"
            )
        }.withProjectAsync { project ->
            assertFalse(project.projectRoot.resolve("module/gradle.properties").exists()) {
                "Gradle properties file should not exist in modules"
            }
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @EnumSource(GradleDsl::class)
    fun testNewModuleInKotlinProjectIndependentHierarchy(gradleDsl: GradleDsl): Unit = runBlocking {
        initProject(projectInfo("project", gradleDsl) {
            simpleKotlinSettingsFile()
            simpleKotlinRootModuleInfo()
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                setGradleWizardData("module", "project/module", gradleDsl, parentData = null)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                simpleKotlinRootModuleInfo()
                simpleKotlinSettingsFile()
            }, projectInfo("project/module", gradleDsl) {
                simpleKotlinRootModuleInfo()
                simpleKotlinSettingsFile()
            })
            project.compileModules(
                "project", "project.main", "project.test",
                "module", "module.main", "module.test",
            )
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @EnumSource(GradleDsl::class)
    fun testNoMultiModuleProjectForNewModules(gradleDsl: GradleDsl): Unit = runBlocking {
        initProject(projectInfo("project", gradleDsl) {
            simpleKotlinRootModuleInfo()
            simpleKotlinSettingsFile()
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                val projectNode = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, project.projectPath)!!
                setGradleWizardData("module", "project/module", gradleDsl, parentData = projectNode.data)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                simpleKotlinRootModuleInfo()
                simpleKotlinSettingsFile {
                    include("module")
                }
                simpleKotlinModuleInfo("project.module", "module", kotlinJvmPluginVersion = null)
            })
            project.compileModules(
                "project", "project.main", "project.test",
                "project.module", "project.module.main", "project.module.test"
            )
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @EnumSource(GradleDsl::class)
    fun testOtherKotlinModule(gradleDsl: GradleDsl): Unit = runBlocking {
        val kotlinJvmPluginVersion = "1.9.25"
        initProject(projectInfo("project", gradleDsl) {
            simpleKotlinSettingsFile {
                include("other_module")
            }
            simpleJavaRootModuleInfo()
            simpleKotlinModuleInfo("project.other_module", "other_module", kotlinJvmPluginVersion = kotlinJvmPluginVersion)
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                val projectNode = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, project.projectPath)!!
                setGradleWizardData("module", "project/module", gradleDsl, parentData = projectNode.data)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project", gradleDsl) {
                simpleKotlinSettingsFile {
                    include("other_module")
                    include("module")
                }
                simpleJavaRootModuleInfo()
                simpleKotlinModuleInfo("project.other_module", "other_module", kotlinJvmPluginVersion = kotlinJvmPluginVersion)
                simpleKotlinModuleInfo("project.module", "module", kotlinJvmPluginVersion = kotlinJvmPluginVersion)
            })
            project.compileModules(
                "project", "project.main", "project.test",
                "project.module", "project.module.main", "project.module.test",
                "project.other_module", "project.other_module.main", "project.other_module.test"
            )
        }.closeProjectAsync()
    }

    @ParameterizedTest
    @CsvCrossProductSource("KOTLIN,GROOVY", "true,false")
    fun testNewModuleWithVersionCatalog(gradleDsl: GradleDsl, addBuildSrcVersionCatalogDependency: Boolean): Unit = runBlocking {
        val kotlinJvmPluginVersion = when {
            GradleVersionUtil.isGradleAtLeast(gradleVersion, "9.6.0") -> "2.3.21"
            GradleVersionUtil.isGradleAtLeast(gradleVersion, "9.5.0") -> "2.3.20"
            GradleVersionUtil.isGradleAtLeast(gradleVersion, "9.4.0") -> "2.3.0"
            else -> "2.2.21"
        }
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
            file("gradle/libs.versions.toml", versionTomlContent)
            moduleInfo("project.buildSrc", "buildSrc") {
                settingsFile {
                    addCode(versionCatalogContent)
                }
                sourceSetInfo("main")
                sourceSetInfo("test")
                buildFile {
                    withKotlinDsl()
                    if (addBuildSrcVersionCatalogDependency) {
                        addImplementationDependency(code("libs.kotlinGradlePlugin"))
                    }
                }
            }
            simpleKotlinSettingsFile()
        })
        openProject("project").withProjectAsync { project ->
            createModuleByWizard(project, KOTLIN) {
                val projectNode = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, project.projectPath)!!
                setGradleWizardData("module", "project/module", gradleDsl, parentData = projectNode.data)
            }
        }.withProjectAsync { project ->
            assertProjectState(project, projectInfo("project") {
                file("gradle/libs.versions.toml", versionTomlContent)
                moduleInfo("project.buildSrc", "buildSrc") {
                    settingsFile {
                        addCode(versionCatalogContent)
                    }
                    sourceSetInfo("main")
                    sourceSetInfo("test")
                    buildFile {
                        withKotlinDsl()
                        if (addBuildSrcVersionCatalogDependency) {
                            addImplementationDependency(code("libs.kotlinGradlePlugin"))
                        }
                    }
                }
                simpleKotlinSettingsFile {
                    include("module")
                }
                if (addBuildSrcVersionCatalogDependency) {
                    // It should not specify an explicit version because it is defined in the version catalog
                    simpleKotlinModuleInfo("project.module", "module", gradleDsl, kotlinJvmPluginVersion = null)
                } else {
                    simpleKotlinModuleInfo("project.module", "module", gradleDsl, kotlinJvmPluginVersion = kotlinJvmPluginVersion)
                }
            })
            project.compileModules(
                "project",
                "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test",
                "project.module", "project.module.main", "project.module.test"
            )
        }.closeProjectAsync()
    }

    @Test
    fun testSdkFilterForMaxSupportedVersion() {
        assertTrue(JavaSdkVersion.JDK_25.isLessOrEqualToMaxJvmTarget())
    }

    @Test
    fun testSdkFilterForUnsupportedVersion() {
        assertFalse(JavaSdkVersion.JDK_27.isLessOrEqualToMaxJvmTarget())
    }
}