// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.versions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.hints.BinaryFileTypePolicy
import com.intellij.util.indexing.hints.FileNameExtensionInputFilter
import com.intellij.util.indexing.hints.FileTypeInputFilterPredicate
import org.jetbrains.kotlin.utils.JsMetadataVersion
import org.jetbrains.kotlin.utils.KotlinJavascriptMetadata
import org.jetbrains.kotlin.utils.KotlinJavascriptMetadataUtils

private val LOG = Logger.getInstance(KotlinJsMetadataVersionIndex::class.java)

class KotlinJsMetadataVersionIndex internal constructor() : KotlinMetadataVersionIndexBase<JsMetadataVersion>() {
    companion object {
        val NAME: ID<JsMetadataVersion, Void> = ID.create(KotlinJsMetadataVersionIndex::class.java.canonicalName)
    }

    override fun getName(): ID<JsMetadataVersion, Void> = NAME

    override fun createBinaryVersion(versionArray: IntArray, extraBoolean: Boolean?): JsMetadataVersion =
        JsMetadataVersion(*versionArray)

    override fun getIndexer() = INDEXER

    override fun getLogger() = LOG

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return when (val fileType = FileTypeManager.getInstance().findFileTypeByName("JS")) {
            null -> FileNameExtensionInputFilter(extension = "js", ignoreCase = false, BinaryFileTypePolicy.NON_BINARY)
            else -> FileTypeInputFilterPredicate(fileType)
        }
    }

    override fun getVersion() = 3

    private val INDEXER = DataIndexer { inputData: FileContent ->
        val result = HashMap<JsMetadataVersion, Void?>()

        tryBlock(inputData) {
            val metadataList = ArrayList<KotlinJavascriptMetadata>()
            KotlinJavascriptMetadataUtils.parseMetadata(inputData.contentAsText, metadataList)
            for (metadata in metadataList) {
                val version = metadata.version.takeIf { it.isCompatible() }
                // Version is set to something weird
                    ?: JsMetadataVersion.INVALID_VERSION
                result[version] = null
            }
        }

        result
    }
}
