/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class RncFileType extends LanguageFileType {
  @SuppressWarnings("unused")
  public static final String RNC_EXT = "rnc";

  public static final FileType INSTANCE = new RncFileType();

  private RncFileType() {
    super(RngCompactLanguage.INSTANCE);
  }

  @Override
  public @NotNull @NonNls String getName() {
    return "RNG Compact";
  }

  @Override
  public @NotNull String getDescription() {
    return RelaxngBundle.message("filetype.relaxng.compact-syntax.description");
  }

  @Override
  public @NotNull @NonNls String getDefaultExtension() {
    return "rnc";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Text;
  }

  public static FileType getInstance() {
    return INSTANCE;
  }
}
