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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

sealed class PackageScope(open val scopeName: String) : Comparable<PackageScope> {

    @get:Nls
    abstract val displayName: String

    override fun compareTo(other: PackageScope): Int = scopeName.compareTo(other.scopeName)

    object Missing : PackageScope("") {

        @Nls
        override val displayName = PackageSearchBundle.message("packagesearch.ui.missingScope")

        @NonNls
        override fun toString() = "[Missing scope]"
    }

    data class Named(@NlsSafe override val scopeName: String) : PackageScope(scopeName) {

        init {
            require(scopeName.isNotBlank()) { "A Named scope name cannot be blank." }
        }

        @Nls
        override val displayName = scopeName

        @NonNls
        override fun toString() = scopeName
    }

    companion object {

        fun from(rawScope: String?): PackageScope {
            if (rawScope.isNullOrBlank()) return Missing
            return Named(rawScope.trim())
        }
    }
}
