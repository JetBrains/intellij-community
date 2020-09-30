// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.youtrack.lang;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public final class YouTrackLanguage extends Language implements DependentLanguage {

  public final static YouTrackLanguage INSTANCE = new YouTrackLanguage();

  private YouTrackLanguage() {
    super("YouTrack");
  }

  @Override
  public boolean isCaseSensitive() {
    return false;
  }

  @NotNull
  @Override
  public LanguageFileType getAssociatedFileType() {
    return YouTrackFileType.INSTANCE;
  }
}
