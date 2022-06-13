/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.extensibility

data class DependencyOperationMetadata(
    val module: ProjectModule,
    val groupId: String,
    val artifactId: String,
    val currentVersion: String?,
    val currentScope: String?,
    val newVersion: String? = null,
    val newScope: String? = null
) {

    @Suppress("DuplicatedCode")
    val displayName by lazy {
        buildString {
            append(groupId)
            append(":$artifactId")
            if (currentVersion != null) append(":$currentVersion")
            append(" [module='")
            append(module.getFullName())
            if (currentScope != null) {
                append("', currentScope='")
                append(currentScope)
            }
            if (newScope != null) {
                append("', newScope='")
                append(newScope)
            }
            if (newVersion != null) {
                append("', newVersion='")
                append(newVersion)
            }
            append("']")
        }
    }
}
