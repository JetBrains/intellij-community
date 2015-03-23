/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.commandInterface.commandLine;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Command line file type
 * @author Ilya.Kazakevich
 */
public final class CommandLineFileType extends LanguageFileType {
  public static final FileType INSTANCE = new CommandLineFileType();
  /**
   * Command line extension
   */
  static final String EXTENSION = "cmdline";

  CommandLineFileType() {
    super(CommandLineLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return CommandLineLanguage.INSTANCE.getID();
  }

  @NotNull
  @Override
  public String getDescription() {
    return CommandLineLanguage.INSTANCE.getID();
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return '.' + EXTENSION;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return null;
  }
}
