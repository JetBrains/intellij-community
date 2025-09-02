// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The object contains a list of import candidates and serves only to show the initial hint;
 * the actual work is done in ImportFromExistingAction..
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
  public @NotNull String getText() {
    if (myUseQualifiedImport) return PyPsiBundle.message("ACT.qualify.with.module");
    else if (myImports.size() == 1) {
      return PyPsiBundle.message("QFIX.auto.import.import.name", myImports.get(0).getPresentableText());
    }
    else {
      return PyPsiBundle.message("QFIX.auto.import.import.this.name");
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.auto.import");
  }

  public @NotNull ImportFromExistingAction createAction(PsiElement element) {
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
  public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    invoke();
  }

  public void invoke() throws IncorrectOperationException {
    // make sure file is committed, writable, etc
    final PsiElement startElement = getStartElement();
    if (startElement == null) {
      return;
    }
    final PsiReference reference = findOriginalReference(startElement);
    if (reference == null || isResolved(reference)) return;
    // act
    ImportFromExistingAction action = createAction();
    if (action != null) {
      action.execute(); // assume that action runs in WriteAction on its own behalf
    }
    myExpended = true;
  }

  protected @Nullable ImportFromExistingAction createAction() {
    return new ImportFromExistingAction(getStartElement(), myImports, myInitialName, null, myUseQualifiedImport, false);
  }

  public void sortCandidates() {
    Collections.sort(myImports);
  }

  public @NotNull List<ImportCandidateHolder> getCandidates() {
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
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(getStartElement().getProject());
    for (ImportCandidateHolder anImport : myImports) {
      PsiFileSystemItem importFile = anImport.getFile();
      VirtualFile file = importFile != null ? importFile.getVirtualFile() : null;
      if (file != null && fileIndex.isInContent(file)) {
        return true;
      }
    }
    return false;
  }

  public @NotNull AutoImportQuickFix forLocalImport() {
    return new AutoImportLocallyQuickFix(getStartElement(), myReferenceType, this.myInitialName, myUseQualifiedImport, myImports);
  }

  public @NotNull String getNameToImport() {
    return myInitialName;
  }

  public @NotNull Class<? extends PsiReference> getReferenceType() {
    return myReferenceType;
  }

  public boolean isUseQualifiedImport() {
    return myUseQualifiedImport;
  }

  @ApiStatus.Internal
  public static boolean isResolved(@NotNull PsiReference reference) {
    if (reference instanceof PsiPolyVariantReference) {
      return ((PsiPolyVariantReference)reference).multiResolve(false).length > 0;
    }
    return reference.resolve() != null;
  }

  @ApiStatus.Internal
  @Nullable
  public PsiReference findOriginalReference(@NotNull PsiElement element) {
    return ContainerUtil.findInstance(element.getReferences(), myReferenceType);
  }

  @Override
  public @Nullable AutoImportQuickFix getFileModifierForPreview(@NotNull PsiFile target) {
    PsiElement unresolvedRef = getStartElement();
    if (unresolvedRef == null) return null;
    PsiElement unresolvedRefCopy = PsiTreeUtil.findSameElementInCopy(unresolvedRef, target);
    List<ImportCandidateHolder> candidates = new ArrayList<>();
    for (ImportCandidateHolder candidate : myImports) {
      ImportCandidateHolder importCandidateForPreview = updateExistingImportElementForPreview(candidate, target);
      if (importCandidateForPreview == null) return null;
      candidates.add(importCandidateForPreview);
    }
    return new AutoImportQuickFix(unresolvedRefCopy, myReferenceType, myInitialName, myUseQualifiedImport, candidates);
  }

  private static @Nullable ImportCandidateHolder updateExistingImportElementForPreview(@NotNull ImportCandidateHolder candidate,
                                                                                       @NotNull PsiFile target) {
    PyImportElement importElement = candidate.getImportElement();
    if (importElement == null) return candidate;
    if (candidate.getImportable() == null || candidate.getFile() == null) return null;
    return new ImportCandidateHolder(candidate.getImportable(),
                                     candidate.getFile(),
                                     PsiTreeUtil.findSameElementInCopy(importElement, target),
                                     candidate.getPath(),
                                     candidate.getAsName());
  }

  private static class AutoImportLocallyQuickFix extends AutoImportQuickFix {
    private AutoImportLocallyQuickFix(@NotNull PsiElement element,
                                      @NotNull Class<? extends PsiReference> type,
                                      @NotNull String name,
                                      boolean qualify,
                                      @NotNull List<ImportCandidateHolder> imports) {
      super(element, type, name, qualify, imports);
    }

    @Override
    public @NotNull String getFamilyName() {
      return PyPsiBundle.message("QFIX.NAME.local.auto.import");
    }

    @Override
    public @NotNull String getText() {
      return PyPsiBundle.message("QFIX.local.auto.import.import.locally", super.getText());
    }

    @Override
    protected @NotNull ImportFromExistingAction createAction() {
      return new ImportFromExistingAction(getStartElement(), getCandidates(), getNameToImport(), null, isUseQualifiedImport(), true);
    }
  }
}
