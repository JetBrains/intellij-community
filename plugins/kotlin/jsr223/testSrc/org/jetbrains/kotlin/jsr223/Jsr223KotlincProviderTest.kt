// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class Jsr223KotlincProviderTest {
    private val ideVersion: String get() = KotlinCompilerVersion.VERSION

    @Test
    fun `the plugin keeps kotlinc next to lib`(@TempDir root: Path) {
        kotlincHome(root.resolve("kotlinc"), ideVersion)
        val pluginJar = file(root.resolve("lib/kotlin-scripting-plugin.jar"))
        assertEquals(root.resolve("kotlinc"), Jsr223KotlincProvider.bundledKotlinc(pluginJar))
    }

    @Test
    fun `a content module jar sits one level deeper`(@TempDir root: Path) {
        kotlincHome(root.resolve("kotlinc"), ideVersion)
        val pluginJar = file(root.resolve("lib/modules/intellij.kotlin.jsr223.jar"))
        assertEquals(root.resolve("kotlinc"), Jsr223KotlincProvider.bundledKotlinc(pluginJar))
    }

    @Test
    fun `a jar outside lib is not a plugin`(@TempDir root: Path) {
        kotlincHome(root.resolve("kotlinc"), ideVersion)
        val classesDir = file(root.resolve("out/production/intellij.kotlin.jsr223.jar"))
        assertNull(Jsr223KotlincProvider.bundledKotlinc(classesDir))
    }

    @Test
    fun `the bundled kotlinc must match the analyzer`(@TempDir root: Path) {
        kotlincHome(root.resolve("kotlinc"), "1.0.0-release-1")
        val pluginJar = file(root.resolve("lib/kotlin-scripting-plugin.jar"))
        assertNull(Jsr223KotlincProvider.bundledKotlinc(pluginJar))
    }

    @Test
    fun `the same dist is unpacked once`(@TempDir root: Path) {
        val distJar = distJar(root.resolve("dist.jar"), "first")
        val target = root.resolve("unpacked")

        Jsr223KotlincProvider.unpackKotlinDistForIde(distJar, target)
        target.resolve(COMPILER_JAR).writeText("touched by hand")

        Jsr223KotlincProvider.unpackKotlinDistForIde(distJar, target)
        assertEquals("touched by hand", target.resolve(COMPILER_JAR).readText())
    }

    @Test
    fun `a republished dist is unpacked again`(@TempDir root: Path) {
        val distJar = root.resolve("dist.jar")
        val target = root.resolve("unpacked")

        Jsr223KotlincProvider.unpackKotlinDistForIde(distJar(distJar, "first"), target)
        assertEquals("first", target.resolve(COMPILER_JAR).readText())

        // The cooperative development version never changes, so only the jar itself marks the new dist.
        distJar(distJar, "second")
        Files.setLastModifiedTime(distJar, FileTime.fromMillis(Files.getLastModifiedTime(distJar).toMillis() + 5_000))

        Jsr223KotlincProvider.unpackKotlinDistForIde(distJar, target)
        assertEquals("second", target.resolve(COMPILER_JAR).readText())
    }

    private fun kotlincHome(dir: Path, build: String): Path {
        dir.resolve("lib").createDirectories()
        dir.resolve("build.txt").writeText(build)
        return dir
    }

    private fun file(path: Path): Path {
        path.createParentDirectories()
        path.writeText("")
        return path
    }

    private fun distJar(path: Path, compilerContent: String): Path {
        path.createParentDirectories()
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.write(ZipEntry("build.txt"), KotlinCompilerVersion.VERSION)
            zip.write(ZipEntry(COMPILER_JAR), compilerContent)
        }
        return path
    }

    private fun ZipOutputStream.write(entry: ZipEntry, content: String) {
        putNextEntry(entry)
        write(content.toByteArray())
        closeEntry()
    }
}

private const val COMPILER_JAR = "lib/kotlin-compiler.jar"
