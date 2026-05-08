// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull

object KotlinTestsDependenciesUtil {
    private const val IDEA_HOME = "IDEA_HOME"
    private const val COMMUNITY_MARKER = ".community.root.marker"
    private const val ULTIMATE_MARKER = ".ultimate.root.marker"
    private const val PROPERTY_HOME_PATH = "idea.home.path"
    private val isUnderTeamcity = System.getenv("TEAMCITY_VERSION") != null

    // We cannot reference PathManager or build scripts here because we call this code from Gradle in TeamCity
    val communityRoot: Path by lazy {
        findCommunityRoot().toAbsolutePath().normalize()
    }

    private fun findCommunityRoot(): Path {
        val possibleHomePaths = mutableListOf<Path>()
        System.getProperty(PROPERTY_HOME_PATH)?.let { possibleHomePaths.add(Path(it)) }
        System.getenv(IDEA_HOME)?.let { possibleHomePaths.add(Path(it)) }
        possibleHomePaths.add(Path("."))
        for (explicit in possibleHomePaths) {
            if (explicit.resolve(COMMUNITY_MARKER).isRegularFile()) {
                return explicit
            }

            val communityPath = explicit.resolve("community")
            if (communityPath.resolve(COMMUNITY_MARKER).isRegularFile()) {
                return communityPath.toAbsolutePath()
            }
        }

        val aClass: Class<*> = KotlinTestsDependenciesUtil::class.java
        val aClassFilename = "${aClass.getName().replace('.', '/')}.class"
        val aClassLocation = aClass.classLoader.getResource(aClassFilename)?.toString()?.substringAfter("file:")
            ?: error("cannot find class location for class ${aClass.name}")
        var rootPath: Path? = try {
            Path(aClassLocation)
        } catch (_: InvalidPathException) {
            if (aClassLocation.contains(":") &&
                (aClassLocation.startsWith("/") || aClassLocation.startsWith("\\"))
            ) {
                Path(aClassLocation.substring(1))
            } else {
                null
            }
        }

        while (rootPath != null) {
            if (rootPath.resolve(COMMUNITY_MARKER).isRegularFile()) {
                return rootPath.toAbsolutePath()
            } else if (rootPath.resolve(ULTIMATE_MARKER).isRegularFile()) {
                val communityPath = rootPath.resolve("community")
                if (communityPath.isDirectory()) {
                    return communityPath.toAbsolutePath()
                }
            }
            rootPath = rootPath.parent
        }

        error("cannot detect community root path for class $aClassLocation")
    }

    val monorepoRoot: Path?
        get() = communityRoot.parent?.takeIf { it.resolve(ULTIMATE_MARKER).exists() }

    val projectRoot: Path
        get() = monorepoRoot ?: communityRoot

    internal val kotlinCompilerSnapshotLocationInsideCommunity: String
        get() = "lib/kotlin-snapshot"

    val kotlinCompilerSnapshotPath: Path
        get() = communityRoot.resolve(kotlinCompilerSnapshotLocationInsideCommunity)

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val REQUEST_TIMEOUT_SECONDS = 60L
    private const val MAX_RETRY_ATTEMPTS = 4
    private const val INITIAL_RETRY_DELAY_MS = 500L
    private const val MAX_RETRY_DELAY_MS = 4000L
    private const val MAX_RETRY_AFTER_SECONDS = 30L

    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .build()

    /**
     * TODO: IJI-3408 remove duplicated code
     * @see com.intellij.testFramework.common.bazel.BazelTestDependencyHttpFileDownloader
     * it was agreed to leave it ‘as is’ for a while,
     * until a more elegant solution for working with bazel labels in project-model-updater is found
     * and so as not to add dependencies on com.intellij.testFramework.common
     */
    data class DownloadFile(
        val fileName: String,
        val url: String,
        val sha256: String,
    )

    val kotlinTestDependenciesHttpFiles: List<DownloadFile> by lazy {
        val kotlinDepsFile = communityRoot.resolve("plugins/kotlin/kotlin_test_dependencies.bzl")
        if (!Files.isRegularFile(kotlinDepsFile)) {
            error("Unable to find test dependency file '$kotlinDepsFile'")
        }
        val content = kotlinDepsFile.readText()
        val versions = loadVersions(content)
        val nameRegex = Regex("""name\s*=\s*["']([^"']+)["']""")
        val sha256Regex = Regex("""sha256\s*=\s*["']([^"']+)["']""")
        val errors = mutableListOf<String>()
        val result =  findDownloadFileBlocks(content).mapNotNull { block ->
            val name = nameRegex.find(block)?.groupValues?.get(1)
            val url = findUrl(block, versions)
            val sha256 = sha256Regex.find(block)?.groupValues?.get(1)

            if (name != null && sha256 != null) {
                DownloadFile(
                    fileName = name,
                    url = url,
                    sha256 = sha256,
                )
            } else {
                errors += buildString {
                    appendLine("Unable to parse http_file block:\n$block")
                    appendLine(block.trim())
                }
                null
            }
        }.toList()
        if (errors.isNotEmpty()) {
            error("${errors.size} download_file blocks were not parsed correctly:\n${errors.joinToString("\n\n")}")
        }
        return@lazy result
    }

    private fun findUrl(string: String, versions: Map<String, String>): String {
        val urlRegex = Regex("""url\s*=\s*["'](.+)["']""")
        val formatedUrlRegex = Regex("""url\s*=\s*["'](.+)["']\.format\((.+)\),""")
        val url = urlRegex.find(string)?.groupValues?.get(1)
        val matchResult = formatedUrlRegex.find(string)
        val formattedUrl = matchResult?.groupValues?.get(1)
        return if (formattedUrl != null) {
            val version = matchResult.groupValues[2]
            formattedUrl.replace("{0}", versions[version] ?: error("cannot find version $version in $versions"))
        } else {
            url ?: error("cannot find url in '$string'")
        }
    }

    private fun findDownloadFileBlocks(content: String): List<String> {
        val blocks = mutableListOf<String>()
        val regex = Regex("""download_file\s*\(""")

        regex.findAll(content).forEach { match ->
            val startPos = match.range.last + 1
            var depth = 1
            var pos = startPos

            while (pos < content.length && depth > 0) {
                when (content[pos]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                pos++
            }

            if (depth == 0) {
                blocks.add(content.substring(startPos, pos - 1))
            }
        }

        return blocks.filter { it.contains("=") }
    }

    private fun loadVersions(content: String): Map<String, String> {
        val kotlinCompilerCliVersion = kotlinCompilerCliVersionRegex.find(content)?.groupValues[1]
            ?: error("cannot find kotlinCompilerCliVersion in content:\n$content")

        val kotlincKotlinJpsPluginTestsVersion = kotlincKotlinJpsPluginTestsVersionRegex.find(content)?.groupValues[1]
            ?: error("cannot find kotlincKotlinJpsPluginTestsVersion in content:\n$content")
        return mapOf(
            "kotlinCompilerCliVersion" to kotlinCompilerCliVersion,
            "kotlincKotlinJpsPluginTestsVersion" to kotlincKotlinJpsPluginTestsVersion
        )
    }

    fun updateChecksum(isUpToDateCheck: Boolean = false) {
        val kotlinDependenciesBazelFile = communityRoot.resolve("plugins/kotlin/kotlin_test_dependencies.bzl")
        val content = kotlinDependenciesBazelFile.readText()
        val versions = loadVersions(content)

        var result: String? = null
        val sha256Regex = Regex("""sha256\s*=\s*["']([^"']+)["']""")
        val errors = mutableListOf<String>()
        for (block in findDownloadFileBlocks(content)) {
            val sha256 = sha256Regex.find(block)?.groupValues?.get(1)
            val url = findUrl(block, versions)
            // We should not update checksum for a special compiler version which used for Kotlin cooperative development.
            // https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/docs/cooperative-development/environment-setup.md
            if (url.contains("255-dev-255")) {
                continue
            }
            if (sha256 == null) {
                error("cannot find sha256 in '$block'")
            }
            val checksum = sha256SumForUrl(url)
            if (isUpToDateCheck) {
                if (checksum != sha256) {
                    errors.add("sha256 mismatch for '$url' expected '$sha256' but got '$checksum'")
                }
            } else {
                if (result == null) {
                    result = content
                }
                result = result.replace(sha256, checksum)
            }
        }
        if (isUpToDateCheck) {
            if (errors.isNotEmpty()) {
                error("Found ${errors.size} errors:\n${errors.joinToString("\n")}")
            }
        }
        if (result != null) {
            kotlinDependenciesBazelFile.writeText(result)
        }
    }

    private val sha256Cache = ConcurrentHashMap<String, String>()

    internal fun sha256SumForUrl(url: String): String = sha256Cache.getOrPut(url) { calculateSha256SumForUrl(url) }

    private fun calculateSha256SumForUrl(url: String): String {
        println("Calculating SHA256 checksum for '$url'...")
        return withRetry(description = "GET $url") {
            val digest = MessageDigest.getInstance("SHA-256")
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build()
            val response = try {
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream()).followRedirectsIfNeeded()
            } catch (e: HttpTimeoutException) {
                throw RetryableException("timeout: ${e.message}", e)
            } catch (e: IOException) {
                throw RetryableException("io error: ${e.message}", e)
            }
            response.body().use { body ->
                // Handle special case for kt-master.
                // On TeamCity compilation and tests use Kotlin compiler dependencies from a build chain placed into the agent .m2 folder.
                if (response.statusCode() == 404 && isUnderTeamcity) {
                    val fileInTeamcityM2Folder = teamcityM2Location.resolve(url.substringAfterLast("/intellij-dependencies/"))
                    if (fileInTeamcityM2Folder.exists()) {
                        println("File not found in by URL, but found in teamcity m2 folder: $fileInTeamcityM2Folder")
                        digest.update(fileInTeamcityM2Folder.readBytes())
                        return@use digest.digest().joinToString("") { "%02x".format(it) }
                    }
                }
                if (response.statusCode() == 429 || response.statusCode() in 500..599) {
                    val retryAfterSec = response.headers().firstValue("Retry-After").getOrNull()?.toLongOrNull()
                        ?.coerceIn(0L, MAX_RETRY_AFTER_SECONDS)
                    throw RetryableException(
                        message = "server returned ${response.statusCode()} for $url",
                        retryAfterSec = retryAfterSec,
                    )
                }
                if (response.statusCode() != 200) {
                    val errorBody = body.readAllBytes().decodeToString()
                    error("cannot download $url: ${response.statusCode()}\n$errorBody")
                }
                try {
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (body.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                } catch (e: IOException) {
                    throw RetryableException("io error reading body of $url: ${e.message}", e)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }
    }

    private class RetryableException(
        message: String,
        cause: Throwable? = null,
        val retryAfterSec: Long? = null,
    ) : RuntimeException(message, cause)

    private fun <T> withRetry(description: String, block: () -> T): T {
        var delayMs = INITIAL_RETRY_DELAY_MS
        repeat(MAX_RETRY_ATTEMPTS - 1) { attempt ->
            try {
                return block()
            } catch (e: RetryableException) {
                val sleepMs = e.retryAfterSec?.times(1000L)
                    ?: (delayMs + (0..delayMs.toInt() / 2).random())
                println("Retry ${attempt + 1}/${MAX_RETRY_ATTEMPTS - 1} for $description after: ${e.message}; sleeping ${sleepMs}ms")
                Thread.sleep(sleepMs)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
        return try {
            block()
        } catch (e: RetryableException) {
            throw IllegalStateException("Failed after $MAX_RETRY_ATTEMPTS attempts for $description: ${e.message}", e.cause)
        }
    }

    private inline fun <reified T> HttpResponse<T>.followRedirectsIfNeeded(maxRedirects: Int = 5): HttpResponse<T> {
        var response = this
        (0..maxRedirects).forEach { _ ->
            if (!isHttpResponseRedirect(response.statusCode())) {
                return response
            }

            val location = response.headers().firstValue("Location").getOrNull() ?: error("Missing Location header")
            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(location))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .method(this.request().method(), HttpRequest.BodyPublishers.noBody())
            @Suppress("UNCHECKED_CAST")
            response = httpClient.send(
                requestBuilder.build(), when (T::class) {
                    InputStream::class -> HttpResponse.BodyHandlers.ofInputStream()
                    Void::class -> HttpResponse.BodyHandlers.discarding()
                    else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
                } as HttpResponse.BodyHandler<T>
            )
        }

        if (isHttpResponseRedirect(response.statusCode())) {
            error("Too many redirects: ${this.request().uri()}")
        }
        return response
    }

    private fun isHttpResponseRedirect(statusCode: Int?): Boolean {
        return when (statusCode) {
            301,          // Moved Permanently
            302,          // Found
            303,          // See Other
            307,          // Temporary Redirect
            308 -> true  // Permanent Redirect
            else -> false
        }
    }

    private val teamcityM2Location = Path("${System.getProperty("user.home")}/.m2/repository/")
}