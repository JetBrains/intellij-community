// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.versions

import com.google.common.io.Closeables
import com.google.gson.JsonParser
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.HttpRequests
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout.standaloneCompilerVersion
import org.jetbrains.kotlin.idea.configuration.getRepositoryForVersion
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@State(
    name = "KotlinVersionsStorage",
    storages = [Storage(StoragePathMacros.CACHE_FILE)]
)
class KotlinVersionsStorage : PersistentStateComponent<KotlinVersionsStorage> {
    var lastSuccessfullyFetchedVersionsTimestamp: Long? = null
    var versions: List<String> = emptyList()

    override fun getState(): KotlinVersionsStorage = this

    override fun loadState(state: KotlinVersionsStorage) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun clear() {
        lastSuccessfullyFetchedVersionsTimestamp = null
        versions = emptyList()
    }

    companion object {
        @JvmStatic
        private val LOG = Logger.getInstance(KotlinVersionsStorage::class.java)

        fun getInstance(project: Project): KotlinVersionsStorage = project.service()

        fun getOrFetchVersions(
            project: Project,
            url: String,
            minimumVersion: String? = null,
            cacheTime: Duration = 1.days
        ): List<String> {
            val now = System.currentTimeMillis()
            val versionsStorage = getInstance(project)
            versionsStorage.lastSuccessfullyFetchedVersionsTimestamp?.let {
                if (now - it < cacheTime.inWholeMilliseconds && versionsStorage.versions.isNotEmpty()) {
                    return versionsStorage.versions
                }
            }

            val versions: MutableList<String> = ArrayList()
            val kotlinCompilerVersion = standaloneCompilerVersion
            val kotlinArtifactVersion = kotlinCompilerVersion.artifactVersion
            val repositoryDescription = getRepositoryForVersion(kotlinCompilerVersion)
            if (repositoryDescription?.bintrayUrl != null) {
                val eapConnection =
                    HttpConfigurable.getInstance().openHttpConnection(repositoryDescription.bintrayUrl + kotlinArtifactVersion)
                try {
                    val timeout = 30.seconds.toInt(DurationUnit.MILLISECONDS)
                    eapConnection.setConnectTimeout(timeout)
                    eapConnection.setReadTimeout(timeout)
                    if (eapConnection.getResponseCode() == 200) {
                        versions.add(kotlinArtifactVersion)
                    }
                } finally {
                    eapConnection.disconnect()
                }
            }
            val urlConnection = HttpConfigurable.getInstance().openHttpConnection(url)
            try {
                val timeout = TimeUnit.SECONDS.toMillis(30).toInt()
                urlConnection.setConnectTimeout(timeout)
                urlConnection.setReadTimeout(timeout)
                urlConnection.connect()
                val streamReader = InputStreamReader(urlConnection.inputStream, StandardCharsets.UTF_8)
                try {
                    val rootElement = JsonParser.parseReader(streamReader)
                    val docsElements = rootElement.getAsJsonObject()["response"].getAsJsonObject()["docs"].getAsJsonArray()
                    for (element in docsElements) {
                        val versionNumber = element.getAsJsonObject()["v"].asString
                        if (VersionComparatorUtil.compare(minimumVersion, versionNumber) <= 0) {
                            versions.add(versionNumber)
                        }
                    }
                } finally {
                    Closeables.closeQuietly(streamReader)
                }
            } catch (e: HttpRequests.HttpStatusException) {
                LOG.warn("Cannot load data from ${url} (statusCode=${e.statusCode})", e)
                throw e
            } catch (e: Exception) {
                LOG.warn("Error parsing Kotlin versions JSON data: ${e} (URL=${url})", e)
                throw e
            } finally {
                urlConnection.disconnect()
            }

            Collections.sort(versions, VersionComparatorUtil.COMPARATOR.reversed())

            // Handle the case when the new version has just been released and the Maven search index hasn't been updated yet
            if (kotlinCompilerVersion.isRelease && !versions.contains(kotlinArtifactVersion)) {
                versions.add(0, kotlinArtifactVersion)
            }

            with(versionsStorage) {
                this.versions = versions
                this.lastSuccessfullyFetchedVersionsTimestamp = now
            }
            return versions
        }
    }
}
