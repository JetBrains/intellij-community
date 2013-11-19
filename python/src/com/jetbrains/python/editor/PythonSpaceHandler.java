/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class PythonSpaceHandler extends TypedHandlerDelegate {
  @Override
  public Result charTyped(char c, Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    if (c == ' ' && codeInsightSettings.JAVADOC_STUB_ON_ENTER) {
      int offset = editor.getCaretModel().getOffset();
      PsiElement element = file.findElementAt(offset);
      if (element == null && offset > 1)
        element = file.findElementAt(offset-2);
      if (element == null) return Result.CONTINUE;
      int expectedStringStart = editor.getCaretModel().getOffset()-4;        // """ or ''' plus space char
      if (PythonDocCommentUtil.atDocCommentStart(element, expectedStringStart)) {
        PythonDocumentationProvider provider = new PythonDocumentationProvider();
        PyFunction fun = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        final PsiElement parent = element.getParent();
        if (fun != null) {
          String docStub = provider.generateDocumentationContentStub(fun, false);
          docStub += parent.getText().substring(0,3);
          if (docStub.length() != 0) {
            editor.getDocument().insertString(editor.getCaretModel().getOffset(), docStub);
            Module module = ModuleUtilCore.findModuleForPsiElement(element);
            if (module == null) return Result.CONTINUE;
            PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(module);
            if (!documentationSettings.myDocStringFormat.equals(DocStringFormat.PLAIN))
              editor.getCaretModel().moveCaretRelatively(100, 1, false, false, false);
            return Result.STOP;
          }
        }
        PyElement klass = PsiTreeUtil.getParentOfType(element, PyClass.class, PyFile.class);
        if (klass != null) {
          editor.getDocument().insertString(editor.getCaretModel().getOffset(),
                          PythonDocCommentUtil.generateDocForClass(klass, parent.getText().substring(0, 3)));
          return Result.STOP;
        }
      }
    }
    return Result.CONTINUE;
  }
}
