/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.jetbrains.typoscript.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


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


