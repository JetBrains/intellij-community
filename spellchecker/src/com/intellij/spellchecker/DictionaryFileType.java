/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.spellchecker;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DictionaryFileType implements FileType {
  public static final DictionaryFileType INSTANCE = new DictionaryFileType();

  private DictionaryFileType() {
  }

  @NotNull
  @Override
  public String getName() {
    return "Dictionary";
  }

  @NotNull
  @Override
  public String getDescription() {
    return SpellCheckerBundle.message("filetype.dictionary.description");
  }

  @Nls
  @Override
  public @NotNull String getDisplayName() {
    return SpellCheckerBundle.message("filetype.dictionary.display.name");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "dic";
  }

  @Override
  public Icon getIcon() {
    return SpellcheckerIcons.Dictionary;
  }

  @Override
  public boolean isBinary() {
    return false;
  }
}
