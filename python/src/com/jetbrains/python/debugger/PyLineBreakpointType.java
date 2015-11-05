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
package com.jetbrains.python.debugger;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Processor;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XLineBreakpointTypeBase;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;


public class PyLineBreakpointType extends XLineBreakpointTypeBase {
  public static final String ID = "python-line";
  private static final String NAME = "Python Line Breakpoint";

  public PyLineBreakpointType() {
    super(ID, NAME, new PyDebuggerEditorsProvider());
  }

  @Override
  public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull final Project project) {
    final Ref<Boolean> stoppable = Ref.create(false);
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      if (file.getFileType() == PythonFileType.INSTANCE || isPythonScratch(project, file)) {
        XDebuggerUtil.getInstance().iterateLine(project, document, line, new Processor<PsiElement>() {
          @Override
          public boolean process(PsiElement psiElement) {
            if (psiElement instanceof PsiWhiteSpace || psiElement instanceof PsiComment) return true;
            if (psiElement.getNode() != null && notStoppableElementType(psiElement.getNode().getElementType())) return true;

            // Python debugger seems to be able to stop on pretty much everything
            stoppable.set(true);
            return false;
          }
        });

        if (PyDebugSupportUtils.isContinuationLine(document, line - 1)) {
          stoppable.set(false);
        }
      }
    }

    return stoppable.get();
  }

  private static boolean isPythonScratch(@NotNull Project project, @NotNull VirtualFile file) {
    return ScratchUtil.isScratch(file) && LanguageUtil.getLanguageForPsi(project, file) == PythonLanguage.INSTANCE;
  }

  private static boolean notStoppableElementType(IElementType elementType) {
    return elementType == PyTokenTypes.TRIPLE_QUOTED_STRING ||
           elementType == PyTokenTypes.SINGLE_QUOTED_STRING ||
           elementType == PyTokenTypes.SINGLE_QUOTED_UNICODE ||
           elementType == PyTokenTypes.DOCSTRING;
  }

  @Override
  public String getBreakpointsDialogHelpTopic() {
    return "reference.dialogs.breakpoints";
  }
}
