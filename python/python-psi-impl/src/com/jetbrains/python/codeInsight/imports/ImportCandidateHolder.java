// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.completion.PyCompletionUtilsKt;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * An immutable holder of information for one auto-import candidate.
 * <p/>
 * There can be do different flavors of such candidates:
 * <ul>
 *   <li>Candidates based on existing imports in module. In this case {@link #getImportElement()} must return not {@code null}.</li>
 *   <li>Candidates not yet imported. In this case {@link #getPath()} must return not {@code null}.</li>
 * </ul>
 * <p/>
 */
public class ImportCandidateHolder implements Comparable<ImportCandidateHolder> {
  private static final Logger LOG = Logger.getInstance(ImportCandidateHolder.class);

  private final @NotNull SmartPsiElementPointer<PsiNamedElement> myImportable;
  private final @Nullable SmartPsiElementPointer<PyImportElement> myImportElement;
  private final @NotNull SmartPsiElementPointer<PsiFileSystemItem> myFile;
  private final @Nullable QualifiedName myPath;
  private final @NotNull String myImportableName;
  private final @Nullable String myAsName;
  private final int myRelevance;

  /**
   * Creates new instance.
   *
   * @param importable    an element that could be imported either from import element or from file.
   * @param file          the file which is the source of the importable (module for symbols, containing directory for modules and packages)
   * @param importElement an existing import element that can be a source for the importable.
   * @param path          import path for the file, as a qualified name (a.b.c)
   *                      For top-level imported symbols it's <em>qualified name of containing module</em> (or package for __init__.py).
   *                      For modules and packages it should be <em>qualified name of their parental package</em>
   *                      (empty for modules and packages located at source roots).
   */
  public ImportCandidateHolder(@NotNull PsiNamedElement importable, @NotNull PsiFileSystemItem file,
                               @Nullable PyImportElement importElement, @Nullable QualifiedName path, @Nullable String asName) {
    if (importElement == null && path == null) {
      throw new IllegalArgumentException("Either an import path or an existing import should be provided for " + importable);
    }
    SmartPointerManager pointerManager = SmartPointerManager.getInstance(importable.getProject());
    myFile = pointerManager.createSmartPsiElementPointer(file);
    myImportable = pointerManager.createSmartPsiElementPointer(importable);
    myImportableName = PyUtil.getElementNameWithoutExtension(importable);
    assert myImportableName != null;
    myImportElement = importElement != null ? pointerManager.createSmartPsiElementPointer(importElement) : null;
    myPath = path;
    myAsName = asName;
    myRelevance = PyCompletionUtilsKt.computeCompletionWeight(importable, myImportableName, myPath, null, false);
    LOG.debug("Computed relevance for import item ", myImportableName, ": ", myRelevance);
  }

  public ImportCandidateHolder(@NotNull PsiNamedElement importable, @NotNull PsiFileSystemItem file,
                               @Nullable PyImportElement importElement, @Nullable QualifiedName path) {
    this(importable, file, importElement, path, null);
  }

  public @Nullable PsiNamedElement getImportable() {
    return myImportable.getElement();
  }

  public @NotNull String getImportableName() {
    return myImportableName;
  }

  public @Nullable PyImportElement getImportElement() {
    return myImportElement != null ? myImportElement.getElement() : null;
  }

  public @Nullable PsiFileSystemItem getFile() {
    return myFile.getElement();
  }

  public @Nullable QualifiedName getPath() {
    return myPath;
  }

  /**
   * Helper method that builds an import path, handling all these "import foo", "import foo as bar", "from bar import foo", etc.
   * Either importPath or importSource must be not null.
   *
   * @param name       what is ultimately imported.
   * @param importPath known path to import the name.
   * @param source     known ImportElement to import the name; its 'as' clause is used if present.
   * @return a properly qualified name.
   */
  public static @NotNull String getQualifiedName(@NotNull String name, @Nullable QualifiedName importPath, @Nullable PyImportElement source) {
    final StringBuilder sb = new StringBuilder();
    if (source != null) {
      final PsiElement parent = source.getParent();
      if (parent instanceof PyFromImportStatement) {
        sb.append(name);
      }
      else {
        sb.append(source.getVisibleName()).append(".").append(name);
      }
    }
    else {
      if (importPath != null && importPath.getComponentCount() > 0) {
        sb.append(importPath).append(".");
      }
      sb.append(name);
    }
    return sb.toString();
  }

  public @NotNull @NlsSafe String getPresentableText() {
    PyImportElement importElement = getImportElement();
    final StringBuilder sb = new StringBuilder(getQualifiedName(getImportableName(), myPath, importElement));
    PsiElement parent = null;
    if (importElement != null) {
      parent = importElement.getParent();
    }
    if (parent instanceof PyFromImportStatement fromImportStatement) {
      sb.append(" from ");
      sb.append(StringUtil.repeat(".", fromImportStatement.getRelativeLevel()));
      final PyReferenceExpression source = fromImportStatement.getImportSource();
      if (source != null) {
        sb.append(source.asQualifiedName());
      }
    }
    if (myAsName != null) {
      sb.append(" as ").append(myAsName);
    }
    return sb.toString();
  }

  @Override
  public int compareTo(@NotNull ImportCandidateHolder other) {
    if (myImportElement != null && other.myImportElement == null) return -1;
    if (myImportElement == null && other.myImportElement != null) return 1;
    if (myAsName != null && other.myAsName == null) return -1;
    if (myAsName == null && other.myAsName != null) return 1;

    int comparedRelevance = Comparator
      .comparing(ImportCandidateHolder::getRelevance).reversed()
      .compare(this, other);
    if (comparedRelevance != 0) return comparedRelevance;

    QualifiedName myLocalPath = getPath();
    QualifiedName otherPath = other.getPath();
    if (myImportElement != null) {
      // both are not null here
      PyImportElement myElement = myImportElement.getElement();
      PyImportElement otherElement = other.myImportElement.getElement();
      if (myElement != null && otherElement != null) {
        myLocalPath = myElement.getImportedQName();
        otherPath = otherElement.getImportedQName();
      }
    }
    return Comparing.compare(myLocalPath, otherPath);
  }

  public int getRelevance() {
    return myRelevance;
  }

  public @Nullable String getAsName() {
    return myAsName;
  }
}
