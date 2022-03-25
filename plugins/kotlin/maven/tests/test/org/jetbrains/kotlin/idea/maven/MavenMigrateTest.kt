// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven

import org.jetbrains.kotlin.idea.notification.catchNotificationText
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class MavenMigrateTest : KotlinMavenImportingTestCase() {
    override fun setUp() {
        super.setUp()
        createStdProjectFolders()
    }

    fun testMigrateApiAndLanguageVersions() {
        val notificationText = doMigrationTest(
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

        assertEquals(
            "Migrations for Kotlin code are available<br/><br/>Detected migration:<br/>&nbsp;&nbsp;Language version: 1.5 -> 1.6<br/>&nbsp;&nbsp;API version: 1.5 -> 1.6<br/>",
            notificationText,
        )
    }

    private fun doMigrationTest(before: String, after: String): String? = catchNotificationText(myProject) {
        importProject(before)
        importProject(after)
    }
}