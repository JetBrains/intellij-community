// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.mlCompletion

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin

class KotlinContextFeatureProvider : ContextFeatureProvider {
    override fun getName(): String = "kotlin"

    override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
        val features = mutableMapOf("plugin_version" to MLFeatureValue.categorical(KotlinVersionFakeEnum.VERSION))

        val fileType = environment.parameters.originalFile.virtualFile?.name?.let { FileTypeStats.parseFromFileName(it) }
        if (fileType != null) {
            features["file_type"] = MLFeatureValue.categorical(fileType)
        }

        return features
    }
}

/**
 * We do not have a enum to represent Kotlin Plugin version; because of that we cannot
 * make it a categorical feature for ML completion (`MLFeatureValue.categorical` accepts only enums).
 *
 * This fake enum is used as a workaround to this problem.
 *
 * TODO As soon as there would be a way to pass Kotlin Plugin version without this enum, it should be removed.
 */
private enum class KotlinVersionFakeEnum {
    VERSION;

    override fun toString(): String = KotlinIdePlugin.version
}

enum class FileTypeStats {
    KT, GRADLEKTS, KTS;

    companion object {
        fun parseFromFileName(fileName: String): FileTypeStats? = when {
            fileName.endsWith(".kt") -> KT
            fileName.endsWith(".gradle.kts") -> GRADLEKTS
            fileName.endsWith(".kts") -> KTS
            else -> null
        }
    }
}