// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.youtrack.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.tasks.TaskBundle;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public final class YouTrackFileType extends LanguageFileType {
  public static final YouTrackFileType INSTANCE = new YouTrackFileType();
  public static final String DEFAULT_EXTENSION = "youtrack";

  private YouTrackFileType() {
    super(YouTrackLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "YouTrack";
  }

  @NotNull
  @Override
  public String getDescription() {
    return TaskBundle.message("filetype.youtrack.query.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return TasksCoreIcons.Youtrack;
  }
}
