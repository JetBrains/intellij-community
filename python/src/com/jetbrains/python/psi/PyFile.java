package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PyFile extends PyElement, PsiFile, PyDocStringOwner, ScopeOwner, NameDefiner {
  List<PyStatement> getStatements();

  List<PyClass> getTopLevelClasses();

  List<PyFunction> getTopLevelFunctions();

  List<PyTargetExpression> getTopLevelAttributes();

  /**
   * Looks for a name exported by this file, preferably in an efficient way.
   * TODO[yole] this behaves differently in stub-based and AST-based mode: in stub-based mode, it returns the import element for
   * an imported name, in AST-based - the actual element referenced by the import
   *
   * @param name what to find
   * @return found element, or null.
   */
  @Nullable
  PsiElement findExportedName(String name);

  /**
  @return an URL of file, maybe bogus if virtual file is not present.
  */
  @NotNull
  String getUrl();

  @Nullable
  PyFunction findTopLevelFunction(String name);

  @Nullable
  PyClass findTopLevelClass(String name);

  @Nullable
  PyTargetExpression findTopLevelAttribute(String name);

  LanguageLevel getLanguageLevel();

  List<PyFromImportStatement> getFromImports();

  List<PyImportElement> getImportTargets();

  /**
   * Returns the list of names in the __all__ declaration, or null if there is no such declaration in the module.
   *
   * @return the list of names or null.
   */
  @Nullable
  List<String> getDunderAll();

  /**
   * Return true if the file contains a 'from __future__ import ...' statement with given feature.
   */
  boolean hasImportFromFuture(FutureFeature feature);

  /**
   * If the function raises a DeprecationWarning or a PendingDeprecationWarning, returns the explanation text provided for the warning..
   *
   * @return the deprecation message or null if the function is not deprecated.
   */
  String getDeprecationMessage();
}
