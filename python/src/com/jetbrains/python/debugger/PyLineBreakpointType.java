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
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XLineBreakpointTypeBase;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.sdk.PySdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;


public class PyLineBreakpointType extends XLineBreakpointTypeBase {
  public static final String ID = "python-line";
  private static final String NAME = "Python Line Breakpoint";

  private final static Set<IElementType> UNSTOPPABLE_ELEMENT_TYPES = Sets.newHashSet(PyTokenTypes.TRIPLE_QUOTED_STRING,
                                                                                     PyTokenTypes.SINGLE_QUOTED_STRING,
                                                                                     PyTokenTypes.SINGLE_QUOTED_UNICODE,
                                                                                     PyTokenTypes.DOCSTRING);

  @SuppressWarnings("unchecked")
  private final static Class<? extends PsiElement>[] UNSTOPPABLE_ELEMENTS = new Class[]{PsiWhiteSpace.class, PsiComment.class};

  public PyLineBreakpointType() {
    super(ID, NAME, new PyDebuggerEditorsProvider());
  }

  public PyLineBreakpointType(@NotNull final String id, @NotNull final String title, @Nullable XDebuggerEditorsProvider editorsProvider) {
    super(id, title, editorsProvider);
  }

  @Override
  public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull final Project project) {
    final Ref<Boolean> stoppable = Ref.create(false);
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null && isSuitableFileType(project, file)) {
      lineHasStoppablePsi(project, file, line, document, getUnstoppableElements(), getUnstoppableElementTypes(), stoppable);
    }

    return stoppable.get();
  }

  protected boolean isSuitableFileType(@NotNull Project project, @NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, getFileType()) ||
           (ScratchUtil.isScratch(file) && LanguageUtil.getLanguageForPsi(project, file) == getFileLanguage());
  }

  protected FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  protected Language getFileLanguage() {
    return PythonLanguage.INSTANCE;
  }

  protected Set<IElementType> getUnstoppableElementTypes() {
    return UNSTOPPABLE_ELEMENT_TYPES;
  }

  protected Class<? extends PsiElement>[] getUnstoppableElements() {
    return UNSTOPPABLE_ELEMENTS;
  }

  /**
   * We can't rely only on file type, because there are Cython files which contain
   * Python & Cython elements and there are Jupyter files which contain Python & Markdown elements
   * That's why we should check that there is at least one stoppable psiElement at the line
   *
   * @param psiElement to check
   * @return true if psiElement is compatible with breakpoint type and false otherwise
   */
  protected boolean isPsiElementStoppable(PsiElement psiElement) {
    return psiElement.getLanguage() == PythonLanguage.INSTANCE;
  }

  protected void lineHasStoppablePsi(@NotNull Project project,
                                     @NotNull VirtualFile file,
                                     int line,
                                     Document document,
                                     Class<? extends PsiElement>[] unstoppablePsiElements,
                                     Set<IElementType> unstoppableElementTypes,
                                     Ref<? super Boolean> stoppable) {
    if (!isSkeleton(project, file)) {
      XDebuggerUtil.getInstance().iterateLine(project, document, line, psiElement -> {
        if (PsiTreeUtil.getNonStrictParentOfType(psiElement, unstoppablePsiElements) != null) return true;
        if (psiElement.getNode() != null && unstoppableElementTypes.contains(psiElement.getNode().getElementType())) return true;
        if (isPsiElementStoppable(psiElement)) {
          stoppable.set(true);
        }
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

  @Override
  public String getBreakpointsDialogHelpTopic() {
    return "reference.dialogs.breakpoints";
  }
}
