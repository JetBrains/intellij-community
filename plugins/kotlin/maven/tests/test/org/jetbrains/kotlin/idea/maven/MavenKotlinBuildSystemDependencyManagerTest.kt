// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.KotlinDependencyProvider
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
class MavenKotlinBuildSystemDependencyManagerTest : AbstractKotlinMavenImporterTest() {
    private lateinit var mavenDependencyManager: MavenKotlinBuildSystemDependencyManager

    private val simplePom = """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1.0.0</version>

        <properties>
            <maven.compiler.source>11</maven.compiler.source>
            <maven.compiler.target>11</maven.compiler.target>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        </properties>
    """.trimIndent()

    override fun setUp() {
        super.setUp()
        mavenDependencyManager = KotlinBuildSystemDependencyManager.findConfigurator<MavenKotlinBuildSystemDependencyManager>(project)
    }

    @Test
    fun testMavenDependencyManagerIsApplicable() = runBlocking {
        importProjectAsync(simplePom)
        val module = project.modules.first()
        val extensions = project.extensionArea.getExtensionPoint(KotlinBuildSystemDependencyManager.EP_NAME).extensionList
        val applicableConfigurators = extensions.filter { it.isApplicable(module) }
        assertSize(1, applicableConfigurators.filterIsInstance<MavenKotlinBuildSystemDependencyManager>())
    }

    @Test
    fun testGettingBuildFile() = runBlocking {
        importProjectAsync(simplePom)
        val module = project.modules.first()
        val buildScript = runReadAction {
            mavenDependencyManager.getBuildScriptFile(module)
        }
        assertNotNull(buildScript)
        val buildScriptPath = buildScript!!.toNioPath()
        val projectPath = projectRoot.toNioPath()

        val relativePath = projectPath.relativize(buildScriptPath)
        assertEquals("pom.xml", relativePath.invariantSeparatorsPathString)
    }

    private suspend fun importProjectWithSubmodule() {
        val mainModulePom = createProjectPom(
            """
            <groupId>test</groupId>
            <artifactId>mainModule</artifactId>
            <version>1</version>
            <modules>
                <module>submodule</module>
            </modules>
              """
        )
        val submodulePom = createModulePom(
            "submodule", """
              <groupId>test</groupId>
              <artifactId>submodule</artifactId>
              <version>1</version>
              """
        )
        importProjectsAsync(mainModulePom, submodulePom)
    }

    @Test
    fun testGettingBuildFileSubmodule() = runBlocking {
        importProjectWithSubmodule()
        val module = project.modules.find { it.name == "submodule" }!!
        assertNotNull(module)
        val buildScript = readAction {
            mavenDependencyManager.getBuildScriptFile(module)
        }
        assertNotNull(buildScript)
        val buildScriptPath = buildScript!!.toNioPath()
        val projectPath = projectRoot.toNioPath()

        val relativePath = projectPath.relativize(buildScriptPath)
        assertEquals("submodule/pom.xml", relativePath.invariantSeparatorsPathString)
    }

    private fun getTestLibraryDescriptor(scope: DependencyScope) =
        ExternalLibraryDescriptor("org.test", "artifact", "1.2.3", "1.2.3", "1.2.3", scope)

    private suspend fun doAddingDependencyTest(moduleName: String, libraryDescriptor: ExternalLibraryDescriptor, expectedScope: String? = null) {
        val module = project.modules.find { it.name == moduleName }!!

        val dependencyProvider = KotlinDependencyProvider.getInstance()
        val jobReference = AtomicReference<Job>()
        dependencyProvider.jobReference = jobReference

        val buildScript = readAction { mavenDependencyManager.getBuildScriptFile(module) }
            ?: error("No pom.xml for module $moduleName")

        val contextFile = readAction { PsiManager.getInstance(project).findFile(buildScript) }
            ?: error("No pom.xml for module $moduleName")

        val actionContext = ActionContext.from(null, contextFile)

        val modCommand =
            readAction {
                mavenDependencyManager.addDependencyModCommand(contextFile, module, libraryDescriptor)
            }

        writeCommandAction(project, "") {
            ModCommandExecutor.getInstance().executeInteractively(actionContext, modCommand, null)
        }

        jobReference.getAndSet(null)!!.join()

        mavenDependencyManager.coroutineScope.launch {
            readAction {
                val buildScript = mavenDependencyManager.getBuildScriptFile(module)
                assertNotNull(buildScript)
                val xmlFile = buildScript?.findPsiFile(project) as XmlFile
                val pomFile = PomFile.forFileOrNull(xmlFile)!!
                val foundDependency = pomFile.domModel.dependencies.dependencies.firstOrNull {
                    it.artifactId.stringValue == libraryDescriptor.libraryArtifactId &&
                            it.groupId.stringValue == libraryDescriptor.libraryGroupId &&
                            it.version.stringValue == libraryDescriptor.preferredVersion &&
                            it.scope.stringValue == expectedScope

                }
                assertNotNull("Did not find expected dependency in pom.xml", foundDependency)
            }
        }
    }

    @Test
    fun testAddingDependency() = runBlocking {
        importProjectAsync(simplePom)
        doAddingDependencyTest("project", getTestLibraryDescriptor(DependencyScope.COMPILE))
    }

    @Test
    fun testAddingTestDependency() = runBlocking {
        importProjectAsync(simplePom)
        doAddingDependencyTest("project", getTestLibraryDescriptor(DependencyScope.TEST), "test")
    }

    @Test
    fun testAddingDependencySubmodule() = runBlocking {
        importProjectWithSubmodule()
        doAddingDependencyTest("submodule", getTestLibraryDescriptor(DependencyScope.COMPILE))
    }
}