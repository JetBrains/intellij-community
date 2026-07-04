// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.application.options.CodeStyle
import com.intellij.facet.FacetManager
import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.notification.Notification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.packaging.impl.artifacts.ArtifactUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.util.PathUtil
import com.intellij.util.ThrowableRunnable
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.additionalArgumentsAsList
import org.jetbrains.kotlin.idea.base.platforms.KotlinCommonLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModules
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.formatter.KotlinObsoleteStyleGuide
import org.jetbrains.kotlin.idea.formatter.KotlinOfficialStyleGuide
import org.jetbrains.kotlin.idea.formatter.kotlinCodeStyleDefaults
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.maven.compilerPlugin.toJpsVersionAgnosticKotlinBundledPath
import org.jetbrains.kotlin.idea.notification.asText
import org.jetbrains.kotlin.idea.notification.catchNotificationTextAsync
import org.jetbrains.kotlin.idea.notification.catchNotificationsAsync
import org.jetbrains.kotlin.idea.test.resetCodeStyle
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.workspaceModel.KotlinFacetBridgeFactory
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import org.junit.Assert.assertNotEquals
import org.junit.Assume
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractKotlinMavenImporterTest(private val createStdProjectFolders: Boolean = true) : KotlinMavenImportingTestCase() {
    protected val kotlinVersion = "1.1.3"

    private val artifactDownloadingScheduled = AtomicInteger()
    private val artifactDownloadingFinished = AtomicInteger()

    private annotation class MppGoal

    override fun setUp() {
        super.setUp()
        if (KotlinFacetBridgeFactory.kotlinFacetBridgeEnabled) {
            Assume.assumeFalse(
                "Disable MPP import tests because Workspace model does not support it yet",
                this.javaClass.isAnnotationPresent(MppGoal::class.java)
            )
        }
        if (createStdProjectFolders) createStdProjectFolders()
        project.messageBus.connect(testRootDisposable)
            .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
                override fun artifactDownloadingScheduled() {
                    artifactDownloadingScheduled.incrementAndGet()
                }

                override fun artifactDownloadingFinished() {
                    artifactDownloadingFinished.incrementAndGet()
                }
            })
    }

    override fun tearDown() = runBlocking {
        try {
            waitForScheduledArtifactDownloads()
        } finally {
            runAll(
                { resetCodeStyle(project) },
                { super.tearDown() },
            )
        }
    }

    private suspend fun waitForScheduledArtifactDownloads() {
        assertWithinTimeout {
            val scheduled = artifactDownloadingScheduled.get()
            val finished = artifactDownloadingFinished.get()
            Assert.assertEquals("Expected $scheduled artifact downloads, but finished $finished", scheduled, finished)
        }
    }

    @OptIn(KaExperimentalApi::class)
    protected suspend fun checkStableModuleName(
        projectName: String,
        expectedName: String,
        platform: TargetPlatform,
        isProduction: Boolean
    ) = readAction {
        val module = getModule(projectName)
        val kaModule = module.toKaSourceModule(if (isProduction) KaSourceModuleKind.PRODUCTION else KaSourceModuleKind.TEST)

        Assert.assertEquals("<$expectedName>", kaModule?.stableModuleName)
    }

    protected fun facetSettings(moduleName: String) = KotlinFacet.get(getModule(moduleName))!!.configuration.settings

    protected val facetSettings: IKotlinFacetSettings
        get() = facetSettings("project")

    protected suspend fun doJvmTarget6Test(version: String?): Pair<IKotlinFacetSettings, List<Notification>> {
        createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        val notifications = catchNotificationsAsync(project, "Kotlin Maven project import", testRootDisposable) {
            importProjectAsync(
                """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>

                    <dependencies>
                        <dependency>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-stdlib</artifactId>
                            <version>$kotlinVersion</version>
                        </dependency>
                    </dependencies>

                    <build>
                        <sourceDirectory>src/main/kotlin</sourceDirectory>

                        <plugins>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                ${version?.let { "<version>$it</version>" } ?: ""}

                                <executions>
                                    <execution>
                                        <id>compile</id>
                                        <phase>compile</phase>
                                        <goals>
                                            <goal>compile</goal>
                                        </goals>
                                    </execution>
                                </executions>
                                <configuration>
                                    <jvmTarget>1.6</jvmTarget>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                """
            )
        }

        assertModules("project")

        return facetSettings to notifications
    }

    class InvalidJvmTarget : AbstractKotlinMavenImporterTest() {
        @Test
        fun testInvalidJvmTarget() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            val kotlinMavenPluginVersion = "1.6.20"
            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <version>$kotlinMavenPluginVersion</version>

                        <executions>
                            <execution>
                                <id>compile</id>
                                <phase>compile</phase>
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                            </execution>
                        </executions>
                        <configuration>
                            <jvmTarget>ILLEGAL_ITEM</jvmTarget>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            with(facetSettings) {
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
            }
        }
    }

    class CollectSourceRootsInCompoundModule : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCollectSourceRootsInCompoundModule() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProjectAsync(
                """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.release>8</maven.compiler.release>
                        <maven.compiler.testRelease>11</maven.compiler.testRelease>
                    </properties>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.11.0</version>
                            </plugin>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>1.8.10</version>
                                <executions>
                                    <execution>
                                        <id>compile</id>
                                        <goals>
                                            <goal>compile</goal>
                                        </goals>
                                        <configuration>
                                            <sourceDirs>
                                                <sourceDir>${"$"}{project.basedir}/src/main/kotlin</sourceDir>
                                                <sourceDir>${"$"}{project.basedir}/src/main/java</sourceDir>
                                            </sourceDirs>
                                        </configuration>
                                    </execution>
                                    <execution>
                                        <id>test-compile</id>
                                        <goals>
                                            <goal>test-compile</goal>
                                        </goals>
                                        <configuration>
                                            <sourceDirs>
                                                <sourceDir>${"$"}{project.basedir}/src/test/kotlin</sourceDir>
                                                <sourceDir>${"$"}{project.basedir}/src/test/java</sourceDir>
                                            </sourceDirs>
                                        </configuration>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
            """
            )

            val mainModule = "project.main"
            val testModule = "project.test"
            val compoundModule = "project"

            assertModules(compoundModule, mainModule, testModule)

            assertSources(mainModule, "src/main/kotlin", "src/main/java")
            assertDefaultResources(mainModule)

            assertTestSources(testModule,"src/test/kotlin", "src/test/java")
            assertDefaultTestResources(testModule)
        }
    }

    class CollectTestSourceRootsInCompoundModule : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCollectSourceRootsInCompoundModule() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProjectAsync(
                """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.release>8</maven.compiler.release>
                        <maven.compiler.testRelease>11</maven.compiler.testRelease>
                    </properties>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.11.0</version>
                            </plugin>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>1.8.10</version>
                                <executions>
                                    <execution>
                                        <id>compile</id>
                                        <goals>
                                            <goal>compile</goal>
                                        </goals>
                                        <configuration>
                                            <sourceDirs>
                                                <sourceDir>${"$"}{project.basedir}/src/main/kotlin</sourceDir>
                                                <sourceDir>${"$"}{project.basedir}/src/main/java</sourceDir>
                                            </sourceDirs>
                                        </configuration>
                                    </execution>
                                    <execution>
                                        <id>test-compile</id>
                                        <goals>
                                            <goal>test-compile</goal>
                                        </goals>
                                        <configuration>
                                            <sourceDirs>
                                                <sourceDir>${"$"}{project.basedir}/src/test/kotlin</sourceDir>
                                                <sourceDir>${"$"}{project.basedir}/src/test/java</sourceDir>
                                            </sourceDirs>
                                        </configuration>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                        <resources>
                            <resource>
                              <directory>${"$"}{project.basedir}</directory>
                              <targetPath>META-INF</targetPath>
                              <includes>
                                  <include>LICENSE</include>
                              </includes>
                            </resource>
                        </resources>
                    </build>
            """
            )

            val mainModule = "project.main"
            val testModule = "project.test"
            val compoundModule = "project"

            assertModules(compoundModule, mainModule, testModule)

            val mainModuleTestSources = getContentRoots(mainModule)
                .flatMap { it.getSourceFolders(JavaSourceRootType.TEST_SOURCE) }
                .map { it.url }
            assertEmpty(mainModuleTestSources)

            val testModuleSources = getContentRoots(testModule)
                .flatMap { it.getSourceFolders(JavaSourceRootType.SOURCE) }
                .map { it.url }
            assertEmpty(testModuleSources)
        }
    }

    internal class ApiVersionExceedingLanguageVersion : AbstractKotlinMavenImporterTest() {
        @Test
        fun testApiVersionExceedingLanguageVersion() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            val kotlinMavenPluginVersion = "1.6.20"
            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <version>$kotlinMavenPluginVersion</version>

                        <executions>
                            <execution>
                                <id>compile</id>
                                <phase>compile</phase>
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                            </execution>
                        </executions>
                        <configuration>
                            <languageVersion>1.1</languageVersion>
                            <apiVersion>1.2</apiVersion>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            with(facetSettings) {
                Assert.assertEquals("1.1", languageLevel!!.versionString)
                Assert.assertEquals("1.1", compilerArguments!!.languageVersion)
                Assert.assertEquals("1.2", apiLevel!!.versionString)
                Assert.assertEquals("1.2", compilerArguments!!.apiVersion)
            }
        }
    }

    object TestVersions {
        object Kotlin {
            const val KOTLIN_2_3_10 = "2.3.10"
            const val KOTLIN_2_3_20 = "2.3.20-Beta2"

            const val LATEST_STABLE = "2.3.10"
        }
    }
}
