/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.spellchecker;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import icons.SpellcheckerIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DictionaryFileType implements FileType {
  public static final DictionaryFileType INSTANCE = new DictionaryFileType();

  @NotNull
  @Override
  public String getName() {
    return SpellCheckerBundle.message("dictionary.filetype.name");
  }

  @NotNull
  @Override
  public String getDescription() {
    return SpellCheckerBundle.message("dictionary.filetype.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "dic";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return SpellcheckerIcons.Dictionary;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Nullable
  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
    return null;
  }
}
