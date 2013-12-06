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
package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The object contains a list of import candidates and serves only to show the initial hint;
 * the actual work is done in ImportFromExistingAction..
 *
 * @author dcheryasov
 */
public class AutoImportQuickFix implements LocalQuickFix, HighPriorityAction {

  private final PyElement myNode;
  private final PsiReference myReference;

  private final List<ImportCandidateHolder> myImports; // from where and what to import
  private final String myInitialName;

  boolean myUseQualifiedImport;
  private boolean myExpended;

  /**
   * Creates a new, empty fix object.
   * @param node to which the fix applies.
   * @param qualify if true, add an "import ..." statement and qualify the name; else use "from ... import name"
   */
  public AutoImportQuickFix(PyElement node, PsiReference reference, boolean qualify) {
    myNode = node;
    myReference = reference;
    myImports = new ArrayList<ImportCandidateHolder>();
    myInitialName = getNameToImport();
    myUseQualifiedImport = qualify;
    myExpended = false;
  }

  /**
   * Adds another import source.
   * @param importable an element that could be imported either from import element or from file.
   * @param file the file which is the source of the importable
   * @param importElement an existing import element that can be a source for the importable.
   */
  public void addImport(@NotNull PsiElement importable, @NotNull PsiFile file, @Nullable PyImportElement importElement) {
    myImports.add(new ImportCandidateHolder(importable, file, importElement, null));
  }

  /**
   * Adds another import source.
   * @param importable an element that could be imported either from import element or from file.
   * @param file the file which is the source of the importable
   * @param path import path for the file, as a qualified name (a.b.c)
   */
  public void addImport(@NotNull PsiElement importable, @NotNull PsiFileSystemItem file, @Nullable QualifiedName path) {
    myImports.add(new ImportCandidateHolder(importable, file, null, path));
  }

  @NotNull
  public String getText() {
    if (myUseQualifiedImport) return PyBundle.message("ACT.qualify.with.module");
    else if (myImports.size() == 1) {
      return "Import '" + myImports.get(0).getPresentableText(getNameToImport()) + "'";
    }
    else {
      return PyBundle.message("ACT.NAME.use.import");
    }
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("ACT.FAMILY.import");
  }

  public boolean showHint(Editor editor) {
    if (!PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP) {
      return false;
    }
    if (myNode == null || !myNode.isValid() || myNode.getName() == null || myImports.size() <= 0) {
      return false; // TODO: also return false if an on-the-fly unambiguous fix is possible?
    }
    if (ImportFromExistingAction.isResolved(myReference)) {
      return false;
    }
    if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) {
      return false;
    }
    if ((myNode instanceof PyQualifiedExpression) && ((((PyQualifiedExpression)myNode).getQualifier() != null))) return false; // we cannot be qualified
    String name = getNameToImport();
    if (!name.equals(myInitialName)) {
      return false;
    }
    final String message = ShowAutoImportPass.getMessage(
      myImports.size() > 1,
      ImportCandidateHolder.getQualifiedName(name, myImports.get(0).getPath(), myImports.get(0).getImportElement())
    );
    final ImportFromExistingAction action = new ImportFromExistingAction(myNode, myImports, name, myUseQualifiedImport);
    action.onDone(new Runnable() {
      public void run() {
        myExpended = true;
      }
    });
    HintManager.getInstance().showQuestionHint(
      editor, message,
      myNode.getTextOffset(),
      myNode.getTextRange().getEndOffset(), action);
    return true;
  }

  public boolean isAvailable() {
    return !myExpended && myNode != null && myNode.isValid() && myImports.size() > 0;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    invoke(descriptor.getPsiElement().getContainingFile());
    myExpended = true;
  }

  public void invoke(PsiFile file) throws IncorrectOperationException {
    // make sure file is committed, writable, etc
    if (!myReference.getElement().isValid()) return;
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    if (ImportFromExistingAction.isResolved(myReference)) return;
    // act
    ImportFromExistingAction action = new ImportFromExistingAction(myNode, myImports, getNameToImport(), myUseQualifiedImport);
    action.execute(); // assume that action runs in WriteAction on its own behalf
    myExpended = true;
  }

  public void sortCandidates() {
    Collections.sort(myImports);
  }

  public int getCandidatesCount() {
    return myImports.size();
  }

  public boolean hasOnlyFunctions() {
    for (ImportCandidateHolder holder : myImports) {
      if (!(holder.getImportable() instanceof PyFunction)) {
        return false;
      }
    }
    return true;
  }

  public String getNameToImport() {
    final String text = myReference.getElement().getText();
    return myReference.getRangeInElement().substring(text); // text of the part we're working with
  }

  public boolean hasProjectImports() {
    ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(myNode.getProject());
    for (ImportCandidateHolder anImport : myImports) {
      VirtualFile file = anImport.getFile().getVirtualFile();
      if (file != null && fileIndex.isInContent(file)) {
        return true;
      }
    }
    return false;
  }
}
