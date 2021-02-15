package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.configuration

import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.jetbrains.packagesearch.intellij.plugin.configuration.addSearchConfigurationMap

private class GradleSearchableOptionContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    // Make settings searchable
    addSearchConfigurationMap(
      processor,
      "gradle", "configuration"
    )
  }
}
