// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.importer

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.formatter.KotlinObsoleteCodeStyle
import org.jetbrains.kotlin.idea.formatter.kotlinCodeStyleDefaults
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinMavenImporterTest09 : AbstractKotlinMavenImporterTest() {
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
            org.junit.Assert.assertEquals("-java-parameters", compilerSettings!!.additionalArguments)
            org.junit.Assert.assertTrue((mergedCompilerArguments as K2JVMCompilerArguments).javaParameters)
        }
    }

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
