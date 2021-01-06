// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.completion.PyCompletionUtilsKt;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * An immutable holder of information for one auto-import candidate.
 * <p/>
 * There can be do different flavors of such candidates:
 * <ul>
 *   <li>Candidates based on existing imports in module. In this case {@link #getImportElement()} must return not {@code null}.</li>
 *   <li>Candidates not yet imported. In this case {@link #getPath()} must return not {@code null}.</li>
 * </ul>
 * <p/>
 *
 * @author dcheryasov
 */
public class ImportCandidateHolder implements Comparable<ImportCandidateHolder> {
  private static final Logger LOG = Logger.getInstance(ImportCandidateHolder.class);

  @NotNull private final SmartPsiElementPointer<PsiNamedElement> myImportable;
  @Nullable private final SmartPsiElementPointer<PyImportElement> myImportElement;
  @NotNull private final SmartPsiElementPointer<PsiFileSystemItem> myFile;
  @Nullable private final QualifiedName myPath;
  @NotNull private final String myImportableName;
  @Nullable private final String myAsName;
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

  @Nullable
  public PsiNamedElement getImportable() {
    return myImportable.getElement();
  }

  public @NotNull String getImportableName() {
    return myImportableName;
  }

  @Nullable
  public PyImportElement getImportElement() {
    return myImportElement != null ? myImportElement.getElement() : null;
  }

  @Nullable
  public PsiFileSystemItem getFile() {
    return myFile.getElement();
  }

  @Nullable
  public QualifiedName getPath() {
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
  @NotNull
  public static String getQualifiedName(@NotNull String name, @Nullable QualifiedName importPath, @Nullable PyImportElement source) {
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

  @NotNull
  public @NlsSafe String getPresentableText() {
    PyImportElement importElement = getImportElement();
    PsiElement importable = getImportable();
    final StringBuilder sb = new StringBuilder(getQualifiedName(getImportableName(), myPath, importElement));
    PsiElement parent = null;
    if (importElement != null) {
      parent = importElement.getParent();
    }
    if (importable instanceof PyFunction) {
      sb.append("()");
    }
    else if (importable instanceof PyClass) {
      final List<String> supers = ContainerUtil.mapNotNull(((PyClass)importable).getSuperClasses(null),
                                                           cls -> PyUtil.isObjectClass(cls) ? null : cls.getName());
      if (!supers.isEmpty()) {
        sb.append("(");
        StringUtil.join(supers, ", ", sb);
        sb.append(")");
      }
    }
    if (parent instanceof PyFromImportStatement) {
      sb.append(" from ");
      final PyFromImportStatement fromImportStatement = (PyFromImportStatement)parent;
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

  @Nullable
  public String getAsName() {
    return myAsName;
  }
}
