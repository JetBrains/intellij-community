// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven.importer

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.maven.AbstractKotlinMavenImporterTest
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class AllOpenKotlinMavenImporterTest : AbstractKotlinMavenImporterTest() {

    @Test
    fun testAllOpenIsNotEnabledViaJpaPluginInOldKotlinVersions() = runBlocking {
        createProjectSubDirs("src/main/kotlin", "src/test/kotlin")

        importProjectAsync(projectWithCompilerPlugins(TestVersions.Kotlin.KOTLIN_2_3_10, "jpa"))

        assertModules("project")

        with(facetSettings) {
            val pluginClasspaths = compilerArguments?.pluginClasspaths ?: error("'compilerArguments?.pluginClasspaths' must not be null")
            assertFalse(pluginClasspaths.any { it.endsWith(KotlinArtifacts.allopenCompilerPluginPath.fileName.toString()) })
            assertTrue(pluginClasspaths.any { it.endsWith(KotlinArtifacts.noargCompilerPluginPath.fileName.toString()) })
        }
    }

    @Test
    fun testAllOpenIsNotEnabledViaJpaAndSpringPluginInOldKotlinVersions() = runBlocking {
        createProjectSubDirs("src/main/kotlin", "src/test/kotlin")

        importProjectAsync(projectWithCompilerPlugins(TestVersions.Kotlin.KOTLIN_2_3_10, "jpa", "spring"))

        assertModules("project")

        with(facetSettings) {
            val pluginClasspaths = compilerArguments?.pluginClasspaths ?: error("'compilerArguments?.pluginClasspaths' must not be null")
            assertTrue(
                pluginClasspaths.joinToString { it },
                pluginClasspaths.any { it.endsWith(KotlinArtifacts.allopenCompilerPluginPath.fileName.toString()) })
            assertTrue(
                pluginClasspaths.joinToString { it },
                pluginClasspaths.any { it.endsWith(KotlinArtifacts.noargCompilerPluginPath.fileName.toString()) })
        }
    }

    @Test
    fun testAllOpenIsEnabledViaJpaPlugin() = runBlocking {
        createProjectSubDirs("src/main/kotlin", "src/test/kotlin")

        importProjectAsync(projectWithCompilerPlugins(TestVersions.Kotlin.KOTLIN_2_3_20, "jpa"))

        assertModules("project")

        with(facetSettings) {
            val pluginClasspaths = compilerArguments?.pluginClasspaths ?: error("'compilerArguments?.pluginClasspaths' must not be null")
            assertTrue(pluginClasspaths.any { it.endsWith(KotlinArtifacts.allopenCompilerPluginPath.fileName.toString()) })
            assertTrue(pluginClasspaths.any { it.endsWith(KotlinArtifacts.noargCompilerPluginPath.fileName.toString()) })
            val pluginOptions = compilerArguments?.pluginOptions ?: error("'compilerArguments?.pluginOptions' must not be null")
            assertArrayEquals(
                pluginOptions,
                arrayOf(
                    "plugin:org.jetbrains.kotlin.noarg:annotation=javax.persistence.Entity",
                    "plugin:org.jetbrains.kotlin.noarg:annotation=javax.persistence.Embeddable",
                    "plugin:org.jetbrains.kotlin.noarg:annotation=javax.persistence.MappedSuperclass",
                    "plugin:org.jetbrains.kotlin.noarg:annotation=jakarta.persistence.Entity",
                    "plugin:org.jetbrains.kotlin.noarg:annotation=jakarta.persistence.Embeddable",
                    "plugin:org.jetbrains.kotlin.noarg:annotation=jakarta.persistence.MappedSuperclass",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=javax.persistence.Entity",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=javax.persistence.Embeddable",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=javax.persistence.MappedSuperclass",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=jakarta.persistence.Entity",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=jakarta.persistence.Embeddable",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=jakarta.persistence.MappedSuperclass",
                )
            )
        }
    }

    @Test
    fun testAllOpenIsEnabledViaJpaAndSpringPlugin() = runBlocking {
        createProjectSubDirs("src/main/kotlin", "src/test/kotlin")

        importProjectAsync(projectWithCompilerPlugins(TestVersions.Kotlin.KOTLIN_2_3_20, "jpa", "spring"))

        assertModules("project")

        with(facetSettings) {
            val pluginClasspaths = compilerArguments?.pluginClasspaths ?: error("'compilerArguments?.pluginClasspaths' must not be null")
            assertTrue(pluginClasspaths.any { it.endsWith(KotlinArtifacts.allopenCompilerPluginPath.fileName.toString()) })
            assertTrue(pluginClasspaths.any { it.endsWith(KotlinArtifacts.noargCompilerPluginPath.fileName.toString()) })
            val pluginOptions = compilerArguments?.pluginOptions ?: error("'compilerArguments?.pluginOptions' must not be null")
            assertArrayEquals(
                pluginOptions,
                arrayOf(
                    "plugin:org.jetbrains.kotlin.noarg:annotation=javax.persistence.Entity",
                    "plugin:org.jetbrains.kotlin.noarg:annotation=javax.persistence.Embeddable",
                    "plugin:org.jetbrains.kotlin.noarg:annotation=javax.persistence.MappedSuperclass",
                    "plugin:org.jetbrains.kotlin.noarg:annotation=jakarta.persistence.Entity",
                    "plugin:org.jetbrains.kotlin.noarg:annotation=jakarta.persistence.Embeddable",
                    "plugin:org.jetbrains.kotlin.noarg:annotation=jakarta.persistence.MappedSuperclass",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.stereotype.Component",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.transaction.annotation.Transactional",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.scheduling.annotation.Async",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.cache.annotation.Cacheable",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.boot.test.context.SpringBootTest",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=org.springframework.validation.annotation.Validated",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=javax.persistence.Entity",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=javax.persistence.Embeddable",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=javax.persistence.MappedSuperclass",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=jakarta.persistence.Entity",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=jakarta.persistence.Embeddable",
                    "plugin:org.jetbrains.kotlin.allopen:annotation=jakarta.persistence.MappedSuperclass"
                )
            )
        }
    }

    private fun projectWithCompilerPlugins(kotlinVersion: String, vararg plugins: String) =
        //language=xml
        """
            |<groupId>test</groupId>
            |<artifactId>project</artifactId>
            |<version>1.0.0</version>
            |
            |<properties>
            |    <kotlin.version>${kotlinVersion}</kotlin.version>
            |</properties>
            |
            |<build>
            |    <sourceDirectory>src/main/kotlin</sourceDirectory>
            |    <testSourceDirectory>src/test/kotlin</testSourceDirectory>
            |    <plugins>
            |        <plugin>
            |            <groupId>org.jetbrains.kotlin</groupId>
            |            <artifactId>kotlin-maven-plugin</artifactId>
            |            <version>${'$'}{kotlin.version}</version>
            |            <configuration>
            |                <compilerPlugins>
            |                    ${plugins.joinToString("\n") { "<plugin>${it}</plugin>" }}
            |                </compilerPlugins>
            |            </configuration>
            |            <executions>
            |                <execution>
            |                    <id>compile</id>
            |                    <phase>compile</phase>
            |                    <goals>
            |                        <goal>compile</goal>
            |                    </goals>
            |                </execution>
            |                <execution>
            |                    <id>test-compile</id>
            |                    <phase>test-compile</phase>
            |                    <goals>
            |                        <goal>test-compile</goal>
            |                    </goals>
            |                </execution>
            |            </executions>
            |            <dependencies>
            |                <dependency>
            |                    <groupId>org.jetbrains.kotlin</groupId>
            |                    <artifactId>kotlin-maven-noarg</artifactId>
            |                    <version>${'$'}{kotlin.version}</version>
            |                </dependency>
            |            </dependencies>
            |        </plugin>
            |    </plugins>
            |</build>
            """.trimMargin()
}
