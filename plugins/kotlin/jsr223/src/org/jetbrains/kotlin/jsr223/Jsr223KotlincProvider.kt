// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jsr223

import com.intellij.openapi.application.PathManager
import com.intellij.util.io.Decompressor
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.name

object Jsr223KotlincProvider {
    private const val KOTLINC_DIR_NAME = "kotlinc"
    private const val KOTLIN_DIST_FOR_IDE_ARTIFACT_ID = "kotlin-dist-for-ide"
    private const val KOTLIN_MAVEN_GROUP_PATH = "org/jetbrains/kotlin"
    private const val KOTLIN_DIST_LOCATION_PREFIX = "kotlin-dist-for-ide"
    private const val BUILD_TXT = "build.txt"

    val ideKotlinc: Path by lazy {
        bundledKotlinc()
            ?: unpackKotlinDistForIde()
            ?: error("Kotlin JSR-223 kotlinc distribution is not found")
    }

    private fun bundledKotlinc(): Path? {
        val kotlinPluginJar = PathManager.getJarForClass(Jsr223KotlincProvider::class.java) ?: return null
        val pluginRoot = kotlinPluginJar
            .parent
            .let { if (it.name == "modules") it.parent else it }
            .takeIf { it.name == "lib" }?.parent ?: return null
        return pluginRoot.resolve(KOTLINC_DIR_NAME).takeIf(::isExpectedKotlincHome)
    }

    private fun unpackKotlinDistForIde(): Path? {
        val distJar = downloadKotlinDistForIdeJar() ?: return null
        val target = PathManager.getSystemDir()
            .resolve(KOTLIN_DIST_LOCATION_PREFIX)
            .resolve(KotlinCompilerVersion.VERSION)
        if (!isKotlincHome(target)) {
            Files.createDirectories(target)
            Decompressor.Zip(distJar).overwrite(true).extract(target)
        }
        check(isKotlincHome(target)) { "Kotlin JSR-223 kotlinc distribution is incomplete: $target" }
        return target
    }

    private fun isKotlincHome(path: Path): Boolean {
        return path.resolve(BUILD_TXT).exists() && path.resolve("lib").exists()
    }

    private fun isExpectedKotlincHome(path: Path): Boolean {
        if (!isKotlincHome(path)) return false
        val build = runCatching { Files.readString(path.resolve(BUILD_TXT)).trim() }.getOrNull() ?: return false
        return build.startsWith(KotlinCompilerVersion.VERSION)
    }

    private fun downloadKotlinDistForIdeJar(): Path? {
        val target = PathManager.getSystemDir()
            .resolve(KOTLIN_DIST_LOCATION_PREFIX)
            .resolve("downloads")
            .resolve(kotlinDistForIdeJarName())
        if (target.exists()) return target

        Files.createDirectories(target.parent)
        val temp = target.resolveSibling("${target.name}.tmp")
        for (url in kotlinDistForIdeUrls()) {
            try {
                URL(url).openStream().use { stream ->
                    Files.copy(stream, temp, StandardCopyOption.REPLACE_EXISTING)
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                return target
            }
            catch (_: IOException) {
                Files.deleteIfExists(temp)
            }
        }
        return null
    }

    private fun kotlinDistForIdeUrls(): List<String> {
        val fileName = kotlinDistForIdeJarName()
        val version = KotlinCompilerVersion.VERSION
        val artifactPath = "$KOTLIN_MAVEN_GROUP_PATH/$KOTLIN_DIST_FOR_IDE_ARTIFACT_ID/$version/$fileName"

        return buildList {
            add("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
            add("https://cache-redirector.jetbrains.com/intellij-dependencies")
            add("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
        }.map { "$it/$artifactPath" }
    }

    private fun kotlinDistForIdeJarName(): String {
        return "$KOTLIN_DIST_FOR_IDE_ARTIFACT_ID-${KotlinCompilerVersion.VERSION}.jar"
    }
}

@Suppress("IO_FILE_USAGE")
class Jsr223KotlincScriptEngineFactory :
    KotlinJsr223StandardScriptEngineFactory4IdeaBase({ Jsr223KotlincProvider.ideKotlinc.toFile() }) {
    override fun getNames(): List<String> = super.getNames() + KOTLIN_IDE_JSR223_SCRIPT_ENGINE_NAME
}

private const val KOTLIN_IDE_JSR223_SCRIPT_ENGINE_NAME = "kotlin-ide-jsr223"
