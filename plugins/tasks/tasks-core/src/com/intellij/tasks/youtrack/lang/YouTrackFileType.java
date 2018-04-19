package com.intellij.tasks.youtrack.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class YouTrackFileType extends LanguageFileType {
  public static final YouTrackFileType INSTANCE = new YouTrackFileType();
  public static final String DEFAULT_EXTENSION = "youtrack";

  public YouTrackFileType() {
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
    return "YouTrack query";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return TasksCoreIcons.Youtrack;
  }
}
