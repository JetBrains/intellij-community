// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.junit.Test
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
        mavenDependencyManager = MavenKotlinBuildSystemDependencyManager(project)
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
        val buildScript = runReadAction {
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

    private fun doAddingDependencyTest(moduleName: String, libraryDescriptor: ExternalLibraryDescriptor, expectedScope: String? = null) {
        val module = project.modules.find { it.name == moduleName }!!
        WriteCommandAction.writeCommandAction(project).run<Throwable> {
            mavenDependencyManager.addDependency(module, libraryDescriptor)
        }
        runReadAction {
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