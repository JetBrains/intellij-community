// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.youtrack.lang;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public final class YouTrackLanguage extends Language implements DependentLanguage {

  public static final YouTrackLanguage INSTANCE = new YouTrackLanguage();

  private YouTrackLanguage() {
    super("YouTrack");
  }

  @Override
  public boolean isCaseSensitive() {
    return false;
  }

  @Override
  public @NotNull LanguageFileType getAssociatedFileType() {
    return YouTrackFileType.INSTANCE;
  }
}
