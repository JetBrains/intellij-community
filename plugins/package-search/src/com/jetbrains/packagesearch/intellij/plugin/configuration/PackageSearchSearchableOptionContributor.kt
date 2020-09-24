package com.jetbrains.packagesearch.intellij.plugin.configuration

import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.jetbrains.packagesearch.intellij.plugin.configuration.ui.PackageSearchGeneralConfigurable

class PackageSearchSearchableOptionContributor : SearchableOptionContributor() {
    override fun processOptions(processor: SearchableOptionProcessor) {
        // Make settings searchable
        addSearchConfigurationMap(
            processor,
            "packagesearch", "package", "search", "dependency", "dependencies",
            "gradle", "configuration", "maven", "scope", "reformat", "refresh", "import"
        )
    }
}

fun addSearchConfigurationMap(processor: SearchableOptionProcessor, vararg entries: String) {
    for (entry in entries) {
        processor.addOptions(entry, null, entry, PackageSearchGeneralConfigurable.ID, null, false)
    }
}
