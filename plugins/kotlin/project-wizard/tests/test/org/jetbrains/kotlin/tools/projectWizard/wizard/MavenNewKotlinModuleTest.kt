// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.application.options.CodeStyle
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.KOTLIN
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.idea.IJIgnore
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.withValue
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.useProject
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.MavenJavaNewProjectWizardData.Companion.javaMavenData
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardTestCase
import org.jetbrains.idea.maven.wizards.sdk
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.kotlinBuildSystemData
import org.jetbrains.kotlin.tools.projectWizard.maven.MavenKotlinNewProjectWizardData.Companion.kotlinMavenData
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@TestRoot("project-wizard/tests")
@RunWith(JUnit4::class)
class MavenNewKotlinModuleTest : MavenNewProjectWizardTestCase(), NewKotlinProjectTestUtils {
    override val testDirectory: String
        get() = "testData/mavenNewProjectWizard"
    private val newModuleName = "module"

    @JvmField
    @Rule
    var testName = TestName()

    override fun runInDispatchThread() = false

    override fun tearDown() {
        runAll({ CodeStyle.getDefaultSettings().clearCodeStyleSettings() },
               { KotlinSdkType.removeKotlinSdkInTests() },
               { super.tearDown() })
    }

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
            runBlocking {
                waitForModuleCreation {
                    createKotlinModuleFromTemplate(project)
                }
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
            runBlocking {
                waitForModuleCreation {
                    createKotlinModuleFromTemplate(project)
                }
            }
            assertModules(project, "project", newModuleName)

            // verify SKD is inherited
            val moduleModule = ModuleManager.getInstanceAsync(project).findModuleByName(newModuleName)!!
            Assert.assertTrue(ModuleRootManager.getInstance(moduleModule).modifiableModel.isSdkInherited)
        }
        return@runBlocking
    }

    @Test
    @IJIgnore(issue = "KTIJ-35262")
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

            runBlocking {
                waitForModuleCreation {
                    createKotlinModuleFromTemplate(project, addSampleCode = true)
                }
            }

            assertModules(project, listOf("project", newModuleName))
            project.assertCorrectProjectFiles()

        }
        return@runBlocking
    }

    @Test
    @IJIgnore(issue = "KTIJ-35262")
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

            runBlocking {
                waitForModuleCreation {
                    createKotlinModuleFromTemplate(
                        project, groupId = groupId,
                        version = version,
                        addSampleCode = addSampleCodeToModule,
                        independentHierarchy = independentHierarchy
                    )
                }
            }

            assertModules(project, expectedModules)
            project.assertCorrectProjectFiles()
            additionalAssertions(project)

        }
        return@runBlocking
    }

    @Test
    @IJIgnore(issue = "KTIJ-35262")
    fun testCreateNewProject() {
        runBlocking {
            waitForProjectCreation {
                createKotlinProjectFromTemplate()
            }.useProject { project ->
                assertModules(project, listOf("project"))
                project.assertCorrectProjectFiles()
            }
        }
    }

    @Test
    @IJIgnore(issue = "KTIJ-35262")
    fun testSimpleProject() {
        runNewProjectAndModuleTestCase()
    }

    @Test
    @IJIgnore(issue = "KTIJ-35262")
    fun testAddSampleCodeEverywhere() {
        runNewProjectAndModuleTestCase(addSampleCodeToProject = true, addSampleCodeToModule = true)
    }

    @Test
    @IJIgnore(issue = "KTIJ-35262")
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

    private fun createKotlinProjectFromTemplate(
        groupId: String = "org.testcase",
        version: String = "1.0.0",
        addSampleCode: Boolean = false,
    ): Project {
        return createProjectFromTemplate(KOTLIN) {
            it.baseData!!.name = "project"
            it.kotlinBuildSystemData!!.buildSystem = MAVEN
            it.kotlinMavenData!!.sdk = mySdk
            it.kotlinMavenData!!.parentData = null

            it.kotlinMavenData!!.groupId = groupId
            it.kotlinMavenData!!.artifactId = "project"
            it.kotlinMavenData!!.version = version

            it.kotlinMavenData!!.addSampleCode = addSampleCode
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

    override fun postprocessOutputFile(relativePath: String, fileContents: String): String {
        val result = substituteCompilerSourceAndTargetLevels(fileContents)
        return substituteArtifactsVersions(result)
    }

    override fun getTestFolderName(): String {
        return testName.methodName.takeWhile { it != '(' }.removePrefix("test").decapitalizeAsciiOnly()
    }

    override fun substituteArtifactsVersions(str: String): String {
        var result = str

        result = substituteVersionForArtifact(result, "kotlin-maven-plugin", needMoreSpaces = true)
        result = substituteVersionForArtifact(result, "kotlin-test-junit5")
        result = substituteVersionForArtifact(result, "kotlin-stdlib")
        result = substituteVersionForArtifact(result, "maven-surefire-plugin", needMoreSpaces = true)
        result = substituteVersionForArtifact(result, "maven-failsafe-plugin", needMoreSpaces = true)
        result = substituteVersionForArtifact(result, "junit-jupiter")
        result = substituteVersionForArtifact(result, "exec-maven-plugin", needMoreSpaces = true)

        return result
    }

    private fun substituteVersionForArtifact(str: String, artifactId: String, needMoreSpaces: Boolean = false): String {
        val regex =
            Regex("<artifactId>$artifactId</artifactId>\n( )*<version>(([a-zA-Z]|(\\.)|(\\d)|-)+)")
        var result = str
        if (result.contains(regex)) {
            val additionalSpaces = if (needMoreSpaces) {
                "    "
            } else {
                ""
            }
            result = result.replace(
                regex, "<artifactId>$artifactId</artifactId>\n" +
                        "$additionalSpaces            <version>VERSION"
            )
        }
        return result
    }

    private fun substituteCompilerSourceAndTargetLevels(fileContents: String): String {
        val compilerSourceRegex = Regex("<maven.compiler.source>(\\d\\d)")
        val compilerTargetRegex = Regex("<maven.compiler.target>(\\d\\d)")
        var result = fileContents
        if (result.contains(compilerSourceRegex)) {
            result = result.replace(compilerSourceRegex, "<maven.compiler.source>VERSION")
        }
        if (result.contains(compilerTargetRegex)) {
            result = result.replace(compilerTargetRegex, "<maven.compiler.target>VERSION")
        }
        return result
    }
}