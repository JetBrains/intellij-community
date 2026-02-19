// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.versions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import java.io.DataInput
import java.io.DataOutput

/**
 * Important! This is not a stub-based index. And it has its own version
 */
abstract class KotlinMetadataVersionIndexBase<V : BinaryVersion> : ScalarIndexExtension<V>() {
    override fun getKeyDescriptor(): KeyDescriptor<V> = object : KeyDescriptor<V> {
        override fun isEqual(val1: V, val2: V): Boolean = val1 == val2

        override fun getHashCode(value: V): Int = value.hashCode()

        override fun read(input: DataInput): V {
            val size = DataInputOutputUtil.readINT(input)
            val versionArray = (0 until size).map { DataInputOutputUtil.readINT(input) }.toIntArray()
            val extraBoolean = if (isExtraBooleanNeeded()) DataInputOutputUtil.readINT(input) == 1 else null
            return createBinaryVersion(versionArray, extraBoolean)
        }

        override fun save(output: DataOutput, value: V) {
            val array = value.toArray()
            DataInputOutputUtil.writeINT(output, array.size)
            for (number in array) {
                DataInputOutputUtil.writeINT(output, number)
            }
            if (isExtraBooleanNeeded()) {
                DataInputOutputUtil.writeINT(output, if (getExtraBoolean(value)) 1 else 0)
            }
        }
    }

    override fun dependsOnFileContent() = true

    protected abstract fun createBinaryVersion(versionArray: IntArray, extraBoolean: Boolean?): V

    protected open fun isExtraBooleanNeeded(): Boolean = false
    protected open fun getExtraBoolean(version: V): Boolean = throw UnsupportedOperationException()

    protected abstract fun getLogger(): Logger

    protected inline fun tryBlock(inputData: FileContent, body: () -> Unit) {
        try {
            body()
        } catch (e: Throwable) {
            getLogger().warn("Could not index ABI version for file " + inputData.file + ": " + e.message)
        }
    }
}
