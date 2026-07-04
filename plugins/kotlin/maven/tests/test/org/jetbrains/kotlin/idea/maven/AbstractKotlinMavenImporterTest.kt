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

    class JvmDetectionByGoalWithCommonStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmDetectionByGoalWithCommonStdlib() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-common</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            Assert.assertEquals(JvmPlatforms.jvm6, facetSettings.targetPlatform)

            assertSources("project", "src/main/kotlin")
            assertTestSources("project", "src/test/java")
            assertDefaultResources("project")
            assertDefaultTestResources("project")
        }
    }

    @MppGoal
    class JsDetectionByGoalWithJsStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsDetectionByGoalWithJsStdlib() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-js</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>js</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <goals>
                                    <goal>test-js</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            Assert.assertTrue(facetSettings.targetPlatform.isJs())

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertDefaultKotlinResources("project")
            assertDefaultKotlinTestResources("project")
        }
    }

    @MppGoal
    class JsDetectionByGoalWithCommonStdlib15 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsDetectionByGoalWithCommonStdlib() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-common</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>js</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <goals>
                                    <goal>test-js</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            Assert.assertTrue(facetSettings.targetPlatform.isJs())

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertDefaultKotlinResources("project")
            assertDefaultKotlinTestResources("project")
        }
    }

    @MppGoal
    class JsAndCommonStdlibKinds : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsAndCommonStdlibKinds() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-common</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-js</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>js</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <goals>
                                    <goal>test-js</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            Assert.assertTrue(facetSettings.targetPlatform.isJs())

            val rootManager = ModuleRootManager.getInstance(getModule("project"))
            val libraries = rootManager.orderEntries.filterIsInstance<LibraryOrderEntry>().map { it.library as LibraryEx }
            assertEquals(KotlinJavaScriptLibraryKind, libraries.single { it.name?.contains("kotlin-stdlib-js") == true }.kind)
            assertEquals(KotlinCommonLibraryKind, libraries.single { it.name?.contains("kotlin-stdlib-common") == true }.kind)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertDefaultKotlinResources("project")
            assertDefaultKotlinTestResources("project")
        }
    }

    @MppGoal
    class CommonDetectionByGoalWithJvmStdlib1164 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCommonDetectionByGoalWithJvmStdlib() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

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
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>metadata</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            Assert.assertTrue(facetSettings.targetPlatform.isCommon())

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertDefaultKotlinResources("project")
            assertDefaultKotlinTestResources("project")
        }
    }

    @MppGoal
    class CommonDetectionByGoalWithCommonStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCommonDetectionByGoalWithCommonStdlib() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-common</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>metadata</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            Assert.assertTrue(facetSettings.targetPlatform.isCommon())

            val rootManager = ModuleRootManager.getInstance(getModule("project"))
            val stdlib = rootManager.orderEntries.filterIsInstance<LibraryOrderEntry>().single().library
            assertEquals(KotlinCommonLibraryKind, (stdlib as LibraryEx).kind)

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertDefaultKotlinResources("project")
            assertDefaultKotlinTestResources("project")
        }
    }

    @MppGoal
    class JvmDetectionByConflictingGoalsAndJvmStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmDetectionByConflictingGoalsAndJvmStdlib() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

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
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>js</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            Assert.assertEquals(JvmPlatforms.jvm6, facetSettings.targetPlatform)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertDefaultKotlinResources("project")
            assertDefaultKotlinTestResources("project")
        }
    }

    @MppGoal
    class JsDetectionByConflictingGoalsAndJsStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsDetectionByConflictingGoalsAndJsStdlib() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-js</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>js</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            Assert.assertTrue(facetSettings.targetPlatform.isJs())

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertDefaultKotlinResources("project")
            assertDefaultKotlinTestResources("project")
        }
    }

    @MppGoal
    class CommonDetectionByConflictingGoalsAndCommonStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCommonDetectionByConflictingGoalsAndCommonStdlib() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-common</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>js</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            Assert.assertTrue(facetSettings.targetPlatform.isCommon())

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertDefaultKotlinResources("project")
            assertDefaultKotlinTestResources("project")
        }
    }

    class NoArgInvokeInitializers : AbstractKotlinMavenImporterTest() {
        @Test
        fun testNoArgInvokeInitializers() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

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
                        <executions>
                            <execution>
                                <id>compile</id>
                                <phase>compile</phase>
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                            </execution>
                        </executions>

                        <dependencies>
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-noarg</artifactId>
                                <version>$kotlinVersion</version>
                            </dependency>
                        </dependencies>

                        <configuration>
                            <compilerPlugins>
                                <plugin>no-arg</plugin>
                            </compilerPlugins>

                            <pluginOptions>
                                <option>no-arg:annotation=NoArg</option>
                                <option>no-arg:invokeInitializers=true</option>
                            </pluginOptions>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            with(facetSettings) {
                Assert.assertEquals(
                    "",
                    compilerSettings!!.additionalArguments
                )
                Assert.assertEquals(
                    listOf(
                        "plugin:org.jetbrains.kotlin.noarg:annotation=NoArg",
                        "plugin:org.jetbrains.kotlin.noarg:invokeInitializers=true"
                    ),
                    compilerArguments!!.pluginOptions!!.toList()
                )
            }
        }
    }

    class ArgsOverridingInFacet : AbstractKotlinMavenImporterTest() {
        @Test
        fun testArgsOverridingInFacet() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

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
                            <jvmTarget>1.8</jvmTarget>
                            <languageVersion>1.0</languageVersion>
                            <apiVersion>1.0</apiVersion>
                            <args>
                                <arg>-jvm-target</arg>
                                <arg>11</arg>
                                <arg>-language-version</arg>
                                <arg>1.1</arg>
                                <arg>-api-version</arg>
                                <arg>1.1</arg>
                            </args>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            with(facetSettings) {
                Assert.assertEquals("JVM 11", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals(LanguageVersion.KOTLIN_1_1.description, languageLevel!!.description)
                Assert.assertEquals(LanguageVersion.KOTLIN_1_1.description, apiLevel!!.description)
                Assert.assertEquals("11", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
            }
        }
    }

    class SubmoduleArgsInheritance : AbstractKotlinMavenImporterTest() {
        @Test
        fun testSubmoduleArgsInheritance() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "myModule1/src/main/kotlin", "myModule2/src/main/kotlin", "myModule3/src/main/kotlin")

            val mainPom = createProjectPom(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>
            <packaging>pom</packaging>

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
                            <jvmTarget>1.7</jvmTarget>
                            <languageVersion>1.1</languageVersion>
                            <apiVersion>1.0</apiVersion>
                            <args>
                                <arg>-java-parameters</arg>
                                <arg>-Xjava-source-roots=javaDir</arg>
                                <arg>-kotlin-home</arg>
                                <arg>temp</arg>
                            </args>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            val modulePom1 = createModulePom(
                "myModule1",
                """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>myModule1</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                </dependencies>

                <build>
                    <sourceDirectory>myModule1/src/main/kotlin</sourceDirectory>

                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>

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
                                <jvmTarget>1.8</jvmTarget>
                                <args>
                                    <arg>-Xjava-source-roots=javaDir2</arg>
                                </args>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
                """
            )

            val modulePom2 = createModulePom(
                "myModule2",
                """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>myModule2</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                </dependencies>

                <build>
                    <sourceDirectory>myModule2/src/main/kotlin</sourceDirectory>

                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>

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
                                <jvmTarget>1.8</jvmTarget>
                                <args combine.children="append">
                                    <arg>-kotlin-home</arg>
                                    <arg>temp2</arg>
                                </args>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
                """
            )

            val modulePom3 = createModulePom(
                "myModule3",
                """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>myModule3</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                </dependencies>

                <build>
                    <sourceDirectory>myModule3/src/main/kotlin</sourceDirectory>

                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>

                            <executions>
                                <execution>
                                    <id>compile</id>
                                    <phase>compile</phase>
                                    <goals>
                                        <goal>compile</goal>
                                    </goals>
                                </execution>
                            </executions>

                            <configuration combine.self="override">
                                <jvmTarget>1.8</jvmTarget>
                                <args>
                                    <arg>-kotlin-home</arg>
                                    <arg>temp2</arg>
                                </args>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
                """
            )

            importProjectsAsync(mainPom, modulePom1, modulePom2, modulePom3)

            assertModules("project", "myModule1", "myModule2", "myModule3")

            with(facetSettings("myModule1")) {
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals(LanguageVersion.KOTLIN_1_1, languageLevel!!)
                Assert.assertEquals(LanguageVersion.KOTLIN_1_0, apiLevel!!)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
                Assert.assertEquals(
                    listOf("-Xjava-source-roots=javaDir2"),
                    compilerSettings!!.additionalArgumentsAsList
                )
            }

            with(facetSettings("myModule2")) {
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals(LanguageVersion.KOTLIN_1_1, languageLevel!!)
                Assert.assertEquals(LanguageVersion.KOTLIN_1_0, apiLevel!!)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
                Assert.assertEquals(
                    listOf("-java-parameters", "-Xjava-source-roots=javaDir", "-kotlin-home", "temp2"),
                    compilerSettings!!.additionalArgumentsAsList
                )
            }

            with(facetSettings("myModule3")) {
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals(KotlinPluginLayout.standaloneCompilerVersion.languageVersion, languageLevel)
                Assert.assertEquals(KotlinPluginLayout.standaloneCompilerVersion.languageVersion, apiLevel)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
                Assert.assertEquals(
                    listOf("-kotlin-home", "temp2"),
                    compilerSettings!!.additionalArgumentsAsList
                )
            }
        }
    }

    class JpsCompilerMultiModule : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJpsCompilerMultiModule() = runBlocking {
            createProjectSubDirs(
                "src/main/kotlin",
                "module1/src/main/kotlin",
                "module2/src/main/kotlin",
            )

            val kotlinMainPluginVersion = "1.5.10"
            val kotlinMavenPluginVersion1 = "1.7.21"
            val kotlinMavenPluginVersion2 = "1.5.31"
            val notifications = catchNotificationsAsync(project, "Kotlin JPS plugin", testRootDisposable) {
                val mainPom = createProjectPom(
                    """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>

                    <modules>
                        <module>module1</module>
                        <module>module2</module>
                    </modules>

                    <build>
                        <sourceDirectory>src/main/kotlin</sourceDirectory>

                        <plugins>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>$kotlinMainPluginVersion</version>
                            </plugin>
                        </plugins>
                    </build>
                """
                )

                val module1 = createModulePom(
                    "module1",
                    """
                    <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1.0.0</version>
                    </parent>

                    <groupId>test</groupId>
                    <artifactId>module1</artifactId>
                    <version>1.0.0</version>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>$kotlinMavenPluginVersion1</version>
                            </plugin>
                        </plugins>
                    </build>
                """
                )

                val module2 = createModulePom(
                    "module2",
                    """
                    <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1.0.0</version>
                    </parent>

                    <groupId>test</groupId>
                    <artifactId>module2</artifactId>
                    <version>1.0.0</version>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>$kotlinMavenPluginVersion2</version>
                            </plugin>
                        </plugins>
                    </build>
                """
                )

                importProjectsAsync(mainPom, module1, module2)
            }

            assertModules("project", "module1", "module2")
            assertEquals("", notifications.asText())
            // The highest of available versions should be picked
            assertEquals(kotlinMavenPluginVersion1, KotlinJpsPluginSettings.jpsVersion(project))
        }
    }

    class JpsCompiler : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJpsCompilerUnsupportedVersionDown() = runBlocking {
            val version = "1.1.0"
            val notifications = catchNotificationsAsync(project, testRootDisposable) {
                doUnsupportedVersionTest(version, KotlinJpsPluginSettings.fallbackVersionForOutdatedCompiler)
            }

            val notification = notifications.find { it.title == "Unsupported Kotlin JPS plugin version" }
            assertNotNull(notifications.asText(), notification)
            assertEquals(
                notification?.content,
                "Version (${KotlinJpsPluginSettings.fallbackVersionForOutdatedCompiler}) of the Kotlin JPS plugin will be used<br>" +
                        "The reason: Kotlin JPS compiler minimum supported version is '${KotlinJpsPluginSettings.jpsMinimumSupportedVersion}' but '$version' is specified",
            )
        }

        @Test
        fun testJpsCompilerUnsupportedVersionUp() = runBlocking {
            val maxVersion = KotlinJpsPluginSettings.jpsMaximumSupportedVersion
            val versionToImport = KotlinVersion(maxVersion.major, maxVersion.minor, maxVersion.minor + 1)
            val text = catchNotificationTextAsync(project, "Kotlin JPS plugin", testRootDisposable) {
                doUnsupportedVersionTest(versionToImport.toString())
            }

            assertEquals(
                "Version (${KotlinJpsPluginSettings.rawBundledVersion}) of the Kotlin JPS plugin will be used<br>" +
                        "The reason: Kotlin JPS compiler maximum supported version is '$maxVersion' but '$versionToImport' is specified",
                text
            )
        }

        private suspend fun doUnsupportedVersionTest(
            version: String,
            expectedFallbackVersion: String = KotlinJpsPluginSettings.rawBundledVersion
        ) {
            createProjectSubDirs("src/main/kotlin")

            importProjectAsync(
                """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>


                    <build>
                        <sourceDirectory>src/main/kotlin</sourceDirectory>

                        <plugins>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>$version</version>
                            </plugin>
                        </plugins>
                    </build>
                """
            )

            assertModules("project")

            // Fallback to bundled to unsupported version
            assertNotEquals(version, KotlinJpsPluginSettings.jpsVersion(project))
            assertEquals(expectedFallbackVersion, KotlinJpsPluginSettings.jpsVersion(project))
        }

        @Test
        fun testDontShowNotificationWhenBuildIsDelegatedToMaven() = runBlocking {
            val isBuildDelegatedToMaven = MavenRunner.getInstance(project).settings.isDelegateBuildToMaven
            MavenRunner.getInstance(project).settings.isDelegateBuildToMaven = true

            try {
                val version = "1.1.0"
                val notifications = catchNotificationsAsync(project, testRootDisposable) {
                    doUnsupportedVersionTest(version, KotlinJpsPluginSettings.fallbackVersionForOutdatedCompiler)
                }

                assertNull(notifications.find { it.title == "Unsupported Kotlin JPS plugin version" })
            } finally {
                MavenRunner.getInstance(project).settings.isDelegateBuildToMaven = isBuildDelegatedToMaven
            }
        }
    }

    @MppGoal //TODO: write multimodule test for JVM only?
    class MultiModuleImport : AbstractKotlinMavenImporterTest() {
        @Test
        fun testMultiModuleImport() = runBlocking {
            createProjectSubDirs(
                "src/main/kotlin",
                "my-common-module/src/main/kotlin",
                "my-jvm-module/src/main/kotlin",
                "my-js-module/src/main/kotlin"
            )

            val mainPom = createProjectPom(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>
            <packaging>pom</packaging>

            <modules>
                <module>my-common-module</module>
                <module>my-jvm-module</module>
                <module>my-js-module</module>
            </modules>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <version>$kotlinVersion</version>
                    </plugin>
                </plugins>
            </build>
            """
            )

            val commonModule1 = createModulePom(
                "my-common-module1",
                """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>my-common-module1</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib-common</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>

                            <executions>
                                <execution>
                                    <id>meta</id>
                                    <phase>compile</phase>
                                    <goals>
                                        <goal>metadata</goal>
                                    </goals>
                                </execution>
                            </executions>
                        </plugin>
                    </plugins>
                </build>
                """
            )

            val commonModule2 = createModulePom(
                "my-common-module2",
                """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>my-common-module2</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib-common</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>

                            <executions>
                                <execution>
                                    <id>meta</id>
                                    <phase>compile</phase>
                                    <goals>
                                        <goal>metadata</goal>
                                    </goals>
                                </execution>
                            </executions>
                        </plugin>
                    </plugins>
                </build>
                """
            )

            val jvmModule = createModulePom(
                "my-jvm-module",
                """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>my-jvm-module</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                    <dependency>
                        <groupId>test</groupId>
                        <artifactId>my-common-module1</artifactId>
                        <version>1.0.0</version>
                    </dependency>
                    <dependency>
                        <groupId>test</groupId>
                        <artifactId>my-common-module2</artifactId>
                        <version>1.0.0</version>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>

                            <executions>
                                <execution>
                                    <id>compile</id>
                                    <phase>compile</phase>
                                    <goals>
                                        <goal>compile</goal>
                                    </goals>
                                </execution>
                            </executions>
                        </plugin>
                    </plugins>
                </build>
                """
            )

            val jsModule = createModulePom(
                "my-js-module",
                """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>my-js-module</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib-js</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                    <dependency>
                        <groupId>test</groupId>
                        <artifactId>my-common-module1</artifactId>
                        <version>1.0.0</version>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>

                            <executions>
                                <execution>
                                    <id>js</id>
                                    <phase>compile</phase>
                                    <goals>
                                        <goal>js</goal>
                                    </goals>
                                </execution>
                            </executions>
                        </plugin>
                    </plugins>
                </build>
                """
            )

            importProjectsAsync(mainPom, commonModule1, commonModule2, jvmModule, jsModule)

            assertModules("project", "my-common-module1", "my-common-module2", "my-jvm-module", "my-js-module")

            with(facetSettings("my-common-module1")) {
                Assert.assertEquals(CommonPlatforms.defaultCommonPlatform, targetPlatform)
            }

            with(facetSettings("my-common-module2")) {
                Assert.assertEquals(CommonPlatforms.defaultCommonPlatform, targetPlatform)
            }

            with(facetSettings("my-jvm-module")) {
                Assert.assertEquals(JvmPlatforms.jvm6, targetPlatform)
                Assert.assertEquals(listOf("my-common-module1", "my-common-module2"), implementedModuleNames)
            }

            with(facetSettings("my-js-module")) {
                Assert.assertEquals(JsPlatforms.defaultJsPlatform, targetPlatform)
                Assert.assertEquals(listOf("my-common-module1"), implementedModuleNames)
            }
        }
    }

    class ProductionOnTestDependency : AbstractKotlinMavenImporterTest() {
        @Test
        fun testProductionOnTestDependency() = runBlocking {
            createProjectSubDirs(
                "module-with-java/src/main/java",
                "module-with-java/src/test/java",
                "module-with-kotlin/src/main/kotlin",
                "module-with-kotlin/src/test/kotlin"
            )

            val dummyFile = createProjectSubFile(
                "module-with-kotlin/src/main/kotlin/foo/dummy.kt",
                """
                    package foo

                    fun dummy() {
                    }

                """.trimIndent()
            )

            val pomA = createModulePom(
                "module-with-java",
                """
                <parent>
                    <groupId>test-group</groupId>
                    <artifactId>mvnktest</artifactId>
                    <version>0.0.0.0-SNAPSHOT</version>
                </parent>

                <artifactId>module-with-java</artifactId>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-jar-plugin</artifactId>
                            <version>2.6</version>
                            <executions>
                                <execution>
                                    <goals>
                                        <goal>test-jar</goal>
                                    </goals>
                                </execution>
                            </executions>
                        </plugin>
                    </plugins>
                </build>
                """.trimIndent()
            )

            val pomB = createModulePom(
                "module-with-kotlin",
                """
                <parent>
                    <groupId>test-group</groupId>
                    <artifactId>mvnktest</artifactId>
                    <version>0.0.0.0-SNAPSHOT</version>
                </parent>

                <artifactId>module-with-kotlin</artifactId>

                <properties>
                    <kotlin.version>1.1.4</kotlin.version>
                    <kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>
                    <kotlin.compiler.incremental>true</kotlin.compiler.incremental>
                </properties>

                <dependencies>

                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>${"$"}{kotlin.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-runtime</artifactId>
                        <version>${"$"}{kotlin.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-reflect</artifactId>
                        <version>${"$"}{kotlin.version}</version>
                    </dependency>

                    <dependency>
                        <groupId>test-group</groupId>
                        <artifactId>module-with-java</artifactId>
                    </dependency>

                    <dependency>
                        <groupId>test-group</groupId>
                        <artifactId>module-with-java</artifactId>
                        <type>test-jar</type>
                        <scope>compile</scope>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <artifactId>kotlin-maven-plugin</artifactId>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <version>${"$"}{kotlin.version}</version>
                            <executions>
                                <execution>
                                    <id>compile</id>
                                    <goals> <goal>compile</goal> </goals>
                                    <configuration>
                                        <sourceDirs>
                                            <sourceDir>${"$"}{project.basedir}/src/main/kotlin</sourceDir>
                                            <sourceDir>${"$"}{project.basedir}/src/main/java</sourceDir>
                                        </sourceDirs>
                                    </configuration>
                                </execution>
                                <execution>
                                    <id>test-compile</id>
                                    <goals> <goal>test-compile</goal> </goals>
                                    <configuration>
                                        <sourceDirs>
                                            <sourceDir>${"$"}{project.basedir}/src/test/kotlin</sourceDir>
                                            <sourceDir>${"$"}{project.basedir}/src/test/java</sourceDir>
                                        </sourceDirs>
                                    </configuration>
                                </execution>
                            </executions>
                        </plugin>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.5.1</version>
                            <executions>
                                <!-- Replacing default-compile as it is treated specially by maven -->
                                <execution>
                                    <id>default-compile</id>
                                    <phase>none</phase>
                                </execution>
                                <!-- Replacing default-testCompile as it is treated specially by maven -->
                                <execution>
                                    <id>default-testCompile</id>
                                    <phase>none</phase>
                                </execution>
                                <execution>
                                    <id>java-compile</id>
                                    <phase>compile</phase>
                                    <goals> <goal>compile</goal> </goals>
                                </execution>
                                <execution>
                                    <id>java-test-compile</id>
                                    <phase>test-compile</phase>
                                    <goals> <goal>testCompile</goal> </goals>
                                </execution>
                            </executions>
                        </plugin>
                    </plugins>
                </build>
                """.trimIndent()
            )

            val pomMain = createModulePom(
                "",
                """
                <groupId>test-group</groupId>
                <artifactId>mvnktest</artifactId>
                <version>0.0.0.0-SNAPSHOT</version>

                <packaging>pom</packaging>

                <properties>
                    <kotlin.version>1.1.4</kotlin.version>
                    <kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>
                    <kotlin.compiler.incremental>true</kotlin.compiler.incremental>
                </properties>

                <modules>
                    <module>module-with-java</module>
                    <module>module-with-kotlin</module>
                </modules>

                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>test-group</groupId>
                            <artifactId>module-with-kotlin</artifactId>
                            <version>${"$"}{project.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>test-group</groupId>
                            <artifactId>module-with-java</artifactId>
                            <version>${"$"}{project.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>test-group</groupId>
                            <artifactId>module-with-java</artifactId>
                            <version>${"$"}{project.version}</version>
                            <type>test-jar</type>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
                """.trimIndent()
            )

            importProjectsAsync(pomMain, pomA, pomB)
            withContext(Dispatchers.EDT) {
                IndexingTestUtil.waitUntilIndexesAreReady(project)
            }
            assertModules("module-with-kotlin", "module-with-java", "mvnktest")

            val dependencies = readAction {
                val module = (dummyFile.toPsiFile(project) as KtFile).module
                module?.let { ModuleRootManager.getInstance(it).getDependencies(true).flatMap { it.toKaSourceModules() } }.orEmpty()
            }
            assertTrue(dependencies.any { it.name == "module-with-java" && it.sourceModuleKind == KaSourceModuleKind.PRODUCTION })
            assertTrue(dependencies.any { it.name == "module-with-java" && it.sourceModuleKind == KaSourceModuleKind.TEST })
        }
    }

    class NoArgDuplication6 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testNoArgDuplication() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

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
                            <args>
                                <arg>-Xjsr305=strict</arg>
                            </args>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            with(facetSettings) {
                Assert.assertEquals("-Xjsr305=strict", compilerSettings!!.additionalArguments)
            }
        }
    }

    class InternalArgumentsFacetImporting8 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testInternalArgumentsFacetImporting() = runBlocking {
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
                            <languageVersion>1.2</languageVersion>
                            <args>
                                <arg>-XXLanguage:+InlineClasses</arg>
                            </args>
                            <jvmTarget>1.8</jvmTarget>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            // Check that we haven't lost internal argument during importing to facet
            Assert.assertTrue("Argument is missing from compiler settings", "-XXLanguage:+InlineClasses" in facetSettings.compilerSettings!!.additionalArguments)

            // Check that internal argument influenced LanguageVersionSettings correctly
            Assert.assertEquals(
                LanguageFeature.State.ENABLED,
                getModule("project").languageVersionSettings.getFeatureSupport(LanguageFeature.InlineClasses)
            )
        }
    }

    class StableModuleNameWhileUsingMavenJVM : AbstractKotlinMavenImporterTest() {
        @Test
        fun testStableModuleNameWhileUsingMaven_JVM() = runBlocking {
            createProjectSubDirs("src/main/kotlin")

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
                            <languageVersion>1.2</languageVersion>
                            <jvmTarget>1.8</jvmTarget>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            checkStableModuleName("project", "project", JvmPlatforms.unspecifiedJvmPlatform, isProduction = true)
            checkStableModuleName("project", "project", JvmPlatforms.unspecifiedJvmPlatform, isProduction = false)
        }
    }

    class ImportObsoleteCodeStyle : AbstractKotlinMavenImporterTest() {
        @Test
        fun testImportObsoleteCodeStyle() = runBlocking {
            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <properties>
                <kotlin.code.style>obsolete</kotlin.code.style>
            </properties>
            """
            )

            Assert.assertEquals(
                KotlinObsoleteStyleGuide.CODE_STYLE_ID,
                CodeStyle.getSettings(project).kotlinCodeStyleDefaults()
            )
        }
    }

    class JavaParameters20 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJavaParameters() = runBlocking {
            createProjectSubDirs("src/main/kotlin")

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
                            <javaParameters>true</javaParameters>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            with(facetSettings) {
                Assert.assertEquals("-java-parameters", compilerSettings!!.additionalArguments)
                Assert.assertTrue((mergedCompilerArguments as K2JVMCompilerArguments).javaParameters)
            }
        }
    }

    class ArgsInFacet : AbstractKotlinMavenImporterTest() {
        @Test
        fun testArgsInFacet() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

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
                            <args>
                                <arg>-jvm-target</arg>
                                <arg>1.8</arg>
                                <arg>-Xcoroutines=enable</arg>
                                <arg>-classpath</arg>
                                <arg>c:\program files\jdk1.8</arg>
                            </args>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            with(facetSettings) {
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
                Assert.assertEquals("c:/program files/jdk1.8", (compilerArguments as K2JVMCompilerArguments).classpath)
            }
        }
    }

    @MppGoal
    class JsDetectionByGoalWithJvmStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsDetectionByGoalWithJvmStdlib() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

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
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>js</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <goals>
                                    <goal>test-js</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            Assert.assertTrue(facetSettings.targetPlatform.isJs())

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertDefaultKotlinResources("project")
            assertDefaultKotlinTestResources("project")
        }
    }

    @MppGoal
    class CommonDetectionByGoalWithJsStdlib24 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCommonDetectionByGoalWithJsStdlib() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-js</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>metadata</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            Assert.assertTrue(facetSettings.targetPlatform.isCommon())

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertDefaultKotlinResources("project")
            assertDefaultKotlinTestResources("project")
        }
    }

    class NoPluginsInAdditionalArgs : AbstractKotlinMavenImporterTest() {
        @Test
        fun testNoPluginsInAdditionalArgs() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

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
                        <executions>
                            <execution>
                                <id>compile</id>
                                <phase>compile</phase>
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                            </execution>
                        </executions>

                        <dependencies>
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-allopen</artifactId>
                                <version>$kotlinVersion</version>
                            </dependency>
                        </dependencies>

                        <configuration>
                            <compilerPlugins>
                                <plugin>spring</plugin>
                            </compilerPlugins>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")

            with(facetSettings) {
                Assert.assertEquals(
                    "",
                    compilerSettings!!.additionalArguments
                )
                Assert.assertEquals(
                    listOf(
                        "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.stereotype.Component",
                        "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.transaction.annotation.Transactional",
                        "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.scheduling.annotation.Async",
                        "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.cache.annotation.Cacheable",
                        "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.boot.test.context.SpringBootTest",
                        "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.validation.annotation.Validated"
                    ),
                    compilerArguments!!.pluginOptions!!.toList()
                )
            }
        }
    }

    class JDKImport : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJDKImport() = runBlocking {
            val mockJdk = IdeaTestUtil.getMockJdk18()
            runWriteAction(ThrowableRunnable {
                ProjectJdkTable.getInstance().addJdk(mockJdk, testFixture.testRootDisposable)
                ProjectRootManager.getInstance(project).projectSdk = mockJdk
            })

            try {
                createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")
                MavenWorkspaceSettingsComponent.getInstance(project).settings.importingSettings.jdkForImporter =
                    ExternalSystemJdkUtil.USE_INTERNAL_JAVA

                val jdkHomePath = mockJdk.homePath
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
                                <jdkHome>$jdkHomePath</jdkHome>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
                """
                )

                assertModules("project")

                val moduleSDK = ModuleRootManager.getInstance(getModule("project")).sdk!!
                Assert.assertTrue(moduleSDK.sdkType is JavaSdk)
                Assert.assertEquals("java 1.8", moduleSDK.name)
                Assert.assertEquals(jdkHomePath, moduleSDK.homePath)
            } finally {
                runWriteAction(ThrowableRunnable { ProjectRootManager.getInstance(project).projectSdk = null })
            }
        }
    }

    @MppGoal
    class StableModuleNameWhileUsngMavenJS : AbstractKotlinMavenImporterTest() {
        @Test
        fun testStableModuleNameWhileUsngMaven_JS() = runBlocking {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProjectAsync(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-js</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>

                        <executions>
                            <execution>
                                <id>compile</id>
                                <phase>compile</phase>
                                <goals>
                                    <goal>js</goal>
                                </goals>
                            </execution>
                        </executions>
                        <configuration>
                            <languageVersion>1.1</languageVersion>
                            <apiVersion>1.0</apiVersion>
                            <multiPlatform>true</multiPlatform>
                            <nowarn>true</nowarn>
                            <args>
                                <arg>-Xcoroutines=enable</arg>
                            </args>
                            <sourceMap>true</sourceMap>
                            <outputFile>test.js</outputFile>
                            <metaInfo>true</metaInfo>
                            <moduleKind>commonjs</moduleKind>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            // Note that we check name induced by '-output-file' -- may be it's not the best
            // decision, but we don't have a better one
            checkStableModuleName("project", "test", JsPlatforms.defaultJsPlatform, isProduction = true)
            checkStableModuleName("project", "test", JsPlatforms.defaultJsPlatform, isProduction = false)
        }
    }

    class JvmTarget6IsImportedAsIs : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmTargetIsImportedAsIs() = runBlocking {
            // If version isn't specified then we will fall back to bundled frontend which is already downloaded => Unbundled JPS can be used
            val (facet, notifications) = doJvmTarget6Test(version = null)
            Assert.assertEquals("JVM 1.6", facet.targetPlatform!!.oldFashionedDescription)
            Assert.assertEquals("1.6", (facet.compilerArguments as K2JVMCompilerArguments).jvmTarget)
            Assert.assertEquals("", notifications.asText())
        }
    }

    class JvmTarget6IsImported8 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmTarget6IsImported8() = runBlocking {
            // Some version won't be imported into JPS (because it's some milestone version which wasn't published to MC) => explicit
            // JPS version during import will be dropped => we will fall back to the bundled JPS =>
            // we have to load 1.6 jvmTarget as 1.8 KTIJ-21515
            val (facet, notifications) = doJvmTarget6Test("1.7.0-RC")

            Assert.assertEquals("JVM 1.8", facet.targetPlatform!!.oldFashionedDescription)
            Assert.assertEquals("1.8", (facet.compilerArguments as K2JVMCompilerArguments).jvmTarget)

            Assert.assertEquals(
                """
                    Title: 'Unsupported JVM target 1.6'
                    Content: 'Maven project uses JVM target 1.6 for Kotlin compilation, which is no longer supported. It has been imported as JVM target 1.8. Consider migrating the project to JVM 1.8.'
                """.trimIndent(),
                notifications.asText(),
            )
        }
    }

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

    class CompilerPlugins : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCompilerPlugins() = runBlocking {
            importProjectAsync(
                """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>$kotlinVersion</version>
                                <configuration>
                                    <compilerPlugins>
                                        <plugin>kotlinx-serialization</plugin>
                                        <plugin>all-open</plugin>
                                        <plugin>lombok</plugin>
                                        <plugin>jpa</plugin>
                                        <plugin>noarg</plugin>
                                        <plugin>sam-with-receiver</plugin>
                                    </compilerPlugins>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                """
            )

            Assert.assertEquals(
                "",
                facetSettings.compilerSettings!!.additionalArguments
            )
            assertModules("project")
            assertEquals(
                listOf(
                    KotlinArtifacts.allopenCompilerPluginPath,
                    KotlinArtifacts.kotlinxSerializationCompilerPluginPath,
                    KotlinArtifacts.lombokCompilerPluginPath,
                    KotlinArtifacts.noargCompilerPluginPath,
                    KotlinArtifacts.samWithReceiverCompilerPluginPath,
                ).map { it.toJpsVersionAgnosticKotlinBundledPath() },
                facetSettings.compilerArguments?.pluginClasspaths?.sorted()
            )
        }
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
