// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jsr223

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.isRunningFromSources
import com.intellij.ide.plugins.getPluginDistDirByClass
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.io.Decompressor
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader.downloadArtifactForIdeFromSources
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

object Jsr223KotlincProvider {
    private const val KOTLINC_DIR_NAME = "kotlinc"
    private const val KOTLIN_DIST_FOR_IDE_ARTIFACT_ID = "kotlin-dist-for-ide"
    private const val KOTLIN_DIST_LOCATION_PREFIX = "kotlin-dist-for-ide"
    private const val BUILD_TXT = "build.txt"

    val ideKotlinc: Path by lazy {
        bundledKotlinc()
            ?: unpackKotlinDistForIde()
            ?: error("Kotlin JSR-223 kotlinc distribution is not found")
    }

    private fun bundledKotlinc(): Path? {
        if (isRunningFromSources()) {
            /** [getPluginDistDirByClass] won't run for IntelliJ ran from sources as it requires plugins to be inside 'lib/' */
            return null
        }

        val pluginRoot = getPluginDistDirByClass(Jsr223KotlincProvider::class.java) ?: return null
        return pluginRoot.resolve(KOTLINC_DIR_NAME).takeIf(::isExpectedKotlincHome)
    }

    private fun unpackKotlinDistForIde(): Path? {
        val distJar = findKotlinDistForIdeJar() ?: return null
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

    private fun findKotlinDistForIdeJar(): Path? {
        return findKotlinDistForIdeJarInClassLoaders()
            ?: runBlockingCancellable { downloadArtifactForIdeFromSources(KotlinCompilerVersion.VERSION) }
    }

    private fun findKotlinDistForIdeJarInClassLoaders(): Path? {
        val classLoaders = sequenceOf(Jsr223KotlincProvider::class.java.classLoader, Thread.currentThread().contextClassLoader)
            .filterNotNull() + PluginManagerCore.loadedPlugins.asSequence().mapNotNull { it.pluginClassLoader }

        return classLoaders
            .flatMap(::classLoaderUrls)
            .mapNotNull(::toLocalPath)
            .distinct()
            .firstOrNull(::isKotlinDistForIdeJar)
    }

    private fun isKotlinDistForIdeJar(path: Path): Boolean {
        return path.name == kotlinDistForIdeJarName() && path.exists()
    }

    private fun kotlinDistForIdeJarName(): String {
        return "$KOTLIN_DIST_FOR_IDE_ARTIFACT_ID-${KotlinCompilerVersion.VERSION}.jar"
    }

    private fun classLoaderUrls(classLoader: ClassLoader): Sequence<URL> = sequence {
        if (classLoader is URLClassLoader) {
            yieldAll(classLoader.urLs.asSequence())
        }
        val urls = runCatching {
            val method = classLoader.javaClass.getMethod("getUrls")
            @Suppress("UNCHECKED_CAST")
            method.invoke(classLoader) as? Iterable<URL>
        }.getOrNull()
        if (urls != null) {
            yieldAll(urls)
        }
    }

    private fun toLocalPath(url: URL): Path? {
        if (url.protocol != "file") return null
        return runCatching { Path.of(url.toURI()) }.getOrNull()
    }
}

@Suppress("IO_FILE_USAGE")
class Jsr223KotlincScriptEngineFactory :
    KotlinJsr223StandardScriptEngineFactory4IdeaBase({ Jsr223KotlincProvider.ideKotlinc.toFile() }) {
    override fun getNames(): List<String> = super.getNames() + KOTLIN_IDE_JSR223_SCRIPT_ENGINE_NAME
}

private const val KOTLIN_IDE_JSR223_SCRIPT_ENGINE_NAME = "kotlin-ide-jsr223"
