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
package org.jetbrains.idea.maven.dom.code;

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

public final class MavenBackspaceHandlerDelegate extends BackspaceHandlerDelegate {
  @Override
  public void beforeCharDeleted(char c, PsiFile file, Editor editor) {
  }

  @Override
  public boolean charDeleted(char c, PsiFile file, Editor editor) {
    if (c != '{') return false;
    if (!MavenTypedHandlerDelegate.shouldProcess(file)) return false;

    int offset = editor.getCaretModel().getOffset();
    CharSequence text = editor.getDocument().getCharsSequence();
    if (offset < text.length() && text.charAt(offset) == '}') {
      editor.getDocument().deleteString(offset, offset + 1);
      return true;
    }
    return false;
  }
}