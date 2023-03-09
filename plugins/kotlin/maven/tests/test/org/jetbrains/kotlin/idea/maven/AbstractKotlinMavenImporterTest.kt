// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.application.options.CodeStyle
import com.intellij.facet.FacetManager
import com.intellij.notification.Notification
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.packaging.impl.artifacts.ArtifactUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.PathUtil
import com.intellij.util.ThrowableRunnable
import junit.framework.TestCase
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.additionalArgumentsAsList
import org.jetbrains.kotlin.idea.base.platforms.KotlinCommonLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.testSourceInfo
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAndGetResult
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.formatter.KotlinObsoleteCodeStyle
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle
import org.jetbrains.kotlin.idea.formatter.kotlinCodeStyleDefaults
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.macros.KOTLIN_BUNDLED
import org.jetbrains.kotlin.idea.notification.asText
import org.jetbrains.kotlin.idea.notification.catchNotificationText
import org.jetbrains.kotlin.idea.notification.catchNotifications
import org.jetbrains.kotlin.idea.test.resetCodeStyle
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.waitIndexingComplete
import org.jetbrains.kotlin.platform.*
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

abstract class AbstractKotlinMavenImporterTest : KotlinMavenImportingTestCase() {
    protected val kotlinVersion = "1.1.3"

    override fun setUp() {
        super.setUp()
        createStdProjectFolders()
    }

    override fun tearDown() = runAll(
        ThrowableRunnable { resetCodeStyle(myProject) },
        ThrowableRunnable { super.tearDown() },
    )

    protected fun checkStableModuleName(projectName: String, expectedName: String, platform: TargetPlatform, isProduction: Boolean) {
        val module = getModule(projectName)
        val moduleInfo = if (isProduction) module.productionSourceInfo else module.testSourceInfo

        val resolutionFacade = KotlinCacheService.getInstance(myProject).getResolutionFacadeByModuleInfo(moduleInfo!!, platform)!!
        val moduleDescriptor = resolutionFacade.moduleDescriptor

        Assert.assertEquals("<$expectedName>", moduleDescriptor.stableName?.asString())
    }

    protected fun facetSettings(moduleName: String) = KotlinFacet.get(getModule(moduleName))!!.configuration.settings

    protected val facetSettings: KotlinFacetSettings
        get() = facetSettings("project")

    protected fun assertImporterStatePresent() {
        assertNotNull("Kotlin importer component is not present", myTestFixture.module.getService(KotlinImporterComponent::class.java))
    }

    class SimpleKotlinProject5 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testSimpleKotlinProject() {
            importProject(
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
            """
            )

            assertModules("project")
            assertImporterStatePresent()
            assertSources("project", "src/main/java")
            assertFalse(ArtifactUtil.areResourceFilesFromSourceRootsCopiedToOutput(getModule("project")))
        }
    }

    class WithSpecifiedSourceRoot : AbstractKotlinMavenImporterTest() {
        @Test
        fun testWithSpecifiedSourceRoot() {
            createProjectSubDir("src/main/kotlin")

            importProject(
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
            </build>
            """
            )

            assertModules("project")
            assertImporterStatePresent()
            assertSources("project", "src/main/kotlin")
        }
    }

    class WithCustomSourceDirs12 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testWithCustomSourceDirs() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/main/kotlin</dir>
                                        <dir>src/main/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/test/kotlin</dir>
                                        <dir>src/test/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")
            assertImporterStatePresent()

            assertSources("project", "src/main/kotlin", "src/main/kotlin.jvm")
            assertTestSources("project", "src/test/java", "src/test/kotlin", "src/test/kotlin.jvm")
        }
    }

    class WithKapt : AbstractKotlinMavenImporterTest() {
        @Test
        fun testWithKapt() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>

                        <executions>
                            <execution>
                                <id>kapt</id>
                                <goals>
                                    <goal>kapt</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <sourceDir>src/main/kotlin</sourceDir>
                                        <sourceDir>src/main/java</sourceDir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>compile</id>
                                <phase>compile</phase>
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <sourceDir>src/main/kotlin</sourceDir>
                                        <sourceDir>src/main/java</sourceDir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>test-kapt</id>
                                <goals>
                                    <goal>test-kapt</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <sourceDir>src/test/kotlin</sourceDir>
                                        <sourceDir>src/test/java</sourceDir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <sourceDir>src/test/kotlin</sourceDir>
                                        <sourceDir>src/test/java</sourceDir>
                                        <sourceDir>target/generated-sources/kapt/test</sourceDir>
                                    </sourceDirs>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")
            assertImporterStatePresent()

            assertSources("project", "src/main/java", "src/main/kotlin")
            assertTestSources("project", "src/test/java", "src/test/kotlin")
        }
    }

    class ImportOfficialCodeStyle8 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testImportOfficialCodeStyle() {
            importProject(
                """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <properties>
                <kotlin.code.style>official</kotlin.code.style>
            </properties>
            """
            )

            Assert.assertEquals(
                KotlinStyleGuideCodeStyle.CODE_STYLE_ID,
                CodeStyle.getSettings(myProject).kotlinCodeStyleDefaults()
            )
        }
    }

    class ReImportRemoveDir : AbstractKotlinMavenImporterTest() {
        @Test
        fun testReImportRemoveDir() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/main/kotlin</dir>
                                        <dir>src/main/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/test/kotlin</dir>
                                        <dir>src/test/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")
            assertImporterStatePresent()

            assertSources("project", "src/main/kotlin", "src/main/kotlin.jvm")
            assertTestSources("project", "src/test/java", "src/test/kotlin", "src/test/kotlin.jvm")

            // reimport
            importProject(
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
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/main/kotlin</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/test/kotlin</dir>
                                        <dir>src/test/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertSources("project", "src/main/kotlin")
            assertTestSources("project", "src/test/java", "src/test/kotlin", "src/test/kotlin.jvm")
        }
    }

    class ReImportAddDir : AbstractKotlinMavenImporterTest() {
        @Test
        fun testReImportAddDir() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/main/kotlin</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/test/kotlin</dir>
                                        <dir>src/test/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")
            assertImporterStatePresent()

            assertSources("project", "src/main/kotlin")
            assertTestSources("project", "src/test/java", "src/test/kotlin", "src/test/kotlin.jvm")

            // reimport
            importProject(
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
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/main/kotlin</dir>
                                        <dir>src/main/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/test/kotlin</dir>
                                        <dir>src/test/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertSources("project", "src/main/kotlin", "src/main/kotlin.jvm")
            assertTestSources("project", "src/test/java", "src/test/kotlin", "src/test/kotlin.jvm")
        }
    }

    class JvmFacetConfiguration : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmFacetConfiguration() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            val kotlinMavenPluginVersion = "1.6.20"
            importProject(
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
                            <apiVersion>1.0</apiVersion>
                            <multiPlatform>true</multiPlatform>
                            <nowarn>true</nowarn>
                            <args>
                                <arg>-Xcoroutines=enable</arg>
                            </args>
                            <jvmTarget>1.8</jvmTarget>
                            <classpath>foobar.jar</classpath>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")
            assertImporterStatePresent()

            with(facetSettings) {
                Assert.assertEquals("1.1", languageLevel!!.versionString)
                Assert.assertEquals("1.1", compilerArguments!!.languageVersion)
                Assert.assertEquals("1.0", apiLevel!!.versionString)
                Assert.assertEquals("1.0", compilerArguments!!.apiVersion)
                Assert.assertFalse(compilerArguments!!.autoAdvanceLanguageVersion)
                Assert.assertFalse(compilerArguments!!.autoAdvanceApiVersion)
                Assert.assertEquals(true, compilerArguments!!.suppressWarnings)
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
                Assert.assertEquals("foobar.jar", (compilerArguments as K2JVMCompilerArguments).classpath)
                Assert.assertEquals(
                    "",
                    compilerSettings!!.additionalArguments
                )
            }

            Assert.assertEquals(kotlinMavenPluginVersion, KotlinJpsPluginSettings.jpsVersion(myProject))

            assertSources("project", "src/main/kotlin")
            assertTestSources("project", "src/test/java")
            assertResources("project", "src/main/resources")
            assertTestResources("project", "src/test/resources")
        }
    }

    class JvmFacetConfigurationFromProperties : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmFacetConfigurationFromProperties() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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

            <properties>
                <kotlin.compiler.languageVersion>1.0</kotlin.compiler.languageVersion>
                <kotlin.compiler.apiVersion>1.0</kotlin.compiler.apiVersion>
                <kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>
            </properties>

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
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")
            assertImporterStatePresent()

            with(facetSettings) {
                Assert.assertEquals("1.0", languageLevel!!.versionString)
                Assert.assertEquals("1.0", compilerArguments!!.languageVersion)
                Assert.assertEquals("1.0", apiLevel!!.versionString)
                Assert.assertEquals("1.0", compilerArguments!!.apiVersion)
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
            }

            assertSources("project", "src/main/kotlin")
            assertTestSources("project", "src/test/java")
            assertResources("project", "src/main/resources")
            assertTestResources("project", "src/test/resources")
        }
    }

    class JsFacetConfiguration : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsFacetConfiguration() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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

            assertModules("project")
            assertImporterStatePresent()

            with(facetSettings) {
                Assert.assertEquals("1.1", languageLevel!!.versionString)
                Assert.assertEquals("1.1", compilerArguments!!.languageVersion)
                Assert.assertEquals("1.0", apiLevel!!.versionString)
                Assert.assertEquals("1.0", compilerArguments!!.apiVersion)
                Assert.assertFalse(compilerArguments!!.autoAdvanceLanguageVersion)
                Assert.assertFalse(compilerArguments!!.autoAdvanceApiVersion)
                Assert.assertEquals(true, compilerArguments!!.suppressWarnings)
                Assert.assertTrue(targetPlatform.isJs())
                with(compilerArguments as K2JSCompilerArguments) {
                    Assert.assertEquals(true, sourceMap)
                    Assert.assertEquals("commonjs", moduleKind)
                }
                Assert.assertEquals(
                    "-meta-info -output test.js",
                    compilerSettings!!.additionalArguments
                )
            }

            val rootManager = ModuleRootManager.getInstance(getModule("project"))
            val stdlib = rootManager.orderEntries.filterIsInstance<LibraryOrderEntry>().single().library
            assertEquals(KotlinJavaScriptLibraryKind, (stdlib as LibraryEx).kind)

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertKotlinResources("project", "src/main/resources")
            assertKotlinTestResources("project", "src/test/resources")
        }
    }

    class JsCustomOutputPaths : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsCustomOutputPaths() {
            createProjectSubDirs("src/main/kotlin", "src/test/kotlin")
            importProject(
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
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <version>$kotlinVersion</version>

                        <executions>
                            <execution>
                                <id>compile</id>
                                <phase>compile</phase>
                                <goals>
                                    <goal>js</goal>
                                </goals>
                                <configuration>
                                    <outputFile>${'$'}{project.basedir}/prod/main.js</outputFile>
                                </configuration>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-js</goal>
                                </goals>
                                <configuration>
                                    <outputFile>${'$'}{project.basedir}/test/test.js</outputFile>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertNotEquals(kotlinVersion, KotlinJpsPluginSettings.jpsVersion(myProject))
            Assert.assertEquals(KotlinJpsPluginSettings.fallbackVersionForOutdatedCompiler, KotlinJpsPluginSettings.jpsVersion(myProject))

            assertModules("project")
            assertImporterStatePresent()

            val projectBasePath = myProjectsManager.projects.first().file.parent.path

            with(facetSettings) {
                Assert.assertEquals(
                    "$projectBasePath/prod/main.js",
                    PathUtil.toSystemIndependentName(productionOutputPath)
                )
                Assert.assertEquals(
                    "$projectBasePath/test/test.js",
                    PathUtil.toSystemIndependentName(testOutputPath)
                )
            }

            with(CompilerModuleExtension.getInstance(getModule("project"))!!) {
                Assert.assertEquals("$projectBasePath/prod", PathUtil.toSystemIndependentName(compilerOutputUrl))
                Assert.assertEquals("$projectBasePath/test", PathUtil.toSystemIndependentName(compilerOutputUrlForTests))
            }
        }
    }

    class FacetSplitConfiguration : AbstractKotlinMavenImporterTest() {
        @Test
        fun testFacetSplitConfiguration() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
                                <configuration>
                                    <languageVersion>1.1</languageVersion>
                                    <multiPlatform>true</multiPlatform>
                                    <args>
                                        <arg>-Xcoroutines=enable</arg>
                                    </args>
                                    <classpath>foobar.jar</classpath>
                                </configuration>
                            </execution>
                        </executions>
                        <configuration>
                            <apiVersion>1.0</apiVersion>
                            <nowarn>true</nowarn>
                            <jvmTarget>1.8</jvmTarget>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")
            assertImporterStatePresent()

            with(facetSettings) {
                Assert.assertEquals("1.1", languageLevel!!.versionString)
                Assert.assertEquals("1.1", compilerArguments!!.languageVersion)
                Assert.assertEquals("1.0", apiLevel!!.versionString)
                Assert.assertEquals("1.0", compilerArguments!!.apiVersion)
                Assert.assertEquals(true, compilerArguments!!.suppressWarnings)
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
                Assert.assertEquals("foobar.jar", (compilerArguments as K2JVMCompilerArguments).classpath)
                Assert.assertEquals("", compilerSettings!!.additionalArguments)
            }
        }
    }

    class ArgsInFacetInSingleElement : AbstractKotlinMavenImporterTest() {
        @Test
        fun testArgsInFacetInSingleElement() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
                                -jvm-target 1.8 -Xcoroutines=enable -classpath "c:\program files\jdk1.8"
                            </args>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")
            assertImporterStatePresent()

            with(facetSettings) {
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
                Assert.assertEquals("c:/program files/jdk1.8", (compilerArguments as K2JVMCompilerArguments).classpath)
            }
        }

        /**
         * This test check that the facet, that was created in older version of IJ and misses external source tag,
         *   is marked with external source after reimport
         */
        @Test
        fun testFacetGetsExternalSource() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")


            val module = invokeAndWaitIfNeeded {
                runWriteAction<Module, Throwable> {
                    val module = createModule("project")
                    FacetManager.getInstance(module).addFacet(KotlinFacetType.INSTANCE, "Kotlin", null)
                    module
                }
            }

            TestCase.assertNull(FacetManager.getInstance(module).allFacets.single().externalSource?.id)

            importProject(
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
                                -jvm-target 1.8 -Xcoroutines=enable -classpath "c:\program files\jdk1.8"
                            </args>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
            )

            assertModules("project")
            assertImporterStatePresent()

            val externalSource = FacetManager.getInstance(module).allFacets.single().externalSource
            TestCase.assertEquals("Maven", externalSource?.id)
        }
    }

    class JvmDetectionByGoalWithJvmStdlib6 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmDetectionByGoalWithJvmStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertEquals(JvmPlatforms.jvm6, facetSettings.targetPlatform)

            assertSources("project", "src/main/kotlin")
            assertTestSources("project", "src/test/java")
            assertResources("project", "src/main/resources")
            assertTestResources("project", "src/test/resources")
        }
    }

    class JvmDetectionByGoalWithJsStdlib15 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmDetectionByGoalWithJsStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertEquals(JvmPlatforms.jvm6, facetSettings.targetPlatform)
        }
    }

    class JvmDetectionByGoalWithCommonStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmDetectionByGoalWithCommonStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertEquals(JvmPlatforms.jvm6, facetSettings.targetPlatform)

            assertSources("project", "src/main/kotlin")
            assertTestSources("project", "src/test/java")
            assertResources("project", "src/main/resources")
            assertTestResources("project", "src/test/resources")
        }
    }

    class JsDetectionByGoalWithJsStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsDetectionByGoalWithJsStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertTrue(facetSettings.targetPlatform.isJs())

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertKotlinResources("project", "src/main/resources")
            assertKotlinTestResources("project", "src/test/resources")
        }
    }

    class JsDetectionByGoalWithCommonStdlib15 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsDetectionByGoalWithCommonStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertTrue(facetSettings.targetPlatform.isJs())

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertKotlinResources("project", "src/main/resources")
            assertKotlinTestResources("project", "src/test/resources")
        }
    }

    class JsAndCommonStdlibKinds : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsAndCommonStdlibKinds() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertTrue(facetSettings.targetPlatform.isJs())

            val rootManager = ModuleRootManager.getInstance(getModule("project"))
            val libraries = rootManager.orderEntries.filterIsInstance<LibraryOrderEntry>().map { it.library as LibraryEx }
            assertEquals(KotlinJavaScriptLibraryKind, libraries.single { it.name?.contains("kotlin-stdlib-js") == true }.kind)
            assertEquals(KotlinCommonLibraryKind, libraries.single { it.name?.contains("kotlin-stdlib-common") == true }.kind)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertKotlinResources("project", "src/main/resources")
            assertKotlinTestResources("project", "src/test/resources")
        }
    }

    class CommonDetectionByGoalWithJvmStdlib1164 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCommonDetectionByGoalWithJvmStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertTrue(facetSettings.targetPlatform.isCommon())

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertKotlinResources("project", "src/main/resources")
            assertKotlinTestResources("project", "src/test/resources")
        }
    }

    class CommonDetectionByGoalWithCommonStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCommonDetectionByGoalWithCommonStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
                """
            <groupId>test</groupId>0
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
            assertImporterStatePresent()

            Assert.assertTrue(facetSettings.targetPlatform.isCommon())

            val rootManager = ModuleRootManager.getInstance(getModule("project"))
            val stdlib = rootManager.orderEntries.filterIsInstance<LibraryOrderEntry>().single().library
            assertEquals(KotlinCommonLibraryKind, (stdlib as LibraryEx).kind)

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertKotlinResources("project", "src/main/resources")
            assertKotlinTestResources("project", "src/test/resources")
        }
    }

    class JvmDetectionByConflictingGoalsAndJvmStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmDetectionByConflictingGoalsAndJvmStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertEquals(JvmPlatforms.jvm6, facetSettings.targetPlatform)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertKotlinResources("project", "src/main/resources")
            assertKotlinTestResources("project", "src/test/resources")
        }
    }

    class JsDetectionByConflictingGoalsAndJsStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsDetectionByConflictingGoalsAndJsStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertTrue(facetSettings.targetPlatform.isJs())

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertKotlinResources("project", "src/main/resources")
            assertKotlinTestResources("project", "src/test/resources")
        }
    }

    class CommonDetectionByConflictingGoalsAndCommonStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCommonDetectionByConflictingGoalsAndCommonStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertTrue(facetSettings.targetPlatform.isCommon())

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertKotlinResources("project", "src/main/resources")
            assertKotlinTestResources("project", "src/test/resources")
        }
    }

    class NoArgInvokeInitializers : AbstractKotlinMavenImporterTest() {
        @Test
        fun testNoArgInvokeInitializers() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

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
        fun testArgsOverridingInFacet() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

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
        fun testSubmoduleArgsInheritance() {
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
                                <arg>-Xdump-declarations-to=dumpDir</arg>
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
                                    <arg>-Xdump-declarations-to=dumpDir2</arg>
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

            importProjects(mainPom, modulePom1, modulePom2, modulePom3)

            assertModules("project", "myModule1", "myModule2", "myModule3")
            assertImporterStatePresent()

            with(facetSettings("myModule1")) {
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals(LanguageVersion.KOTLIN_1_1, languageLevel!!)
                Assert.assertEquals(LanguageVersion.KOTLIN_1_0, apiLevel!!)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
                Assert.assertEquals(
                    listOf("-Xdump-declarations-to=dumpDir2"),
                    compilerSettings!!.additionalArgumentsAsList
                )
            }

            with(facetSettings("myModule2")) {
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals(LanguageVersion.KOTLIN_1_1, languageLevel!!)
                Assert.assertEquals(LanguageVersion.KOTLIN_1_0, apiLevel!!)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
                Assert.assertEquals(
                    listOf("-Xdump-declarations-to=dumpDir", "-java-parameters", "-kotlin-home", "temp2"),
                    compilerSettings!!.additionalArgumentsAsList
                )
            }

            with(facetSettings("myModule3")) {
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals(KotlinPluginLayout.standaloneCompilerVersion.languageVersion, languageLevel)
                Assert.assertEquals(LanguageVersion.KOTLIN_1_1, apiLevel)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
                Assert.assertEquals(
                    listOf("-kotlin-home", "temp2"),
                    compilerSettings!!.additionalArgumentsAsList
                )
            }
        }
    }

    class JpsCompilerMultiModule : AbstractKotlinMavenImporterTest() {
      override fun runInDispatchThread(): Boolean {
        return false
      }

      @Test
        fun testJpsCompilerMultiModule() {
            createProjectSubDirs(
                "src/main/kotlin",
                "module1/src/main/kotlin",
                "module2/src/main/kotlin",
            )

            val kotlinMainPluginVersion = "1.5.10"
            val kotlinMavenPluginVersion1 = "1.6.21"
            val kotlinMavenPluginVersion2 = "1.5.31"
            val notifications = catchNotifications(myProject, "Kotlin JPS plugin") {
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

                importProjects(mainPom, module1, module2)
            }

            assertModules("project", "module1", "module2")
            assertImporterStatePresent()
            assertEquals("", notifications.asText())
            // The highest of available versions should be picked
            assertEquals(kotlinMavenPluginVersion1, KotlinJpsPluginSettings.jpsVersion(myProject))
        }
    }

    class JpsCompiler : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJpsCompilerUnsupportedVersionDown() {
            val version = "1.1.0"
            val notifications = catchNotifications(myProject) {
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
        fun testJpsCompilerUnsupportedVersionUp() {
            val maxVersion = KotlinJpsPluginSettings.jpsMaximumSupportedVersion
            val versionToImport = KotlinVersion(maxVersion.major, maxVersion.minor, maxVersion.minor + 1)
            val text = catchNotificationText(myProject, "Kotlin JPS plugin") {
                doUnsupportedVersionTest(versionToImport.toString())
            }

            assertEquals(
                "Version (${KotlinJpsPluginSettings.rawBundledVersion}) of the Kotlin JPS plugin will be used<br>" +
                        "The reason: Kotlin JPS compiler maximum supported version is '$maxVersion' but '$versionToImport' is specified",
                text
            )
        }

        private fun doUnsupportedVersionTest(version: String, expectedFallbackVersion: String = KotlinJpsPluginSettings.rawBundledVersion) {
            createProjectSubDirs("src/main/kotlin")

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
                                <version>$version</version>
                            </plugin>
                        </plugins>
                    </build>
                """
            )

            importProjects(mainPom)

            assertModules("project")
            assertImporterStatePresent()

            // Fallback to bundled to unsupported version
            assertNotEquals(version, KotlinJpsPluginSettings.jpsVersion(myProject))
            assertEquals(expectedFallbackVersion, KotlinJpsPluginSettings.jpsVersion(myProject))
        }

        @Test
        fun testDontShowNotificationWhenBuildIsDelegatedToMaven() {
            val isBuildDelegatedToMaven = MavenRunner.getInstance(myProject).settings.isDelegateBuildToMaven
            MavenRunner.getInstance(myProject).settings.isDelegateBuildToMaven = true

            try {
                val version = "1.1.0"
                val notifications = catchNotifications(myProject) {
                    doUnsupportedVersionTest(version, KotlinJpsPluginSettings.fallbackVersionForOutdatedCompiler)
                }

                assertNull(notifications.find { it.title == "Unsupported Kotlin JPS plugin version" })
            } finally {
                MavenRunner.getInstance(myProject).settings.isDelegateBuildToMaven = isBuildDelegatedToMaven
            }
        }
    }

    class MultiModuleImport : AbstractKotlinMavenImporterTest() {
        @Test
        fun testMultiModuleImport() {
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

            importProjects(mainPom, commonModule1, commonModule2, jvmModule, jsModule)

            assertModules("project", "my-common-module1", "my-common-module2", "my-jvm-module", "my-js-module")
            assertImporterStatePresent()

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
        fun testProductionOnTestDependency() {
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

            importProjects(pomMain, pomA, pomB)
            myProject.waitIndexingComplete()
            assertModules("module-with-kotlin", "module-with-java", "mvnktest")

            val dependencies = (dummyFile.toPsiFile(myProject) as KtFile).analyzeAndGetResult().moduleDescriptor.allDependencyModules
            TestCase.assertTrue(dependencies.any { it.name.asString() == "<production sources for module module-with-java>" })
            TestCase.assertTrue(dependencies.any { it.name.asString() == "<test sources for module module-with-java>" })
        }
    }

    class NoArgDuplication6 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testNoArgDuplication() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            with(facetSettings) {
                Assert.assertEquals("-Xjsr305=strict", compilerSettings!!.additionalArguments)
            }
        }
    }

    class InternalArgumentsFacetImporting8 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testInternalArgumentsFacetImporting() {
            importProject(
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

            assertImporterStatePresent()

            // Check that we haven't lost internal argument during importing to facet
            Assert.assertEquals("-XXLanguage:+InlineClasses", facetSettings.compilerSettings?.additionalArguments)

            // Check that internal argument influenced LanguageVersionSettings correctly
            Assert.assertEquals(
                LanguageFeature.State.ENABLED,
                getModule("project").languageVersionSettings.getFeatureSupport(LanguageFeature.InlineClasses)
            )
        }
    }

    class StableModuleNameWhileUsingMavenJVM : AbstractKotlinMavenImporterTest() {
        @Test
        fun testStableModuleNameWhileUsingMaven_JVM() {
            createProjectSubDirs("src/main/kotlin")

            importProject(
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

            assertImporterStatePresent()

            checkStableModuleName("project", "project", JvmPlatforms.unspecifiedJvmPlatform, isProduction = true)
            checkStableModuleName("project", "project", JvmPlatforms.unspecifiedJvmPlatform, isProduction = false)
        }
    }

    class ImportObsoleteCodeStyle : AbstractKotlinMavenImporterTest() {
        @Test
        fun testImportObsoleteCodeStyle() {
            importProject(
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
                KotlinObsoleteCodeStyle.CODE_STYLE_ID,
                CodeStyle.getSettings(myProject).kotlinCodeStyleDefaults()
            )
        }
    }

    class JavaParameters20 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJavaParameters() {
            createProjectSubDirs("src/main/kotlin")

            importProject(
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
            assertImporterStatePresent()

            with(facetSettings) {
                Assert.assertEquals("-java-parameters", compilerSettings!!.additionalArguments)
                Assert.assertTrue((mergedCompilerArguments as K2JVMCompilerArguments).javaParameters)
            }
        }
    }

    class ArgsInFacet : AbstractKotlinMavenImporterTest() {
        @Test
        fun testArgsInFacet() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            with(facetSettings) {
                Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
                Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
                Assert.assertEquals("c:/program files/jdk1.8", (compilerArguments as K2JVMCompilerArguments).classpath)
            }
        }
    }

    class JsDetectionByGoalWithJvmStdlib : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJsDetectionByGoalWithJvmStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertTrue(facetSettings.targetPlatform.isJs())

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertKotlinResources("project", "src/main/resources")
            assertKotlinTestResources("project", "src/test/resources")
        }
    }

    class CommonDetectionByGoalWithJsStdlib24 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCommonDetectionByGoalWithJsStdlib() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

            Assert.assertTrue(facetSettings.targetPlatform.isCommon())

            Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

            assertKotlinSources("project", "src/main/kotlin")
            assertKotlinTestSources("project", "src/test/java")
            assertKotlinResources("project", "src/main/resources")
            assertKotlinTestResources("project", "src/test/resources")
        }
    }

    class NoPluginsInAdditionalArgs : AbstractKotlinMavenImporterTest() {
        @Test
        fun testNoPluginsInAdditionalArgs() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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
            assertImporterStatePresent()

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
        fun testJDKImport() {
            val mockJdk = IdeaTestUtil.getMockJdk18()
            runWriteAction(ThrowableRunnable {
                ProjectJdkTable.getInstance().addJdk(mockJdk, myTestFixture.testRootDisposable)
                ProjectRootManager.getInstance(myProject).projectSdk = mockJdk
            })

            try {
                createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")
                MavenWorkspaceSettingsComponent.getInstance(myProject).settings.getImportingSettings().jdkForImporter =
                    ExternalSystemJdkUtil.USE_INTERNAL_JAVA

                val jdkHomePath = mockJdk.homePath
                importProject(
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
                assertImporterStatePresent()

                val moduleSDK = ModuleRootManager.getInstance(getModule("project")).sdk!!
                Assert.assertTrue(moduleSDK.sdkType is JavaSdk)
                Assert.assertEquals("java 1.8", moduleSDK.name)
                Assert.assertEquals(jdkHomePath, moduleSDK.homePath)
            } finally {
                runWriteAction(ThrowableRunnable { ProjectRootManager.getInstance(myProject).projectSdk = null })
            }
        }
    }

    class StableModuleNameWhileUsngMavenJS : AbstractKotlinMavenImporterTest() {
        @Test
        fun testStableModuleNameWhileUsngMaven_JS() {
            createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

            importProject(
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

            assertImporterStatePresent()

            // Note that we check name induced by '-output-file' -- may be it's not the best
            // decision, but we don't have a better one
            checkStableModuleName("project", "test", JsPlatforms.defaultJsPlatform, isProduction = true)
            checkStableModuleName("project", "test", JsPlatforms.defaultJsPlatform, isProduction = false)
        }
    }

    class JvmTarget6IsImportedAsIs : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmTargetIsImportedAsIs() {
            // If version isn't specified then we will fall back to bundled frontend which is already downloaded => Unbundled JPS can be used
            val (facet, notifications) = doJvmTarget6Test(version = null)
            Assert.assertEquals("JVM 1.6", facet.targetPlatform!!.oldFashionedDescription)
            Assert.assertEquals("1.6", (facet.compilerArguments as K2JVMCompilerArguments).jvmTarget)
            Assert.assertEquals("", notifications.asText())
        }
    }

    class JvmTarget6IsImported8 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmTarget6IsImported8() {
            // Some version won't be imported into JPS (because it's some milestone version which wasn't published to MC) => explicit
            // JPS version during import will be dropped => we will fall back to the bundled JPS =>
            // we have to load 1.6 jvmTarget as 1.8 KTIJ-21515
            val (facet, notifications) = doJvmTarget6Test("1.6.20-RC")

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

    protected fun doJvmTarget6Test(version: String?): Pair<KotlinFacetSettings, List<Notification>> {
        createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        val notifications = catchNotifications(myProject, "Kotlin Maven project import") {
            importProject(
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
                                ${version?.let { "<version>$it</version>" }}

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
        assertImporterStatePresent()

        return facetSettings to notifications
    }

    class CompilerPlugins : AbstractKotlinMavenImporterTest() {
        @Test
        fun testCompilerPlugins() {
            importProject(
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
            assertImporterStatePresent()
            TestCase.assertEquals(
                listOf(
                    KotlinArtifacts.allopenCompilerPlugin,
                    KotlinArtifacts.kotlinxSerializationCompilerPlugin,
                    KotlinArtifacts.lombokCompilerPlugin,
                    KotlinArtifacts.noargCompilerPlugin,
                    KotlinArtifacts.samWithReceiverCompilerPlugin,
                ).map { it.toJpsVersionAgnosticKotlinBundledPath() },
                facetSettings.compilerArguments?.pluginClasspaths?.sorted()
            )
        }
    }
}

fun File.toJpsVersionAgnosticKotlinBundledPath(): String {
    val kotlincDirectory = KotlinPluginLayout.kotlinc
    require(this.startsWith(kotlincDirectory)) { "$this should start with ${kotlincDirectory}" }
    return "\$$KOTLIN_BUNDLED\$/${this.relativeTo(kotlincDirectory)}"
}
