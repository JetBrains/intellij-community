// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven

import com.intellij.notification.Notification
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.notification.asText
import org.jetbrains.kotlin.idea.notification.catchNotificationsAsync
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class MavenMigrateTest : KotlinMavenImportingTestCase() {
    fun testMigrateApiAndLanguageVersions() = runBlocking {
        val notifications = doMigrationTest(
            before = """
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1.0.0</version>
    
                <properties>
                    <kotlin.version>1.5.31</kotlin.version>
                </properties>
    
                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>${'$'}{kotlin.version}</version>
                    </dependency>
                </dependencies>
    
                <build>
                    <plugins>
                        <plugin>
                            <artifactId>kotlin-maven-plugin</artifactId>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <version>${'$'}{kotlin.version}</version>
                        </plugin>
                    </plugins>
                </build>
            """.trimIndent(),
            after = """
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1.0.0</version>
    
                <properties>
                    <kotlin.version>1.5.31</kotlin.version>
                    <kotlin.compiler.languageVersion>1.6</kotlin.compiler.languageVersion>
                    <kotlin.compiler.apiVersion>1.6</kotlin.compiler.apiVersion>
                </properties>
    
                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>${'$'}{kotlin.version}</version>
                    </dependency>
                </dependencies>
    
                <build>
                    <plugins>
                        <plugin>
                            <artifactId>kotlin-maven-plugin</artifactId>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <version>${'$'}{kotlin.version}</version>
                        </plugin>
                    </plugins>
                </build>
            """.trimIndent(),
        )

        assertTrue(
            /* message = */ notifications.asText(),
            /* condition = */ notifications.any {
                it.content == "Update your code to replace the use of deprecated language and library features with supported constructs<br/><br/>Detected migration:<br/>&nbsp;&nbsp;Language version: 1.5 to 1.6<br/>&nbsp;&nbsp;API version: 1.5 to 1.6<br/>"
            }
        )
    }

    private suspend fun doMigrationTest(before: String, after: String): List<Notification> = catchNotificationsAsync(project) {
        importProjectAsync(before)
        importProjectAsync(after)
    }
}