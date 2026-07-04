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
