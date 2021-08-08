// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.backwardRefs.SearchId
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.kotlin.name.FqName

@IntellijInternalApi
sealed class FqNameWrapper {
    abstract val fqName: FqName
    abstract val jvmFqName: String

    private class FqNameBasedWrapper(override val fqName: FqName) : FqNameWrapper() {
        /**
         * it is impossible to unambiguously convert "fqName" into "jvmFqName" without additional information
         */
        override val jvmFqName: String by lazy(fun(): String {
            val asString = fqName.asString()
            var startIndex = 0
            while (startIndex != -1) { // always true
                val dotIndex = asString.indexOf('.', startIndex)
                if (dotIndex == -1) return asString

                startIndex = dotIndex + 1
                val charAfterDot = asString.getOrNull(startIndex) ?: return asString
                if (!charAfterDot.isLetter()) return asString
                if (charAfterDot.isUpperCase()) return buildString {
                    append(asString.subSequence(0, startIndex))
                    append(asString.substring(startIndex).replace('.', '$'))
                }
            }

            return asString
        })
    }

    private class JvmFqNameBasedWrapper(override val jvmFqName: String) : FqNameWrapper() {
        override val fqName: FqName = FqName(jvmFqName.replace('$', '.'))
    }

    companion object {
        fun createFromFqName(fqName: FqName): FqNameWrapper = FqNameBasedWrapper(fqName)
        fun createFromJvmFqName(jvmFqName: String): FqNameWrapper = JvmFqNameBasedWrapper(jvmFqName)
        fun createFromSearchId(searchId: SearchId): FqNameWrapper = JvmFqNameBasedWrapper(searchId.deserializedName)
    }
}