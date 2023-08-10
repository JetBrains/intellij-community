// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import com.intellij.ide.plugins.*
import com.intellij.notification.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.io.HttpRequests
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.update.verify
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset


class KotlinPluginUpdater : StandalonePluginUpdateChecker(
    KotlinIdePlugin.id,
    PROPERTY_NAME,
    NotificationGroupManager.getInstance().getNotificationGroup("Kotlin plugin updates"),
    KotlinIcons.SMALL_LOGO
) {
    override val currentVersion: String
        get() = KotlinIdePlugin.version

    override fun skipUpdateCheck() = KotlinIdePlugin.isSnapshot || KotlinIdePlugin.hasPatchedVersion

    override fun verifyUpdate(status: PluginUpdateStatus.Update): PluginUpdateStatus {
        return verify(status)
    }

    fun runUpdateCheck(callback: (PluginUpdateStatus) -> Boolean) {
        ApplicationManager.getApplication().executeOnPooledThread {
            updateCheck(callback)
        }
    }

    fun runCachedUpdate(callback: (PluginUpdateStatus) -> Boolean) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val cachedStatus = lastUpdateStatus
        if (cachedStatus != null && System.currentTimeMillis() - cachedStatus.timestamp < CACHED_REQUEST_DELAY) {
            if (cachedStatus !is PluginUpdateStatus.CheckFailed) {
                callback(cachedStatus)
                return
            }
        }

        queueUpdateCheck(callback)
    }

    companion object {

        private const val PROPERTY_NAME = "kotlin.lastUpdateCheck"
        private val LOG = Logger.getInstance(KotlinPluginUpdater::class.java)

        fun getInstance(): KotlinPluginUpdater = service()

        class ResponseParseException(message: String, cause: Exception? = null) : IllegalStateException(message, cause)

        @Suppress("SpellCheckingInspection")
        private class PluginDTO {
            var cdate: String? = null
            var channel: String? = null

            // `true` if the version is seen in plugin site and available for download.
            // Maybe be `false` if author requested version deletion.
            var listed: Boolean = true

            // `true` if version is approved and verified
            var approve: Boolean = true
        }

        @Throws(IOException::class, ResponseParseException::class)
        fun fetchPluginReleaseDate(pluginId: PluginId, version: String, channel: String?): LocalDate? {
            val url = "https://plugins.jetbrains.com/api/plugins/${pluginId.idString}/updates?version=$version"

            val pluginDTOs: Array<PluginDTO> = try {
                HttpRequests.request(url).connect {
                    GsonBuilder().create().fromJson(it.inputStream.reader(), Array<PluginDTO>::class.java)
                }
            } catch (ioException: JsonIOException) {
                throw IOException(ioException)
            } catch (syntaxException: JsonSyntaxException) {
                throw ResponseParseException("Can't parse json response", syntaxException)
            }

            val selectedPluginDTO = pluginDTOs.firstOrNull {
                it.listed && it.approve && (it.channel == channel || (it.channel == "" && channel == null))
            } ?: return null

            val dateString = selectedPluginDTO.cdate ?: throw ResponseParseException("Empty cdate")
            return try {
                val dateLong = dateString.toLong()
                Instant.ofEpochMilli(dateLong).atZone(ZoneOffset.UTC).toLocalDate()
            } catch (e: NumberFormatException) {
                throw ResponseParseException("Can't parse long date", e)
            } catch (e: DateTimeException) {
                throw ResponseParseException("Can't convert to date", e)
            }
        }
    }
}
