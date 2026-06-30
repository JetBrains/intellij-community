// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.openapi.project.modules
import com.intellij.testFramework.junit5.TestApplication
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.configuration.hasKotlinPluginEnabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class HasKotlinPluginEnabledTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testSingleModuleNoKotlin() = runBlocking {
        maven.importProjectAsync(
            """
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1.0.0</version>

<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
"""
        )
        TestCase.assertTrue(project.modules.none { it.hasKotlinPluginEnabled() })
    }

    @Test
    fun testTransitiveDependencyOnly() = runBlocking {
        maven.importProjectAsync(
            """
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1.0.0</version>

<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
<dependencies>
    <dependency>
        <groupId>io.ktor</groupId>
        <artifactId>ktor-server-core-jvm</artifactId>
        <version>2.3.1</version>
    </dependency>
</dependencies>
"""
        )
        TestCase.assertTrue(project.modules.none { it.hasKotlinPluginEnabled() })
    }

    @Test
    fun testSingleModuleKotlin() = runBlocking {
        maven.importProjectAsync(
            """
<artifactId>project</artifactId>
<groupId>test</groupId>
<version>1.0.0</version>

<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <kotlin.code.style>official</kotlin.code.style>
    <kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>
</properties>

<build>
    <sourceDirectory>src/main/kotlin</sourceDirectory>
    <testSourceDirectory>src/test/kotlin</testSourceDirectory>
    <plugins>
        <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-plugin</artifactId>
            <version>1.8.21</version>
            <executions>
                <execution>
                    <id>compile</id>
                    <phase>compile</phase>
                    <goals>
                        <goal>compile</goal>
                    </goals>
                </execution>
                <execution>
                    <id>test-compile</id>
                    <phase>test-compile</phase>
                    <goals>
                        <goal>test-compile</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        <plugin>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>2.22.2</version>
        </plugin>
        <plugin>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>2.22.2</version>
        </plugin>
    </plugins>
</build>

<dependencies>
    <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-test-junit5</artifactId>
        <version>1.8.21</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-engine</artifactId>
        <version>5.8.2</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-stdlib</artifactId>
        <version>1.8.21</version>
    </dependency>
</dependencies>
"""
        )
        TestCase.assertEquals(project.modules.count { it.hasKotlinPluginEnabled() }, 1)
    }

    @Test
    fun testChildOnlyKotlin() = runBlocking {
        maven.createProjectSubDirs(
            "submodule",
        )
        val projectPom = maven.createProjectPom(
    """
<groupId>org.example</groupId>
<artifactId>project</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>
<modules>
    <module>submodule</module>
</modules>

<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
        """)

        val submodulePom = maven.createModulePom(
            "submodule",
        """
<parent>
    <groupId>org.example</groupId>
    <artifactId>project</artifactId>
    <version>1.0.0</version>
</parent>

<artifactId>submodule</artifactId>

<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <kotlin.version>1.8.22</kotlin.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-stdlib-jdk8</artifactId>
        <version>${"$"}{kotlin.version}</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-test</artifactId>
        <version>${"$"}{kotlin.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-plugin</artifactId>
            <version>${"$"}{kotlin.version}</version>
            <executions>
                <execution>
                    <id>compile</id>
                    <phase>compile</phase>
                    <goals>
                        <goal>compile</goal>
                    </goals>
                </execution>
                <execution>
                    <id>test-compile</id>
                    <phase>test-compile</phase>
                    <goals>
                        <goal>test-compile</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <jvmTarget>${"$"}{maven.compiler.target}</jvmTarget>
            </configuration>
        </plugin>
    </plugins>
</build>
            """
        )

        maven.importProjectsAsync(projectPom, submodulePom)

        val kotlinModules = project.modules.filter { it.hasKotlinPluginEnabled() }
        TestCase.assertEquals(1, kotlinModules.size)
        TestCase.assertEquals("submodule", kotlinModules.first().name)
    }

    @Test
    fun testSingleChildKotlin() = runBlocking {
        maven.createProjectSubDirs(
            "submoduleA",
            "submoduleB",
        )
        val projectPom = maven.createProjectPom(
            """
<groupId>org.example</groupId>
<artifactId>project</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>
<modules>
    <module>submoduleA</module>
    <module>submoduleB</module>
</modules>

<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
        """)

        val submoduleAPom = maven.createModulePom(
            "submoduleA",
            """
<parent>
    <groupId>org.example</groupId>
    <artifactId>project</artifactId>
    <version>1.0.0</version>
</parent>

<artifactId>submoduleA</artifactId>

<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <kotlin.version>1.8.22</kotlin.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-stdlib-jdk8</artifactId>
        <version>${"$"}{kotlin.version}</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-test</artifactId>
        <version>${"$"}{kotlin.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-plugin</artifactId>
            <version>${"$"}{kotlin.version}</version>
            <executions>
                <execution>
                    <id>compile</id>
                    <phase>compile</phase>
                    <goals>
                        <goal>compile</goal>
                    </goals>
                </execution>
                <execution>
                    <id>test-compile</id>
                    <phase>test-compile</phase>
                    <goals>
                        <goal>test-compile</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <jvmTarget>${"$"}{maven.compiler.target}</jvmTarget>
            </configuration>
        </plugin>
    </plugins>
</build>
            """
        )



        val submoduleBPom = maven.createModulePom(
            "submoduleB",
            """
<groupId>test</groupId>
<artifactId>submoduleB</artifactId>
<version>1.0.0</version>

<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
"""
        )

        maven.importProjectsAsync(projectPom, submoduleAPom, submoduleBPom)

        val kotlinModules = project.modules.filter { it.hasKotlinPluginEnabled() }
        TestCase.assertEquals(1, kotlinModules.size)
        TestCase.assertEquals("submoduleA", kotlinModules.first().name)
    }
}