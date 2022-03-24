package com.jetbrains.packagesearch.intellij.plugin.gradle.configuration

import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.jetbrains.packagesearch.intellij.plugin.configuration.addSearchConfigurationMap

internal class GradleSearchableOptionContributor : SearchableOptionContributor() {

    override fun processOptions(processor: SearchableOptionProcessor) {
        // Make settings searchable
        addSearchConfigurationMap(
            processor,
            "gradle", "configuration"
        )
    }
}
