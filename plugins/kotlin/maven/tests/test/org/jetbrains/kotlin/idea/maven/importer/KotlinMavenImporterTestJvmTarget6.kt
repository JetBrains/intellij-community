// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.importer

import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.notification.catchNotificationText
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.Assert
import org.junit.Test
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinMavenImporterTestJvmTarget6 : AbstractKotlinMavenImporterTest() {
        @Test
        fun testJvmFacetConfiguration() {
            val notificationText = doTest()
            assertEquals(
                "Maven project uses JVM target 1.6 for Kotlin compilation, which is no longer supported. " +
                "It has been imported as JVM target 1.8. Consider migrating the project to JVM 1.8.",
                notificationText,
            )
        }

        private fun doTest(): String? = catchNotificationText(myProject) {
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
                            <jvmTarget>1.6</jvmTarget>
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
            }
        }
    }
