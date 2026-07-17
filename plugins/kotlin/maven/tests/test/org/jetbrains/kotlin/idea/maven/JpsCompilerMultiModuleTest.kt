// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.notification.asText
import org.jetbrains.kotlin.idea.notification.catchNotificationsAsync
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class JpsCompilerMultiModuleTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testJpsCompilerMultiModule() = runBlocking {
        maven.createProjectSubDirs(
            "src/main/kotlin",
            "module1/src/main/kotlin",
            "module2/src/main/kotlin",
        )

        val kotlinMainPluginVersion = "1.5.10"
        val kotlinMavenPluginVersion1 = "1.7.21"
        val kotlinMavenPluginVersion2 = "1.5.31"
        val notifications = catchNotificationsAsync(project, "Kotlin JPS plugin", maven.testRootDisposable) {
            val mainPom = maven.createProjectPom(
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

            val module1 = maven.createModulePom(
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

            val module2 = maven.createModulePom(
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

            maven.importProjectsAsync(mainPom, module1, module2)
        }

        maven.assertModules("project", "module1", "module2")
        assertEquals("", notifications.asText())
        // The highest of available versions should be picked
        assertEquals(kotlinMavenPluginVersion1, KotlinJpsPluginSettings.jpsVersion(project))
    }
}
