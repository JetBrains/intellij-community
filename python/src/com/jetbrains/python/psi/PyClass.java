package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyClassStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a class declaration in source.
 */
public interface PyClass extends PsiNamedElement, PyStatement, NameDefiner, PyDocStringOwner, StubBasedPsiElement<PyClassStub>, ScopeOwner {
  @NotNull
  PyStatementList getStatementList();

  @NotNull
  PyExpression[] getSuperClassExpressions();

  @NotNull
  PsiElement[] getSuperClassElements();       

  @NotNull
  PyClass[] getSuperClasses();

  @NotNull
  PyFunction[] getMethods();

  /**
   * Finds a method with given name.
   * @param name what to look for
   * @param inherited true: search in superclasses; false: only look for methods defined in this class.
   * @return
   */
  @Nullable
  PyFunction findMethodByName(@NotNull @NonNls final String name, boolean inherited);

  PyTargetExpression[] getClassAttributes();

  PyTargetExpression[] getInstanceAttributes();

  /**
   * @return true if the class is new-style and descends from 'object'.
   */
  boolean isNewStyleClass();

  /**
   * A lazy way to list ancestor classes width first, in method-resolution order (MRO).
   * @return an iterable of ancestor classes.
   */
  Iterable<PyClass> iterateAncestors();

  /**
   * @param parent
   * @return True iff this and parent are the same or parent is one of our superclasses.
   */
  boolean isSubclass(PyClass parent);

  @Nullable
  PyDecoratorList getDecoratorList();

  String getQualifiedName();
}
