// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.rules.TempDirectory
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.readText

class K2ScratchCompilerArtifactsTest : UsefulTestCase() {
    private val tempDir = TempDirectory()

    override fun setUp() {
        super.setUp()
        tempDir.before(name)
    }

    override fun tearDown() {
        try {
            tempDir.after()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testExtractScratchCompilerHomeKeepsOnlyScratchArtifacts() {
        val distJar = createDistJar(
            kotlincIdeScratchHomeRelativePaths +
                    listOf(
                        "lib/${KotlinArtifactNames.KOTLIN_MAIN_KTS}",
                        "lib/kotlin-stdlib-wasm-js.klib",
                        "license/NOTICE.txt",
                    )
        )

        val extractedHome = extractKotlincIdeScratchHomeForTests(
            distJar = distJar,
            targetDir = tempDir.newDirectoryPath("scratch-home"),
        )

        assertTrue(Files.exists(extractedHome.resolve(kotlincIdeScratchBuildTxtFileName)))
        kotlincIdeScratchHomeRelativePaths.forEach { relativePath ->
            assertTrue("Missing $relativePath", Files.exists(extractedHome.resolve(relativePath)))
        }
        assertFalse(Files.exists(extractedHome.resolve("lib/${KotlinArtifactNames.KOTLIN_MAIN_KTS}")))
        assertFalse(Files.exists(extractedHome.resolve("lib/kotlin-stdlib-wasm-js.klib")))
        assertFalse(Files.exists(extractedHome.resolve("license/NOTICE.txt")))
    }

    fun testScratchCompilerHomeUsesDedicatedDirectoryName() {
        assertEquals("kotlinc.ide.scratch", kotlincIdeScratchDirectoryName)
        assertTrue(kotlincIdeScratchHomeArtifactFileNames.contains(KotlinArtifactNames.KOTLIN_PRELOADER))
        assertFalse(kotlincIdeScratchClasspathArtifactFileNames.contains(KotlinArtifactNames.KOTLIN_PRELOADER))
    }

    fun testExtractScratchCompilerHomeRefreshesWhenDistJarChanges() {
        val targetDir = tempDir.newDirectoryPath("scratch-home")
        val distJar = tempDir.newFileNio("dist.jar")

        writeDistJar(distJar, kotlincIdeScratchHomeRelativePaths.associateWith { "old:$it" })
        extractKotlincIdeScratchHomeForTests(distJar = distJar, targetDir = targetDir)

        writeDistJar(distJar, kotlincIdeScratchHomeRelativePaths.associateWith { "new:$it" })
        Files.setLastModifiedTime(distJar, FileTime.fromMillis(System.currentTimeMillis() + 1000))

        val refreshedHome = extractKotlincIdeScratchHomeForTests(distJar = distJar, targetDir = targetDir)

        assertEquals("new:${kotlincIdeScratchBuildTxtFileName}", refreshedHome.resolve(kotlincIdeScratchBuildTxtFileName).readText())
        assertEquals(
            "new:lib/${KotlinArtifactNames.KOTLIN_COMPILER}",
            refreshedHome.resolve("lib/${KotlinArtifactNames.KOTLIN_COMPILER}").readText()
        )
    }

    private fun createDistJar(entries: List<String>): Path {
        val distJar = tempDir.newFileNio("dist.jar")
        writeDistJar(distJar, entries.distinct().associateWith { it })
        return distJar
    }

    private fun writeDistJar(distJar: Path, entries: Map<String, String>) {
        distJar.parent.createDirectories()
        ZipOutputStream(distJar.outputStream().buffered()).use { output ->
            val addedDirectories = mutableSetOf<String>()
            entries.forEach { (entryName, entryContent) ->
                val normalizedEntryName = entryName.trim('/')
                if (normalizedEntryName.isBlank()) return@forEach
                if (normalizedEntryName.contains('/')) {
                    val directoryPath = normalizedEntryName.substringBeforeLast('/')
                    directoryPath.split('/').runningFold("") { prefix, segment ->
                        if (prefix.isEmpty()) segment else "$prefix/$segment"
                    }.filter(String::isNotBlank).forEach { directory ->
                        if (!addedDirectories.add(directory)) return@forEach
                        output.putNextEntry(ZipEntry("$directory/"))
                        output.closeEntry()
                    }
                }
                output.putNextEntry(ZipEntry(normalizedEntryName))
                output.write(entryContent.toByteArray())
                output.closeEntry()
            }
        }
    }
}
