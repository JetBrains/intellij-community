// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.config

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import icons.MavenIcons
import javax.swing.Icon

class MavenConfigFileType : LanguageFileType(PlainTextLanguage.INSTANCE) {

  override fun getName(): String {
    return "MavenConfig"
  }

  override fun getDescription(): String {
    return "MavenConfig"
  }

  override fun getDefaultExtension(): String {
    return "config"
  }

  override fun getIcon(): Icon? {
    return MavenIcons.MavenLogo
  }
}

