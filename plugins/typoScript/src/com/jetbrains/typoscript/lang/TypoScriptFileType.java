package com.jetbrains.typoscript.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author lene
 *         Date: 03.04.12
 */
public class TypoScriptFileType extends LanguageFileType {
  private static final Icon ICON = IconLoader.getIcon("/icons/typo3.png");

  public static final TypoScriptFileType INSTANCE = new TypoScriptFileType();
  public static final String DEFAULT_EXTENSION = "ts";

  private TypoScriptFileType() {
    super(TypoScriptLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "TypoScript";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "TypoScript";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }
}


