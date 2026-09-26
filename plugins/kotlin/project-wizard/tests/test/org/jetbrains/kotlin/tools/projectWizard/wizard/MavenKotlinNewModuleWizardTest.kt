// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.KOTLIN
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.withValue
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.useProject
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.MavenJavaNewProjectWizardData.Companion.javaMavenData
import org.jetbrains.idea.maven.wizards.sdk
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.kotlinBuildSystemData
import org.jetbrains.kotlin.tools.projectWizard.maven.MavenKotlinNewProjectWizardData.Companion.kotlinMavenData
import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@TestRoot("project-wizard/tests")
@RunWith(JUnit4::class)
class MavenKotlinNewModuleWizardTest : MavenKotlinNewProjectWizardTestCase() {
    override val testDirectory: String
        get() = "testData/mavenNewModuleWizard"
    private val newModuleName = "module"
    override val testRoot: File? = TestMetadataUtil.getTestRoot(MavenKotlinNewModuleWizardTest::class.java)

    @Test
    fun `test when module is created then its pom is unignored`() = runBlocking {
        // create project
        waitForProjectCreation {
            createKotlinProjectFromTemplate()
        }.useProject { project ->
            val mavenProjectsManager = MavenProjectsManager.getInstance(project)
            // import project
            assertModules(project, "project")
            Assert.assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

            // ignore pom
            val modulePomPath = "${project.basePath}/$newModuleName/pom.xml"
            val ignoredPoms = listOf(modulePomPath)
            mavenProjectsManager.ignoredFilesPaths = ignoredPoms
            Assert.assertEquals(ignoredPoms, mavenProjectsManager.ignoredFilesPaths)

            // create module
            waitForModuleCreation {
                createKotlinModuleFromTemplate(project)
            }
            assertModules(project, "project", newModuleName)

            // verify pom unignored
            assertSize(0, mavenProjectsManager.ignoredFilesPaths)
        }
        return@runBlocking
    }

    @Test
    fun `test new maven module inherits project sdk by default`() = runBlocking {
        // create project
        waitForProjectCreation {
            createKotlinProjectFromTemplate()
        }.useProject { project ->
            // import project
            assertModules(project, "project")
            val mavenProjectsManager = MavenProjectsManager.getInstance(project)
            Assert.assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

            // create module
            waitForModuleCreation {
                createKotlinModuleFromTemplate(project)
            }
            assertModules(project, "project", newModuleName)

            // verify SKD is inherited
            val moduleModule = ModuleManager.getInstanceAsync(project).findModuleByName(newModuleName)!!
            Assert.assertTrue(ModuleRootManager.getInstance(moduleModule).modifiableModel.isSdkInherited)
        }
        return@runBlocking
    }

    @Test
    fun testNewModuleInJavaProject() = runBlocking {
        waitForProjectCreation {
            createProjectFromTemplate(JAVA) {
                it.baseData!!.name = "project"
                it.javaBuildSystemData!!.buildSystem = MAVEN
                it.javaMavenData!!.sdk = mySdk
                it.javaMavenData!!.groupId = "org.testcase"
                it.javaMavenData!!.version = "1.0.0"
                it.javaMavenData!!.addSampleCode = false
            }
        }.useProject { project ->
            assertModules(project, "project")
            val mavenProjectsManager = MavenProjectsManager.getInstance(project)
            Assert.assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

            waitForModuleCreation {
                createKotlinModuleFromTemplate(project, addSampleCode = true)
            }

            assertModules(project, listOf("project", newModuleName))
            project.assertCorrectProjectFiles(testRoot)

        }
        return@runBlocking
    }

    @Test
    fun testNewModuleInKotlinProjectIndependentHierarchy() {
        runNewProjectAndModuleTestCase(
            independentHierarchy = true
        )
    }

    private fun runNewProjectAndModuleTestCase(
        expectedModules: List<String> = listOf("project", newModuleName),
        groupId: String = "org.testcase",
        version: String = "1.0.0",
        addSampleCodeToProject: Boolean = false,
        addSampleCodeToModule: Boolean = false,
        independentHierarchy: Boolean = false,
        additionalAssertions: (Project) -> Unit = {}
    ) = runBlocking {
        waitForProjectCreation {
            createKotlinProjectFromTemplate(
                groupId = groupId,
                version = version,
                addSampleCode = addSampleCodeToProject
            )
        }.useProject { project ->
            assertModules(project, "project")
            val mavenProjectsManager = MavenProjectsManager.getInstance(project)
            Assert.assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

            waitForModuleCreation {
                createKotlinModuleFromTemplate(
                    project, groupId = groupId,
                    version = version,
                    addSampleCode = addSampleCodeToModule,
                    independentHierarchy = independentHierarchy
                )
            }

            assertModules(project, expectedModules)
            project.assertCorrectProjectFiles(testRoot)
            additionalAssertions(project)

        }
        return@runBlocking
    }

    @Test
    fun testCreateNewProject() {
        runBlocking {
            waitForProjectCreation {
                createKotlinProjectFromTemplate()
            }.useProject { project ->
                assertModules(project, listOf("project"))
                project.assertCorrectProjectFiles(testRoot)
            }
        }
    }

    @Test
    fun testSimpleProject() {
        runNewProjectAndModuleTestCase()
    }

    @Test
    fun testAddSampleCodeEverywhere() {
        runNewProjectAndModuleTestCase(addSampleCodeToProject = true, addSampleCodeToModule = true)
    }

    @Test
    fun testAddSampleCodeOnlyInModule() {
        runNewProjectAndModuleTestCase(addSampleCodeToProject = false, addSampleCodeToModule = true)
    }

    @Test
    fun testSampleCode() {
        runBlocking {
            waitForProjectCreation {
                createKotlinProjectFromTemplate(addSampleCode = true)
            }.useProject { project ->
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
    }

    @Test
    fun testSampleCodeRawOnboardingTips() {
        Registry.get("doc.onboarding.tips.render").withValue(false) {
            runBlocking {
                waitForProjectCreation {
                    createKotlinProjectFromTemplate(addSampleCode = true)
                }.useProject { project ->
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
        }
    }

    private fun createKotlinModuleFromTemplate(
        project: Project,
        groupId: String = "org.testcase",
        version: String = "1.0.0",
        addSampleCode: Boolean = false,
        independentHierarchy: Boolean = false
    ): Module {
        val projectModule = project.modules.single()
        val mavenProjectsManager = MavenProjectsManager.getInstance(project)

        return createModuleFromTemplate(project, KOTLIN) {
            it.baseData!!.name = newModuleName
            it.kotlinBuildSystemData!!.buildSystem = MAVEN
            it.kotlinMavenData!!.sdk = mySdk

            it.kotlinMavenData!!.groupId = groupId
            it.kotlinMavenData!!.artifactId = newModuleName
            it.kotlinMavenData!!.version = version

            it.kotlinMavenData!!.addSampleCode = addSampleCode

            it.kotlinMavenData!!.parentData = if (independentHierarchy) {
                null
            } else {
                mavenProjectsManager.findProject(projectModule)
            }
        }
    }
}