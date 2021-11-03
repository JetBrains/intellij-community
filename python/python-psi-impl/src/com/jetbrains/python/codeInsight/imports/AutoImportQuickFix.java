// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
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

  private final List<ImportCandidateHolder> myImports; // from where and what to import
  private final String myInitialName;
  private final boolean myUseQualifiedImport;
  private final @NotNull Class<? extends PsiReference> myReferenceType;
  private boolean myExpended = false;

  /**
   * Creates a new, empty fix object.
   * @param node to which the fix applies.
   * @param referenceType
   * @param name name to import
   * @param qualify if true, add an "import ..." statement and qualify the name; else use "from ... import name"
   */
  public AutoImportQuickFix(@NotNull PsiElement node,
                            @NotNull Class<? extends PsiReference> referenceType,
                            @NotNull String name,
                            boolean qualify) {
    this(node, referenceType, name, qualify, Collections.emptyList());
  }

  private AutoImportQuickFix(@NotNull PsiElement node,
                             @NotNull Class<? extends PsiReference> referenceType,
                             @NotNull String name,
                             boolean qualify,
                             @NotNull Collection<ImportCandidateHolder> candidates) {
    super(node);
    myReferenceType = referenceType;
    myInitialName = name;
    myUseQualifiedImport = qualify;
    myImports = new ArrayList<>(candidates);
  }

  /**
   * Adds another import source.
   * @param importable an element that could be imported either from import element or from file.
   * @param file the file which is the source of the importable
   * @param importElement an existing import element that can be a source for the importable.
   */
  public void addImport(@NotNull PsiNamedElement importable, @NotNull PsiFile file, @Nullable PyImportElement importElement) {
    myImports.add(new ImportCandidateHolder(importable, file, importElement, null));
  }

  /**
   * Adds another import source.
   * @param importable an element that could be imported either from import element or from file.
   * @param file the file which is the source of the importable
   * @param path import path for the file, as a qualified name (a.b.c)
   */
  public void addImport(@NotNull PsiNamedElement importable, @NotNull PsiFileSystemItem file, @Nullable QualifiedName path) {
    myImports.add(new ImportCandidateHolder(importable, file, null, path));
  }

  public void addImport(@NotNull PsiNamedElement importable,
                        @NotNull PsiFileSystemItem file,
                        @Nullable QualifiedName path,
                        @Nullable String asName) {
    addImport(importable, file, null, path, asName);
  }

  public void addImport(@NotNull PsiNamedElement importable,
                        @NotNull PsiFileSystemItem file,
                        @Nullable PyImportElement importElement,
                        @Nullable QualifiedName path,
                        @Nullable String asName) {
    myImports.add(new ImportCandidateHolder(importable, file, importElement, path, asName));
  }

  @Override
  @NotNull
  public String getText() {
    if (myUseQualifiedImport) return PyPsiBundle.message("ACT.qualify.with.module");
    else if (myImports.size() == 1) {
      return PyPsiBundle.message("QFIX.auto.import.import.name", myImports.get(0).getPresentableText());
    }
    else {
      return PyPsiBundle.message("QFIX.auto.import.import.this.name");
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.auto.import");
  }

  @NotNull
  public ImportFromExistingAction createAction(PsiElement element) {
    final ImportFromExistingAction action =
      new ImportFromExistingAction(element, myImports, myInitialName, null, myUseQualifiedImport, false);
    action.onDone(() -> myExpended = true);
    return action;
  }

  @Override
  public boolean isAvailable() {
    final PsiElement element = getStartElement();
    if (element == null) {
      return false;
    }
    PyPsiUtils.assertValid(element);
    return !myExpended && element.isValid() && !myImports.isEmpty();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    invoke(getStartElement().getContainingFile());
  }

  public void invoke(PsiFile file) throws IncorrectOperationException {
    // make sure file is committed, writable, etc
    final PsiElement startElement = getStartElement();
    if (startElement == null) {
      return;
    }
    PyPsiUtils.assertValid(startElement);
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    final PsiReference reference = findOriginalReference(startElement);
    if (reference == null || isResolved(reference)) return;
    // act
    ImportFromExistingAction action = createAction();
    if (action != null) {
      action.execute(); // assume that action runs in WriteAction on its own behalf
    }
    myExpended = true;
  }

  @Nullable
  protected ImportFromExistingAction createAction() {
    return new ImportFromExistingAction(getStartElement(), myImports, myInitialName, null, myUseQualifiedImport, false);
  }

  public void sortCandidates() {
    Collections.sort(myImports);
  }

  @NotNull
  public List<ImportCandidateHolder> getCandidates() {
    return Collections.unmodifiableList(myImports);
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
      PsiFileSystemItem importFile = anImport.getFile();
      VirtualFile file = importFile != null ? importFile.getVirtualFile() : null;
      if (file != null && fileIndex.isInContent(file)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public AutoImportQuickFix forLocalImport() {
    return new AutoImportQuickFix(getStartElement(), myReferenceType, myInitialName, myUseQualifiedImport, myImports) {
      @NotNull
      @Override
      public String getFamilyName() {
        return PyPsiBundle.message("QFIX.NAME.local.auto.import");
      }

      @NotNull
      @Override
      public String getText() {
        return PyPsiBundle.message("QFIX.local.auto.import.import.locally", super.getText());
      }

      @NotNull
      @Override
      protected ImportFromExistingAction createAction() {
        return new ImportFromExistingAction(getStartElement(), myImports, myInitialName, null, myUseQualifiedImport, true);
      }
    };
  }

  @NotNull
  public String getNameToImport() {
    return myInitialName;
  }

  public @NotNull Class<? extends PsiReference> getReferenceType() {
    return myReferenceType;
  }

  public boolean isUseQualifiedImport() {
    return myUseQualifiedImport;
  }

  static boolean isResolved(@NotNull PsiReference reference) {
    if (reference instanceof PsiPolyVariantReference) {
      return ((PsiPolyVariantReference)reference).multiResolve(false).length > 0;
    }
    return reference.resolve() != null;
  }

  @Nullable
  PsiReference findOriginalReference(@NotNull PsiElement element) {
    return ContainerUtil.findInstance(element.getReferences(), myReferenceType);
  }
}
