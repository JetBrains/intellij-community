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
package com.jetbrains.typoscript.lang.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.jetbrains.typoscript.lang.TypoScriptFileType;
import com.jetbrains.typoscript.lang.TypoScriptLanguage;
import org.jetbrains.annotations.NotNull;


public class TypoScriptFile  extends PsiFileBase {

  public TypoScriptFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, TypoScriptLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return TypoScriptFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "TypoScript File";
  }
}
