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
package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The object contains a list of import candidates and serves only to show the initial hint;
 * the actual work is done in ImportFromExistingAction..
 *
 * @author dcheryasov
 */
public class AutoImportQuickFix extends LocalQuickFixOnPsiElement implements HighPriorityAction {

  protected final PsiReference myReference;
  protected final List<ImportCandidateHolder> myImports; // from where and what to import
  protected final String myInitialName;
  protected final boolean myUseQualifiedImport;
  private boolean myExpended;

  /**
   * Creates a new, empty fix object.
   * @param node to which the fix applies.
   * @param name name to import
   * @param qualify if true, add an "import ..." statement and qualify the name; else use "from ... import name"
   */
  public AutoImportQuickFix(@NotNull PsiElement node, @NotNull PsiReference reference, @NotNull String name, boolean qualify) {
    this(node, reference, name, qualify, Collections.<ImportCandidateHolder>emptyList());
  }

  protected AutoImportQuickFix(@NotNull PsiElement node, @NotNull PsiReference reference, @NotNull String name, boolean qualify,
                               @NotNull Collection<ImportCandidateHolder> candidates) {
    super(node);
    myReference = reference;
    myImports = new ArrayList<ImportCandidateHolder>(candidates);
    myInitialName = name;
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

  public void addImport(@NotNull PsiElement importable, @NotNull PsiFileSystemItem file, @Nullable QualifiedName path, @Nullable String asName) {
    myImports.add(new ImportCandidateHolder(importable, file, null, path, asName));
  }

  @NotNull
  public String getText() {
    if (myUseQualifiedImport) return PyBundle.message("ACT.qualify.with.module");
    else if (myImports.size() == 1) {
      return PyBundle.message("QFIX.auto.import.import.name", myImports.get(0).getPresentableText(myInitialName));
    }
    else {
      return PyBundle.message("QFIX.auto.import.import.this.name");
    }
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.auto.import.family");
  }

  public boolean showHint(Editor editor) {
    if (!PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP) {
      return false;
    }
    final PsiElement element = getStartElement();
    if (element == null || !element.isValid() || (element instanceof PsiNamedElement && ((PsiNamedElement)element).getName() == null)
        || myImports.size() <= 0) {
      return false; // TODO: also return false if an on-the-fly unambiguous fix is possible?
    }
    if (ImportFromExistingAction.isResolved(myReference)) {
      return false;
    }
    if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) {
      return false;
    }
    if ((element instanceof PyQualifiedExpression) && ((((PyQualifiedExpression)element).isQualified()))) return false; // we cannot be qualified

    final String message = ShowAutoImportPass.getMessage(
      myImports.size() > 1,
      ImportCandidateHolder.getQualifiedName(myInitialName, myImports.get(0).getPath(), myImports.get(0).getImportElement())
    );
    final ImportFromExistingAction action = new ImportFromExistingAction(element, myImports, myInitialName, myUseQualifiedImport, false);
    action.onDone(new Runnable() {
      public void run() {
        myExpended = true;
      }
    });
    HintManager.getInstance().showQuestionHint(
      editor, message,
      element.getTextOffset(),
      element.getTextRange().getEndOffset(), action);
    return true;
  }

  public boolean isAvailable() {
    return !myExpended && getStartElement() != null && getStartElement().isValid() && myImports.size() > 0;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    invoke(getStartElement().getContainingFile());
  }

  public void invoke(PsiFile file) throws IncorrectOperationException {
    // make sure file is committed, writable, etc
    if (!myReference.getElement().isValid()) return;
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    if (ImportFromExistingAction.isResolved(myReference)) return;
    // act
    ImportFromExistingAction action = createAction();
    action.execute(); // assume that action runs in WriteAction on its own behalf
    myExpended = true;
  }

  @NotNull
  protected ImportFromExistingAction createAction() {
    return new ImportFromExistingAction(getStartElement(), myImports, myInitialName, myUseQualifiedImport, false);
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

  public boolean hasProjectImports() {
    ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(getStartElement().getProject());
    for (ImportCandidateHolder anImport : myImports) {
      VirtualFile file = anImport.getFile().getVirtualFile();
      if (file != null && fileIndex.isInContent(file)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public AutoImportQuickFix forLocalImport() {
    return new AutoImportQuickFix(getStartElement(), myReference, myInitialName, myUseQualifiedImport, myImports) {
      @NotNull
      @Override
      public String getFamilyName() {
        return PyBundle.message("QFIX.local.auto.import.family");
      }

      @NotNull
      @Override
      public String getText() {
        return PyBundle.message("QFIX.local.auto.import.import.locally", super.getText());
      }

      @NotNull
      @Override
      protected ImportFromExistingAction createAction() {
        return new ImportFromExistingAction(getStartElement(), myImports, myInitialName, myUseQualifiedImport, true);
      }
    };
  }

  public String getNameToImport() {
    return myInitialName;
  }
}
