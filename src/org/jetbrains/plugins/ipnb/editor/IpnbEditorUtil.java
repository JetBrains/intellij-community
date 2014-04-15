/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.ipnb.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class IpnbEditorUtil {

  public static Editor createPythonCodeEditor(@NotNull Project project, @NotNull String text) {
    Editor editor = EditorFactory.getInstance().createEditor(createPythonCodeDocument(project, text), project, PythonFileType.INSTANCE, false);
    return editor;
  }

  @NotNull
  public static Document createPythonCodeDocument(@NotNull final Project project,
                                                  @NotNull String text) {
    text = text.trim();
    final PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(project, "code.py", text, true);

    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }
}
