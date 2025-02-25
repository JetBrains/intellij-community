// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.rt.debugger.JsonUtils
import com.intellij.rt.debugger.MetadataDebugHelper
import com.sun.jdi.ReferenceType
import com.sun.jdi.StringReference
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.debugger.base.util.wrapEvaluateException
import org.jetbrains.kotlin.idea.debugger.base.util.wrapIllegalArgumentException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.metadata.jvm.KotlinClassMetadata

object KotlinMetadataCacheService {
    private val METADATA_CACHE_KEY = Key.create<KotlinMetadataCache?>("METADATA_CACHE_KEY")

    fun getKotlinMetadata(refType: ReferenceType, context: EvaluationContext): KotlinClassMetadata? {
        return getCache(context)?.fetchKotlinMetadata(refType, context)
    }

    fun getKotlinMetadataList(refTypes: Collection<ReferenceType>, context: EvaluationContext): List<KotlinClassMetadata>? {
	    return getCache(context)?.fetchKotlinMetadataList(refTypes, context)
    }

    private fun getCache(context: EvaluationContext): KotlinMetadataCache? {
        val vmProxy = (context as? EvaluationContextImpl)?.virtualMachineProxy ?: return null
        return vmProxy.getOrCreateUserData(METADATA_CACHE_KEY) {
            KotlinMetadataCache()
        }
    }
}

private class KotlinMetadataCache {
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

    fun fetchKotlinMetadata(refType: ReferenceType, context: EvaluationContext): KotlinClassMetadata? {
        cache[refType]?.let { return it }
        val metadataAsJson = callMethodFromHelper(
            context, "getDebugMetadataAsJson", listOf(refType.classObject())
        ) ?: return null
        val metadata = parseMetadataFromJson(metadataAsJson) ?: return null
        cache[refType] = metadata
        return metadata
    }

    fun fetchKotlinMetadataList(refTypes: Collection<ReferenceType>, context: EvaluationContext): List<KotlinClassMetadata>? {
        val result = mutableListOf<KotlinClassMetadata>()
        val toFetch = mutableListOf<ReferenceType>()
        for (refType in refTypes) {
            val metadata = cache[refType]
            if (metadata != null) {
                result.add(metadata)
            } else {
                toFetch.add(refType)
            }
        }

        if (toFetch.isEmpty()) {
            return result
        }

        val classObjects = toFetch.map { it.classObject() }
        val concatenatedJsonMetadatas = callMethodFromHelper(
            context, "getDebugMetadataListAsJson", classObjects
        ) ?: return null
        val splitJsonMetadatas = concatenatedJsonMetadatas.split(MetadataDebugHelper.METADATA_SEPARATOR)
        if (splitJsonMetadatas.size != toFetch.size) {
            return null
        }

        for ((metadataAsJson, refType) in splitJsonMetadatas.zip(toFetch)) {
            val metadata = parseMetadataFromJson(metadataAsJson) ?: return null
            cache[refType] = metadata
            result.add(metadata)
        }
        return result
    }

    private fun parseMetadataFromJson(metadataAsJson: String): KotlinClassMetadata? {
        val metadata = wrapJsonSyntaxException {
            Gson().fromJson(metadataAsJson, MetadataAdapter::class.java).toMetadata()
        } ?: return null
        val parsedMetadata = wrapIllegalArgumentException {
            KotlinClassMetadata.readStrict(metadata)
        } ?: return null
        return parsedMetadata
    }

    private fun callMethodFromHelper(context: EvaluationContext, methodName: String, args: List<Value>): String? {
        return wrapEvaluateException {
            val value = DebuggerUtilsImpl.invokeHelperMethod(
                context as EvaluationContextImpl,
                MetadataDebugHelper::class.java,
                methodName,
                args,
                true,
                JsonUtils::class.java.name
            )
            (value as? StringReference)?.value()
        }
    }
}

private inline fun <T> wrapJsonSyntaxException(block: () -> T): T? {
    return try {
        block()
    } catch (_: JsonSyntaxException) {
        null
    }
}
