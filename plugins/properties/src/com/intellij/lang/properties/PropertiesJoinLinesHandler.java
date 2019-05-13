/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class PropertiesJoinLinesHandler implements JoinLinesHandlerDelegate {
  @Override
  public int tryJoinLines(@NotNull final Document doc, @NotNull final PsiFile psiFile, int start, final int end) {
    if (!(psiFile instanceof PropertiesFile)) return -1;
    // strip continuation char
    if (PropertiesUtil.isUnescapedBackSlashAtTheEnd(doc.getText().substring(0, start + 1))) {
      doc.deleteString(start, start + 1);
      start--;
    }
    return start + 1;
  }
}
