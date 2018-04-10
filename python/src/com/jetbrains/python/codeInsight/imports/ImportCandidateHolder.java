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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
// visibility is intentionally package-level
public class ImportCandidateHolder implements Comparable<ImportCandidateHolder> {
  @NotNull private final SmartPsiElementPointer<PsiElement> myImportable;
  @Nullable private final SmartPsiElementPointer<PyImportElement> myImportElement;
  @NotNull private final SmartPsiElementPointer<PsiFileSystemItem> myFile;
  @Nullable private final QualifiedName myPath;
  @Nullable private final String myAsName;

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
   *
   */
  public ImportCandidateHolder(@NotNull PsiElement importable, @NotNull PsiFileSystemItem file,
                               @Nullable PyImportElement importElement, @Nullable QualifiedName path, @Nullable String asName) {
    SmartPointerManager pointerManager = SmartPointerManager.getInstance(importable.getProject());
    myFile = pointerManager.createSmartPsiElementPointer(file);
    myImportable = pointerManager.createSmartPsiElementPointer(importable);
    myImportElement = importElement != null ? pointerManager.createSmartPsiElementPointer(importElement) : null;
    myPath = path;
    myAsName = asName;
    assert importElement != null || path != null; // one of these must be present
  }

  public ImportCandidateHolder(@NotNull PsiElement importable, @NotNull PsiFileSystemItem file,
                               @Nullable PyImportElement importElement, @Nullable QualifiedName path) {
    this(importable, file, importElement, path, null);
  }

  @Nullable
  public PsiElement getImportable() {
    return myImportable.getElement();
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
  public String getPresentableText(@NotNull String myName) {
    PyImportElement importElement = getImportElement();
    PsiElement importable = getImportable();
    final StringBuilder sb = new StringBuilder(getQualifiedName(myName, myPath, importElement));
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
    return sb.toString();
  }

  public int compareTo(@NotNull ImportCandidateHolder other) {
    final int lRelevance = getRelevance();
    final int rRelevance = other.getRelevance();
    if (rRelevance != lRelevance) {
      return rRelevance - lRelevance;
    }
    if (myPath != null && other.myPath != null) {
      // prefer shorter paths
      final int lengthDiff = myPath.getComponentCount() - other.myPath.getComponentCount();
      if (lengthDiff != 0) {
        return lengthDiff;
      }
    }
    return Comparing.compare(myPath, other.myPath);
  }

  int getRelevance() {
    if (myImportElement != null) return 4;
    final Project project = myImportable.getProject();
    final PsiFile psiFile = myImportable.getContainingFile();
    final VirtualFile vFile = psiFile == null ? null : psiFile.getVirtualFile();
    if (vFile == null) return 0;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    // files under project source are most relevant
    final Module module = fileIndex.getModuleForFile(vFile);
    if (module != null) return 3;
    // then come files directly under Lib
    if (vFile.getParent().getName().equals("Lib")) return 2;
    // tests we don't want
    if (vFile.getParent().getName().equals("test")) return 0;
    return 1;
  }

  @Nullable
  public String getAsName() {
    return myAsName;
  }
}
