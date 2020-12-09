package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.V2Repository
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import org.jetbrains.annotations.Nls

fun V2Repository?.asList() =
    if (this != null) {
        listOf(this.id)
    } else {
        emptyList()
    }

@Suppress("ReturnCount")
fun V2Repository.isEquivalentTo(repository: UnifiedDependencyRepository): Boolean {

    // URL matching
    if (repository.url.isNotBlank()) {
        val normalizedRepositoryUrl = repository.url.trimEnd('/')

        if (this.url.trimEnd('/').equals(normalizedRepositoryUrl, true)) {
            return true
        }

        if (this.alternateUrls != null && this.alternateUrls.any { it.trimEnd('/').equals(normalizedRepositoryUrl, true) }) {
            return true
        }
    }

    // ID matching
    if (this.id.replace("_", "").equals(repository.id, true)) {
        return true
    }

    // Account for repositories API returning "gmaven" vs. parser using "google"
    if (this.id == "gmaven" && repository.id == "google") {
        return true
    }

    // Account for repositories API returning "maven_central" vs. parser using "central"
    if (this.id == "maven_central" && repository.id == "central") {
        return true
    }

    return false
}

@Nls
fun V2Repository.localizedName() = when (this.id) {
    "maven_central" -> PackageSearchBundle.message("packagesearch.repository.known.id.maven_central")
    "gmaven" -> PackageSearchBundle.message("packagesearch.repository.known.id.google")
    "jcenter" -> PackageSearchBundle.message("packagesearch.repository.known.id.jcenter")
    else -> if (this.friendlyName.isNotBlank()) this.friendlyName else this.id
}
