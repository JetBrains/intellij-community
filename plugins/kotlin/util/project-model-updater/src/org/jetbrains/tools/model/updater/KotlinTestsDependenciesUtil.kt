// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.*
import kotlin.jvm.optionals.getOrNull

object KotlinTestsDependenciesUtil {
    private const val IDEA_HOME = "IDEA_HOME"
    private const val COMMUNITY_MARKER = ".community.root.marker"
    private const val ULTIMATE_MARKER = ".ultimate.root.marker"
    private const val PROPERTY_HOME_PATH = "idea.home.path"

    val communityRoot: Path by lazy {
        val possibleHomePaths = mutableListOf<Path>()
        System.getProperty(PROPERTY_HOME_PATH)?.let { possibleHomePaths.add(Path(it)) }
        System.getenv(IDEA_HOME)?.let { possibleHomePaths.add(Path(it)) }
        possibleHomePaths.add(Path("."))
        for (explicit in possibleHomePaths) {
            if (explicit.resolve(COMMUNITY_MARKER).isRegularFile()) {
                return@lazy explicit
            }
            val communityPath = explicit.resolve("community")
            if (communityPath.resolve(COMMUNITY_MARKER).isRegularFile()) {
                return@lazy communityPath.toAbsolutePath()
            }
        }
        val aClass: Class<*> = KotlinTestsDependenciesUtil::class.java
        val aClassFilename = "${aClass.getName().replace('.', '/')}.class"
        val aClassLocation = aClass.classLoader.getResource(aClassFilename)?.toString()?.substringAfter("file:")
            ?: error("cannot find class location for class ${aClass.name}")
        var rootPath: Path? = Path(aClassLocation)

        while (rootPath != null) {
            if (rootPath.resolve(COMMUNITY_MARKER).isRegularFile()) {
                return@lazy rootPath.toAbsolutePath()
            } else if (rootPath.resolve(ULTIMATE_MARKER).isRegularFile()) {
                val communityPath = rootPath.resolve("community")
                if (communityPath.isDirectory()) {
                    return@lazy communityPath.toAbsolutePath()
                }
            }
            rootPath = rootPath.parent
        }
        error("cannot detect community root path for class $aClassLocation")
    }

    private val httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    val kotlinTestDependenciesHttpFiles: List<HttpFile> by lazy {
        val kotlinDepsFile = communityRoot.resolve("plugins/kotlin/kotlin_test_dependencies.bzl")
        if (!Files.isRegularFile(kotlinDepsFile)) {
            error("Unable to find test dependency file '$kotlinDepsFile'")
        }
        val content = kotlinDepsFile.readText()
        val nameRegex = Regex("""name\s*=\s*["']([^"']+)["']""")
        val filenameRegex = Regex("""downloaded_file_path\s*=\s*["']([^"']+)["']""")
        val versions = loadVersions(content)

        val errors = mutableListOf<String>()
        val result = findHttpFileBlocks(content).mapNotNull { block ->
            val name = nameRegex.find(block)?.groupValues?.get(1)
            val url = findUrl(block, versions)
            val filename = filenameRegex.find(block)?.groupValues?.get(1)
            if (name != null && filename != null) {
                HttpFile(filename, name, url)
            } else {
                errors += buildString {
                    appendLine("Unable to parse http_file block:\n")
                    appendLine(block.trim())
                }
                null
            }
        }.toList()

        if (errors.isNotEmpty()) {
            error("${errors.size} http_file blocks were not parsed correctly:\n${errors.joinToString("\n\n")}")
        }
        return@lazy result
    }

    data class HttpFile(val downloadFilePath: String, val name: String, val url: String)

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

    private fun findHttpFileBlocks(content: String): List<String> {
        val blocks = mutableListOf<String>()
        val regex = Regex("""http_file\s*\(""")

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

        return blocks
    }

    private fun loadVersions(content: String): Map<String, String> {
        val kotlinCompilerCliVersionRegex = Regex("""kotlinCompilerCliVersion\s*=\s*"(\S+)"""")
        val kotlinCompilerCliVersion = kotlinCompilerCliVersionRegex.find(content)?.groupValues[1]
            ?: error("cannot find kotlinCompilerCliVersion in content:\n$content")

        val kotlincKotlinJpsPluginTestsVersionRegex = Regex("""kotlincKotlinJpsPluginTestsVersion\s*=\s*"(\S+)"""")
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
        for (block in findHttpFileBlocks(content)) {
            val sha256 = sha256Regex.find(block)?.groupValues?.get(1)
            val url = findUrl(block, versions)
            if (sha256 == null) {
                error("cannot find sha256 in '$block'")
            }
            val checksum = calculateSha256SumForUrl(url)
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

    private fun calculateSha256SumForUrl(url: String): String {
        println("Calculating SHA256 checksum for '$url'")
        val digest = MessageDigest.getInstance("SHA-256")
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
        val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
            .followRedirectsIfNeeded()
        if (response.statusCode() != 200) {
            val body = response.body().use { it.readAllBytes() }.decodeToString()
            error("cannot download $url: ${response.statusCode()}\n$body")
        }

        response.body().use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private inline fun <reified T> HttpResponse<T>.followRedirectsIfNeeded(maxRedirects: Int = 5): HttpResponse<T> {
        var response = this
        (0..maxRedirects).forEach { _ ->
            if (!isHttpResponseRedirect(response.statusCode())) {
                return response
            }

            val location = response.headers().firstValue("Location").getOrNull() ?: error("Missing Location header")
            val requestBuilder =
                HttpRequest.newBuilder().uri(URI.create(location)).method(this.request().method(), HttpRequest.BodyPublishers.noBody())
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
}