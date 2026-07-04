// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.runWriteAction
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class JDKImportTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testJDKImport() = runBlocking {
        val mockJdk = IdeaTestUtil.getMockJdk18()
        maven.runWriteAction(ThrowableRunnable {
            ProjectJdkTable.getInstance().addJdk(mockJdk, maven.testRootDisposable)
            ProjectRootManager.getInstance(project).projectSdk = mockJdk
        })

        try {
            maven.createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")
            MavenWorkspaceSettingsComponent.getInstance(project).settings.importingSettings.jdkForImporter =
                ExternalSystemJdkUtil.USE_INTERNAL_JAVA

            val jdkHomePath = mockJdk.homePath
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
                                <jdkHome>$jdkHomePath</jdkHome>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
                """
            )

            maven.assertModules("project")

            val moduleSDK = ModuleRootManager.getInstance(maven.getModule("project")).sdk!!
            Assert.assertTrue(moduleSDK.sdkType is JavaSdk)
            Assert.assertEquals("java 1.8", moduleSDK.name)
            Assert.assertEquals(jdkHomePath, moduleSDK.homePath)
        } finally {
            maven.runWriteAction(ThrowableRunnable { ProjectRootManager.getInstance(project).projectSdk = null })
        }
    }
}
