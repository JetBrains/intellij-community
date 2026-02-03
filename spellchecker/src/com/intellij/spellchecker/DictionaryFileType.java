// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public class DictionaryFileType implements FileType {
  public static final DictionaryFileType INSTANCE = new DictionaryFileType();

  private DictionaryFileType() {
  }

  @Override
  public @NotNull String getName() {
    return "Dictionary";
  }

  @Override
  public @NotNull String getDescription() {
    return SpellCheckerBundle.message("filetype.dictionary.description");
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return SpellCheckerBundle.message("filetype.dictionary.display.name");
  }

  @Override
  public @NotNull String getDefaultExtension() {
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
