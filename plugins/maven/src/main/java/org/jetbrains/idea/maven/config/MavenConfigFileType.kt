// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.config

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import icons.OpenapiIcons.RepositoryLibraryLogo
import org.jetbrains.idea.maven.project.MavenProjectBundle
import javax.swing.Icon

class MavenConfigFileType private constructor(): LanguageFileType(PlainTextLanguage.INSTANCE, true) {

  override fun getName(): String {
    return "MavenConfig"
  }

  override fun getDescription(): String = MavenProjectBundle.message("filetype.maven.config.description")
  override fun getDisplayName(): String = MavenProjectBundle.message("filetype.maven.config.display.name")

  override fun getDefaultExtension(): String {
    return "config"
  }

  override fun getIcon(): Icon {
    return RepositoryLibraryLogo
  }
}

