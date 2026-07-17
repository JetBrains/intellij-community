// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.notification.Notification
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.idea.notification.asText
import org.jetbrains.kotlin.idea.notification.catchNotificationsAsync
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class JvmTarget6IsImportedAsIsTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testJvmTargetIsImportedAsIs() = runBlocking {
        // If version isn't specified then we will fall back to bundled frontend which is already downloaded => Unbundled JPS can be used
        val (facet, notifications) = doJvmTarget6Test(version = null)
        Assertions.assertEquals("JVM 1.6", facet.targetPlatform!!.oldFashionedDescription)
        Assertions.assertEquals("1.6", (facet.compilerArguments as K2JVMCompilerArguments).jvmTarget)
        Assertions.assertEquals("", notifications.asText())
    }

    private suspend fun doJvmTarget6Test(version: String?): Pair<IKotlinFacetSettings, List<Notification>> {
        maven.createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        val notifications = catchNotificationsAsync(project, "Kotlin Maven project import", maven.testRootDisposable) {
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

        maven.assertModules("project")

        return facetSettings to notifications
    }
}
