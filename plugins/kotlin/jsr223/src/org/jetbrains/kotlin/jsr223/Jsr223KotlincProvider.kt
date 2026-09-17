// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jsr223

import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.BazelEnvironmentUtil
import com.intellij.util.io.Decompressor
import com.intellij.util.net.NetUtils
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.outputStream

object Jsr223KotlincProvider {
    private const val KOTLINC_DIR_NAME = "kotlinc"
    private const val KOTLIN_DIST_FOR_IDE = "kotlin-dist-for-ide"
    private const val KOTLIN_MAVEN_GROUP_PATH = "org/jetbrains/kotlin"
    private const val KOTLIN_SNAPSHOT_DIR_PATH = "lib/kotlin-snapshot"
    private const val BUILD_TXT = "build.txt"
    private const val DIST_SOURCE_TXT = "dist-source.txt"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 60_000

    private val LOG = logger<Jsr223KotlincProvider>()
    private val kotlinDistForIdeJar = "$KOTLIN_DIST_FOR_IDE-${KotlinCompilerVersion.VERSION}.jar"

    val ideKotlinc: Path by lazy {
        val kotlinc = bundledKotlinc() ?: unpackKotlinDistForIdeWithProgress()
        LOG.info("The JSR-223 kotlinc home is $kotlinc")
        kotlinc
    }

    // The dist can need a download of about 100 MB. The script engine asks for it on the UI thread.
    private fun unpackKotlinDistForIdeWithProgress(): Path = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable { unpackKotlinDistForIde() },
        KotlinJsr223Bundle.message("progress.title.preparing.kotlin.compiler"),
        true,
        null,
    ) ?: error("Kotlin JSR-223 kotlinc distribution is not found for ${KotlinCompilerVersion.VERSION}")

    private fun bundledKotlinc(): Path? {
        val kotlinPluginJar = PathManager.getJarForClass(Jsr223KotlincProvider::class.java) ?: return null
        return bundledKotlinc(kotlinPluginJar)
    }

    // Repeats `com.intellij.ide.plugins.getPluginDistDirByClass`.
    // That function reads the plugin path from the class loader first. This copy has only the path walk.
    internal fun bundledKotlinc(pluginJar: Path): Path? {
        val pluginRoot = pluginJar
            .parent
            .let { if (it.name == "modules") it.parent else it }
            .takeIf { it.name == "lib" }?.parent ?: return null
        val bundled = pluginRoot.resolve(KOTLINC_DIR_NAME).takeIf(::isKotlincHome) ?: return null
        val build = buildNumber(bundled)
        if (build?.startsWith(KotlinCompilerVersion.VERSION) != true) {
            LOG.info("The bundled kotlinc is $build, the JSR-223 engine needs ${KotlinCompilerVersion.VERSION}")
            return null
        }
        return bundled
    }

    private fun unpackKotlinDistForIde(): Path? {
        val target = PathManager.getSystemDir()
            .resolve(KOTLIN_DIST_FOR_IDE)
            .resolve(KotlinCompilerVersion.VERSION)

        // The cooperative development version is a constant, so a republished dist keeps the same target.
        // A downloaded dist cannot change, because the Maven coordinates are immutable.
        val snapshotJar = snapshotKotlinDistForIdeJar()
        if (snapshotJar == null && isKotlincHome(target)) return target

        val distJar = snapshotJar ?: downloadKotlinDistForIdeJar() ?: return null
        LOG.info("The JSR-223 kotlinc dist comes from $distJar")
        return unpackKotlinDistForIde(distJar, target)
    }

    /** Unpacks [distJar] into [target], and keeps an existing unpacked dist when the same jar produced it. */
    internal fun unpackKotlinDistForIde(distJar: Path, target: Path): Path {
        val distSource = target.resolve(DIST_SOURCE_TXT)
        val distStamp = Files.getLastModifiedTime(distJar).toMillis().toString()
        if (!isKotlincHome(target) || runCatching { Files.readString(distSource) }.getOrNull() != distStamp) {
            LOG.info("Unpacking the JSR-223 kotlinc dist into $target")
            indicator?.text = KotlinJsr223Bundle.message("progress.text.unpacking.kotlinc.dist")
            NioFiles.deleteRecursively(target)
            Files.createDirectories(target)
            Decompressor.Zip(distJar).overwrite(true).extract(target)
            Files.writeString(distSource, distStamp)
        }

        check(isKotlincHome(target)) { "Kotlin JSR-223 kotlinc distribution is incomplete: $target" }
        return target
    }

    private fun snapshotKotlinDistForIdeJar(): Path? {
        return Path.of(PathManager.getCommunityHomePath())
            .resolve(KOTLIN_SNAPSHOT_DIR_PATH)
            .resolve(KOTLIN_MAVEN_GROUP_PATH)
            .resolve(KOTLIN_DIST_FOR_IDE)
            .resolve(KotlinCompilerVersion.VERSION)
            .resolve(kotlinDistForIdeJar)
            .takeIf { it.isRegularFile() }
    }

    // `getInstance` throws without an application, and the dist is also unpacked outside of progress.
    private val indicator: ProgressIndicator?
        get() = ProgressManager.getInstanceOrNull()?.progressIndicator

    private fun isKotlincHome(path: Path): Boolean {
        return path.resolve(BUILD_TXT).exists() && path.resolve("lib").exists()
    }

    private fun buildNumber(kotlincHome: Path): String? {
        return runCatching { Files.readString(kotlincHome.resolve(BUILD_TXT)).trim() }.getOrNull()
    }

    private fun downloadKotlinDistForIdeJar(): Path? {
        val target = PathManager.getSystemDir()
            .resolve(KOTLIN_DIST_FOR_IDE)
            .resolve("downloads")
            .resolve(kotlinDistForIdeJar)
        if (target.exists()) return target

        Files.createDirectories(target.parent)
        // A unique name, because a second IDE on the same installation downloads into the same directory.
        val temp = Files.createTempFile(target.parent, target.name, ".tmp")
        try {
            for (url in kotlinDistForIdeUrls) {
                try {
                    downloadTo(url, temp)
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                    return target
                }
                catch (e: IOException) {
                    LOG.info("The JSR-223 kotlinc dist download failed: $url", e)
                }
            }
            return null
        }
        finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun downloadTo(url: String, target: Path) {
        indicator?.text = KotlinJsr223Bundle.message("progress.text.downloading.kotlinc.dist")
        val connection = URI(url).toURL().openConnection()
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        try {
            connection.getInputStream().use { input ->
                target.outputStream().use { output ->
                    NetUtils.copyStreamContent(
                        indicator,
                        input,
                        output,
                        connection.contentLengthLong,
                        true,
                    )
                }
            }
        }
        finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }

    /**
     * The URLs to try for the 'kotlin-dist-for-ide' artifact, in order.
     *
     * The repository list is copied from
     * `community/plugins/kotlin/base/plugin/src/org/jetbrains/kotlin/idea/compiler/configuration/KotlinArtifactsDownloader.kt`,
     * because the Kotlin Scripting plugin used in Qodana does not depend on the Kotlin plugin.
     */
    private val kotlinDistForIdeUrls: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        // For example: org/jetbrains/kotlin/kotlin-dist-for-ide/2.5.0-dev-6810/kotlin-dist-for-ide-2.5.0-dev-6810.jar
        val artifactPath = "$KOTLIN_MAVEN_GROUP_PATH/$KOTLIN_DIST_FOR_IDE/${KotlinCompilerVersion.VERSION}/$kotlinDistForIdeJar"
        buildList {
            add("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
            add("https://cache-redirector.jetbrains.com/intellij-dependencies")
            add("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")

            // This aims to cover tests when run both from sources and binaries, and the debug IDE in various configurations
            val isTestLikeRun = ApplicationManager.getApplication().isUnitTestMode
                    || AppMode.isRunningFromDevBuild()
                    || BazelEnvironmentUtil.isBazelTestRun()

            if (isTestLikeRun) {
                // Do not use the experimental repository in production
                add("https://packages.jetbrains.team/maven/p/kt/experimental")
            }
        }.map { "$it/$artifactPath" }
    }

}

@Suppress("IO_FILE_USAGE")
class Jsr223KotlincScriptEngineFactory :
    KotlinJsr223StandardScriptEngineFactory4IdeaBase({ Jsr223KotlincProvider.ideKotlinc.toFile() }) {
    override fun getNames(): List<String> = super.getNames() + KOTLIN_IDE_JSR223_SCRIPT_ENGINE_NAME
}

private const val KOTLIN_IDE_JSR223_SCRIPT_ENGINE_NAME = "kotlin-ide-jsr223"
