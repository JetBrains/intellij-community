package com.jetbrains.packagesearch.intellij.plugin.extensions.maven.configuration

import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.jetbrains.packagesearch.intellij.plugin.configuration.addSearchConfigurationMap

private class MavenSearchableOptionContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    // Make settings searchable
    addSearchConfigurationMap(
      processor,
      "maven", "scope"
    )
  }
}
