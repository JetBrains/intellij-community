// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.jcef.fileActions.export

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.markdown.jcef.preview.HtmlResourceSavingSettings

@State(name = "HtmlExportSettings", storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE)])
internal class MarkdownHtmlExportSettings : PersistentStateComponent<MarkdownHtmlExportSettings> {
  var saveResources: Boolean = false
  var resourceDirectory: String = ""

  override fun getState(): MarkdownHtmlExportSettings = this

  override fun loadState(state: MarkdownHtmlExportSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  fun getResourceSavingSettings() = HtmlResourceSavingSettings(saveResources, resourceDirectory)
}
