// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.testFramework.junit5.RegistryKey
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A test case for the Kotlin Gradle new project/module wizard.
 * Test cases can either generate a new project, or a new module in an existing projects.
 * Existing projects can be defined using the [projectInfo] DSL.
 * The tests work by asserting that certain generated files are created with the correct content,
 * the files are located in the testData/newProjectWizard/<testName> folder.
 * Only files that are mentioned in these folders are asserted to be generated correctly.
 */
@Disabled("Temporarily disabled until timeouts are fixed: KTI-2059")
class GradleKotlinNewProjectWizardTest : GradleKotlinNewProjectWizardTestCase() {

    @Test
    fun testSimpleProject() {
        runNewProjectTestCase()
    }

    @Test
    fun testSimpleProjectKts() {
        runNewProjectTestCase(useKotlinDsl = true)
    }

    @Test
    @RegistryKey("gradle.daemon.jvm.criteria.new.project", "true")
    fun testSimpleProjectUsingDaemonJvmCriteria() {
        runNewProjectTestCase { project ->
            assertDaemonJvmProperties(project)
        }
    }

    private val expectedMultiModuleProjectModules = listOf(
        "project",
        "project.app",
        "project.utils",
        "project.buildSrc",
        "project.app.main",
        "project.app.test",
        "project.utils.main",
        "project.utils.test",
        "project.buildSrc.main",
        "project.buildSrc.test"
    )

    @Test
    fun testNoMultiModuleProjectForGroovy() {
        runNewProjectTestCase(
            generateMultipleModules = true,
        )
    }

    @Test
    fun testMultiModuleProjectKts() {
        runNewProjectTestCase(
            useKotlinDsl = true,
            addSampleCode = true,
            expectedModules = expectedMultiModuleProjectModules,
            generateMultipleModules = true
        )
    }

    @Test
    fun testMultiModuleProjectEmptyKts() {
        runNewProjectTestCase(
            useKotlinDsl = true,
            addSampleCode = false,
            expectedModules = expectedMultiModuleProjectModules,
            generateMultipleModules = true
        ) { project ->
            val basePath = File(project.basePath!!)
            val hasKotlinFile = basePath.walkTopDown().none { it.extension == "kt" }
            Assertions.assertFalse(hasKotlinFile, "empty project should not contain Kotlin files")
        }
    }

    @Test
    fun testSampleCode() {
        runNewProjectTestCase(addSampleCode = true) { project ->
            val mainFileContent = project.findMainFileContent()
            Assertions.assertNotNull(mainFileContent, "Could not find Main.kt file")
            Assertions.assertTrue(
                mainFileContent!!.contains(ONBOARDING_TIPS_SEARCH_STR),
                "Main file did not contain onboarding tips"
            )
            Assertions.assertTrue(
                mainFileContent.contains("//TIP"),
                "Main file contained rendered onboarding tips"
            )
        }
    }

    // The onboarding tips have to be handled a bit more manually because the rendered text depends on the OS of the system
    // because shortcuts are OS specific
    @Test
    fun testSampleCodeKts() {
        runNewProjectTestCase(useKotlinDsl = true, addSampleCode = true) { project ->
            val mainFileContent = project.findMainFileContent()
            Assertions.assertNotNull(mainFileContent, "Could not find Main.kt file")
            Assertions.assertTrue(
                mainFileContent!!.contains(ONBOARDING_TIPS_SEARCH_STR),
                "Main file did not contain onboarding tips"
            )
            Assertions.assertTrue(
                mainFileContent.contains("//TIP"),
                "Main file contained rendered onboarding tips"
            )
        }
    }

    @Test
    @RegistryKey("doc.onboarding.tips.render", "false")
    fun testSampleCodeRawOnboardingTips() {
        runNewProjectTestCase(addSampleCode = true) { project ->
            val mainFileContent = project.findMainFileContent()
            Assertions.assertNotNull(mainFileContent, "Could not find Main.kt file")
            Assertions.assertTrue(
                mainFileContent!!.contains(ONBOARDING_TIPS_SEARCH_STR),
                "Main file did not contain onboarding tips"
            )
            Assertions.assertFalse(
                mainFileContent.contains("//TIP"),
                "Main file contained rendered onboarding tips"
            )
        }
    }

    @Test
    @RegistryKey("doc.onboarding.tips.render", "false")
    fun testSampleCodeRawOnboardingTipsKts() {
        runNewProjectTestCase(useKotlinDsl = true, addSampleCode = true) { project ->
            val mainFileContent = project.findMainFileContent()
            Assertions.assertNotNull(mainFileContent, "Could not find Main.kt file")
            Assertions.assertTrue(
                mainFileContent!!.contains(ONBOARDING_TIPS_SEARCH_STR),
                "Main file did not contain onboarding tips"
            )
            Assertions.assertFalse(
                mainFileContent.contains("//TIP"),
                "Main file contained rendered onboarding tips"
            )
        }
    }

    private fun simpleJavaProject(gradleDsl: GradleDsl) = projectInfo("project", gradleDsl) {
        withJavaBuildFile()
        withSettingsFile {
            withFoojayPlugin()
            setProjectName("project")
        }
    }

    @Test
    fun testNewModuleInJavaProject() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleJavaProject(GradleDsl.GROOVY),
            addSampleCode = true
        ) { project ->
            val propertiesExist = project.findRelativeFile("module/gradle.properties")?.exists() == true
            Assertions.assertFalse(propertiesExist, "Gradle properties file should not exist in modules")
        }
    }

    @Test
    fun testNewModuleInJavaProjectKts() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleJavaProject(GradleDsl.KOTLIN),
            addSampleCode = true,
            useKotlinDsl = true
        ) { project ->
            val propertiesExist = project.findRelativeFile("module/gradle.properties")?.exists() == true
            Assertions.assertFalse(propertiesExist, "Gradle properties file should not exist in modules")
        }
    }

    @Test
    fun testNewModuleInJavaProjectMixed() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleJavaProject(GradleDsl.KOTLIN)
        )
    }

    private fun simpleKotlinProject(gradleDsl: GradleDsl) = projectInfo("project", gradleDsl) {
        withBuildFile {
            addGroup(groupId)
            addVersion(version)
            withKotlinJvmPlugin("1.9.0")
        }
        withSettingsFile {
            withFoojayPlugin()
            setProjectName("project")
        }
    }

    @Test
    fun testNewModuleInKotlinProject() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleKotlinProject(GradleDsl.GROOVY)
        )
    }

    @Test
    fun testNewModuleInKotlinProjectKts() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleKotlinProject(GradleDsl.KOTLIN),
            useKotlinDsl = true
        )
    }

    @Test
    fun testNewModuleInKotlinProjectIndependentHierarchy() {
        runNewModuleTestCase(
            expectedNewModules = listOf("module", "module.main", "module.test"),
            projectInfo = simpleKotlinProject(GradleDsl.GROOVY),
            independentHierarchy = true
        )
    }

    @Test
    fun testNewModuleInKotlinProjectIndependentHierarchyKts() {
        runNewModuleTestCase(
            expectedNewModules = listOf("module", "module.main", "module.test"),
            projectInfo = simpleKotlinProject(GradleDsl.KOTLIN),
            useKotlinDsl = true,
            independentHierarchy = true
        )
    }

    @Test
    fun testNoMultiModuleProjectForNewModulesKts() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleKotlinProject(GradleDsl.KOTLIN),
            useKotlinDsl = true,
        )
    }

    private fun javaProjectWithKotlinSubmodule(gradleDsl: GradleDsl) = projectInfo("project", gradleDsl) {
        withJavaBuildFile()
        moduleInfo("othermodule", "othermodule", gradleDsl) {
            withBuildFile {
                addGroup(groupId)
                addVersion(version)
                withKotlinJvmPlugin("1.8.0")
            }
        }
        withSettingsFile {
            withFoojayPlugin()
            setProjectName("project")
            include("othermodule")
        }
    }

    @Test
    fun testOtherKotlinModule() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = javaProjectWithKotlinSubmodule(GradleDsl.GROOVY)
        ) { project ->
            project.assertKotlinVersion("1.8.0", false, "module")
        }
    }

    @Test
    fun testOtherKotlinModuleKts() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = javaProjectWithKotlinSubmodule(GradleDsl.KOTLIN),
            useKotlinDsl = true
        ) { project ->
            project.assertKotlinVersion("1.8.0", true, "module")
        }
    }

    private fun simpleBuildSrcProjectWithVersionCatalog(useDependency: Boolean) = projectInfo("project") {
        moduleInfo("project.buildSrc", "buildSrc") {
            withBuildFile {
                if (useDependency) {
                    withDependency {
                        addElement(code("libs.kotlinGradlePlugin"))
                    }
                }
            }
            withSettingsFile {
                addCode(
                    """
                        dependencyResolutionManagement {
        
                            // Use Maven Central and Gradle Plugin Portal for resolving dependencies in the shared build logic ("buildSrc") project
                            @Suppress("UnstableApiUsage")
                            repositories {
                                mavenCentral()
                            }
        
                            // Re-use the version catalog from the main build
                            versionCatalogs {
                                create("libs") {
                                    from(files("../gradle/libs.versions.toml"))
                                }
                            }
                        }
                    """.trimIndent()
                )
            }
        }
        withSettingsFile {
            withFoojayPlugin()
            setProjectName("project")
        }
        withFile(
            "gradle/libs.versions.toml",
            """
                 [versions]
                 kotlin = "2.0.21"

                 [libraries]
                 kotlinGradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
             """.trimIndent()
        )
    }

    @Test
    fun testNewModuleWithVersionCatalog() {
        runNewModuleTestCase(
            useKotlinDsl = false,
            expectedNewModules = listOf("module"),
            projectInfo = simpleBuildSrcProjectWithVersionCatalog(true)
        ) { project ->
            // It should not specify an explicit version because it is defined in the version catalog
            Assertions.assertNull(project.findKotlinVersion(false, "module"))
        }
    }

    @Test
    fun testNewModuleWithVersionCatalogKts() {
        runNewModuleTestCase(
            useKotlinDsl = true,
            expectedNewModules = listOf("module"),
            projectInfo = simpleBuildSrcProjectWithVersionCatalog(true)
        ) { project ->
            // It should not specify an explicit version because it is defined in the version catalog
            Assertions.assertNull(project.findKotlinVersion(true, "module"))
        }
    }

    @Test
    fun testNewModuleWithUnusedVersionCatalog() {
        runNewModuleTestCase(
            useKotlinDsl = false,
            expectedNewModules = listOf("project.module.main", "project.module.test", "project.module"),
            projectInfo = simpleBuildSrcProjectWithVersionCatalog(false)
        ) { project ->
            Assertions.assertNotNull(project.findKotlinVersion(false, "module"))
        }
    }

    @Test
    fun testNewModuleWithUnusedVersionCatalogKts() {
        runNewModuleTestCase(
            useKotlinDsl = true,
            expectedNewModules = listOf("project.module.main", "project.module.test", "project.module"),
            projectInfo = simpleBuildSrcProjectWithVersionCatalog(false)
        ) { project ->
            Assertions.assertNotNull(project.findKotlinVersion(true, "module"))
        }
    }
}