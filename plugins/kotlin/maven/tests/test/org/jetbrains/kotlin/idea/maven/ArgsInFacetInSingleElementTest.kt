// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.facet.FacetManager
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModule
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.runWriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.module.Module
import com.intellij.testFramework.junit5.TestApplication
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.Assert
import org.junit.Assert.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class ArgsInFacetInSingleElementTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testArgsInFacetInSingleElement() = runBlocking {
        maven.createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        maven.importProjectAsync(
            /* xml = */ """
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

        maven.assertModules("project")

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
    fun testFacetGetsExternalSource() = runBlocking {

        maven.createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")


        val module = invokeAndWaitIfNeeded {
            maven.runWriteAction<Module, Throwable> {
                val module = maven.createModule("project")
                FacetManager.getInstance(module).addFacet(KotlinFacetType.INSTANCE, "Kotlin", null)
                module
            }
        }

        assertNull(FacetManager.getInstance(module).allFacets.single().externalSource?.id)

        maven.importProjectAsync(
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

        maven.assertModules("project")

        val externalSource = FacetManager.getInstance(module).allFacets.single().externalSource
        TestCase.assertEquals("Maven", externalSource?.id)
    }
}
