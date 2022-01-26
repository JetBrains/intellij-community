package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

internal sealed class PackageScope(open val scopeName: String) : Comparable<PackageScope> {

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
