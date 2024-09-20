// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sun.jdi.*
import kotlinx.metadata.jvm.KotlinClassMetadata
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.debugger.base.util.wrapEvaluateException
import org.jetbrains.kotlin.idea.debugger.base.util.wrapIllegalArgumentException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class KotlinMetadataDebuggerCacheService private constructor(project: Project) {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinMetadataDebuggerCacheService = project.service()
    }

    private class KotlinMetadataCacheListener(private val project: Project) : DebuggerManagerListener {
        override fun sessionCreated(session: DebuggerSession) {
            getInstance(project).createCache(session.process)
        }

        override fun sessionRemoved(session: DebuggerSession) {
            getInstance(project).removeCache(session.process)
        }
    }

    // There is one cache per debug process. The size of the list will almost always be 1 when debugging.
    private val caches = mutableListOf<KotlinMetadataCache>()

    fun getKotlinMetadata(refType: ReferenceType, context: EvaluationContext): KotlinClassMetadata? {
        for (cache in caches) {
            if (context.debugProcess === cache.debugProcess) {
                return cache.fetchKotlinMetadata(refType, context)
            }
        }
        return null
    }

    private fun createCache(debugProcess: DebugProcess) {
        caches.add(KotlinMetadataCache(debugProcess))
    }

    private fun removeCache(debugProcess: DebugProcess) {
        caches.removeIf { it.debugProcess === debugProcess }
    }
}

private class KotlinMetadataCache(val debugProcess: DebugProcess)  {
    // The purpose of this class is to prevent searching for
    // the MetadataUtilKt class and `getDebugMetadataAsJson` method
    // multiple times.
    private sealed class MetadataJdiFetcher {
        companion object {
            private const val METADATA_UTILS_CLASS_NAME = "kotlin.jvm.internal.MetadataDebugUtilKt"
            private const val GET_DEBUG_METADATA_AS_JSON = "getDebugMetadataAsJson"

            fun getInstance(context: EvaluationContext): MetadataJdiFetcher {
                val metadataUtilClass = wrapEvaluateException {
                    context.debugProcess.findClass(context, METADATA_UTILS_CLASS_NAME, null)
                } as? ClassType ?: return FailedToInitialize
                val getDebugMetadataAsJsonMethod = metadataUtilClass.methodsByName(GET_DEBUG_METADATA_AS_JSON).singleOrNull()
                    ?: return FailedToInitialize
                return Initialized(metadataUtilClass, getDebugMetadataAsJsonMethod)
            }
        }

        data object FailedToInitialize : MetadataJdiFetcher()
        class Initialized(
            private val metadataUtilClass: ClassType,
            private val getDebugMetadataAsJsonMethod: Method
        ) : MetadataJdiFetcher() {
            fun fetchMetadataAsJson(refType: ReferenceType, context: EvaluationContext): String? {
                val classObject = refType.classObject() ?: return null
                val stringRef = wrapEvaluateException {
                    context.debugProcess.invokeMethod(
                        context, metadataUtilClass, getDebugMetadataAsJsonMethod, listOf(classObject)
                    )
                } as? StringReference
                return stringRef?.value()
            }
        }
    }

    private class MetadataAdapter(
        val kind: Int,
        val metadataVersion: Array<Int>,
        val data1: Array<String>,
        val data2: Array<String>,
        val extraString: String,
        val packageName: String,
        val extraInt: Int,
    ) {
        @OptIn(ExperimentalEncodingApi::class)
        fun toMetadata(): Metadata {
            return Metadata(
                kind = kind,
                metadataVersion = metadataVersion.toIntArray(),
                data1 = data1.map { String(Base64.Default.decode(it)) }.toTypedArray(),
                data2 = data2,
                extraString = extraString,
                packageName = packageName,
                extraInt = extraInt
            )
        }
    }

    private val cache = mutableMapOf<ReferenceType, KotlinClassMetadata>()
    private lateinit var metadataJdiFetcher: MetadataJdiFetcher

    fun fetchKotlinMetadata(refType: ReferenceType, context: EvaluationContext): KotlinClassMetadata? {
        if (context.debugProcess !== debugProcess) {
            return null
        }

        if (!::metadataJdiFetcher.isInitialized) {
            metadataJdiFetcher = MetadataJdiFetcher.getInstance(context)
        }

        when (val fetcher = metadataJdiFetcher) {
            is MetadataJdiFetcher.FailedToInitialize -> return null
            is MetadataJdiFetcher.Initialized -> {
                cache[refType]?.let { return it }

                val metadataAsJson = fetcher.fetchMetadataAsJson(refType, context) ?: return null
                val metadata = wrapJsonSyntaxException {
                    Gson().fromJson(metadataAsJson, MetadataAdapter::class.java).toMetadata()
                } ?: return null

                val parsedMetadata = wrapIllegalArgumentException {
                    KotlinClassMetadata.readStrict(metadata)
                } ?: return null

                cache[refType] = parsedMetadata
                return parsedMetadata
            }
        }
    }
}

private fun <T> wrapJsonSyntaxException(block: () -> T): T? {
    return try {
        block()
    } catch (e: JsonSyntaxException) {
        null
    }
}
