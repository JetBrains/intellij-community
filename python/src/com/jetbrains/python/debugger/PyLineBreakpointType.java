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

import com.google.common.collect.Sets;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XLineBreakpointTypeBase;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.sdk.PySdkUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;


public class PyLineBreakpointType extends XLineBreakpointTypeBase {
  public static final String ID = "python-line";
  private static final String NAME = "Python Line Breakpoint";

  private final static Set<IElementType> UNSTOPPABLE_ELEMENT_TYPES = Sets.newHashSet(PyTokenTypes.TRIPLE_QUOTED_STRING,
                                                                                     PyTokenTypes.SINGLE_QUOTED_STRING,
                                                                                     PyTokenTypes.SINGLE_QUOTED_UNICODE,
                                                                                     PyTokenTypes.DOCSTRING);


  private final static Class[] UNSTOPPABLE_ELEMENTS = new Class[]{PsiWhiteSpace.class, PsiComment.class};


  public PyLineBreakpointType() {
    super(ID, NAME, new PyDebuggerEditorsProvider());
  }

  @Override
  public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull final Project project) {
    final Ref<Boolean> stoppable = Ref.create(false);
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      lineHasStoppablePsi(project, file, line, PythonFileType.INSTANCE, document, UNSTOPPABLE_ELEMENTS, UNSTOPPABLE_ELEMENT_TYPES, stoppable
      );
    }

    return stoppable.get();
  }

  public static void lineHasStoppablePsi(@NotNull Project project,
                                         @NotNull VirtualFile file,
                                         int line,
                                         PythonFileType fileType,
                                         Document document,
                                         Class[] unstoppablePsiElements,
                                         Set<IElementType> unstoppableElementTypes,
                                         Ref<Boolean> stoppable) {
    if ((file.getFileType() == fileType || isPythonScratch(project, file)) && !isSkeleton(project, file)) {
      XDebuggerUtil.getInstance().iterateLine(project, document, line, psiElement -> {

        if (PsiTreeUtil.getNonStrictParentOfType(psiElement, unstoppablePsiElements) != null) {
          return true;
        }

        if (psiElement.getNode() != null && unstoppableElementTypes.contains(psiElement.getNode().getElementType())) return true;

        // Python debugger seems to be able to stop on pretty much everything
        stoppable.set(true);
        return false;
      });

      if (PyDebugSupportUtils.isContinuationLine(document, line - 1)) {
        stoppable.set(false);
      }
    }
  }

  @Override
  public boolean isSuspendThreadSupported() {
    return true;
  }

  @Override
  public SuspendPolicy getDefaultSuspendPolicy() {
    return SuspendPolicy.THREAD;
  }

  private static boolean isSkeleton(@NotNull Project project, @NotNull VirtualFile file) {
    if (PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(file)) return true;

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    return psiFile != null && PySdkUtil.isElementInSkeletons(psiFile);
  }

  private static boolean isPythonScratch(@NotNull Project project, @NotNull VirtualFile file) {
    return ScratchUtil.isScratch(file) && LanguageUtil.getLanguageForPsi(project, file) == PythonLanguage.INSTANCE;
  }

  @Override
  public String getBreakpointsDialogHelpTopic() {
    return "reference.dialogs.breakpoints";
  }
}
