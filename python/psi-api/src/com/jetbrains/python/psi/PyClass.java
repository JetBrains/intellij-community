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
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayFactory;
import com.intellij.util.Processor;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a class declaration in source.
 */
public interface PyClass extends PsiNameIdentifierOwner, PyStatement, NameDefiner, PyDocStringOwner, StubBasedPsiElement<PyClassStub>,
                                 ScopeOwner, PyDecoratable, PyTypedElement, PyQualifiedNameOwner {
  ArrayFactory<PyClass> ARRAY_FACTORY = new ArrayFactory<PyClass>() {
    @NotNull
    @Override
    public PyClass[] create(int count) {
      return new PyClass[count];
    }
  };

  @Nullable
  ASTNode getNameNode();

  @NotNull
  PyStatementList getStatementList();

  /**
   * Returns types of all ancestors from the hierarchy.
   */
  @NotNull
  List<PyClassLikeType> getAncestorTypes(@NotNull TypeEvalContext context);

  /**
   * Returns only those ancestors from the hierarchy, that are resolved to PyClass PSI elements.
   *
   * @see #getAncestorTypes(TypeEvalContext) for the full list of ancestors.
   */
  @NotNull
  List<PyClass> getAncestorClasses(@NotNull TypeEvalContext context);

  /**
   * Returns only those ancestors from the hierarchy, that are resolved to PyClass PSI elements, using the default type evaluation context.
   *
   * @see #getAncestorClasses(TypeEvalContext) if a more detailed TypeEvalContext is available.
   */
  @NotNull
  List<PyClass> getAncestorClasses();

  /**
   * Returns types of expressions in the super classes list.
   *
   * If no super classes are specified, returns the type of the implicit super class for old- and new-style classes.
   *
   * @see #getAncestorTypes(TypeEvalContext) for the full list of ancestors.
   */
  @NotNull
  List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context);

  /**
   * Returns only those super classes for expressions from the super classes list, that are resolved to PyClass PSI elements.
   *
   * If no super classes are specified, returns the implicit super class for old- and new-style classes.
   *
   * @see #getSuperClassTypes(TypeEvalContext) for the full list of super classes.
   * @see #getAncestorTypes(TypeEvalContext) for the full list of ancestors.
   */
  @NotNull
  PyClass[] getSuperClasses();

  /**
   * Returns a PSI element for the super classes list.
   *
   * Operates at the AST level.
   */
  @Nullable
  PyArgumentList getSuperClassExpressionList();

  /**
   * Returns PSI elements for the expressions in the super classes list.
   *
   * Operates at the AST level.
   */
  @NotNull
  PyExpression[] getSuperClassExpressions();

  @NotNull
  PyFunction[] getMethods();

  /**
   * Finds a method with given name.
   * @param name what to look for
   * @param inherited true: search in superclasses; false: only look for methods defined in this class.
   * @return
   */
  @Nullable
  PyFunction findMethodByName(@Nullable @NonNls final String name, boolean inherited);

  /**
   * Finds either __init__ or __new__, whichever is defined for given class.
   * If __init__ is defined, it is found first. This mimics the way initialization methods
   * are searched for and called by Python when a constructor call is made.
   * Since __new__ only makes sense for new-style classes, an old-style class never finds it with this method.
   * @param inherited true: search in superclasses, too.
   * @return a method that would be called first when an instance of this class is instantiated.
   */
  @Nullable
  PyFunction findInitOrNew(boolean inherited);

  /**
   * Finds a property with the specified name in the class or one of its ancestors.
   *
   * @param name of the property
   * @return descriptor of property accessors, or null if such property does not exist.
   */
  @Nullable
  Property findProperty(@NotNull String name);

  /**
   * Apply a processor to every method, looking at superclasses in method resolution order as needed.
   * @param processor what to apply
   * @param inherited true: search in superclasses, too.
   */
  boolean visitMethods(Processor<PyFunction> processor, boolean inherited);

  boolean visitClassAttributes(Processor<PyTargetExpression> processor, boolean inherited);

  List<PyTargetExpression> getClassAttributes();

  PyTargetExpression findClassAttribute(@NotNull String name, boolean inherited);

  List<PyTargetExpression> getInstanceAttributes();

  @Nullable
  PyTargetExpression findInstanceAttribute(String name, boolean inherited);

  PyClass[] getNestedClasses();

  @Nullable
  PyClass findNestedClass(String name, boolean inherited);

  /**
   * @return true if the class is new-style and descends from 'object'.
   */
  boolean isNewStyleClass();

  /**
   * Scan properties in order of definition, until processor returns true for one of them.
   * @param processor to check properties
   * @param inherited whether inherited properties need to be scanned, too
   * @return a property that processor accepted, or null.
   */
  @Nullable
  Property scanProperties(Processor<Property> processor, boolean inherited);

  /**
   * Non-recursively searches for a property for which the given function is a getter, setter or deleter.
   *
   * @param callable the function which may be an accessor
   * @return the property, or null
   */
  @Nullable
  Property findPropertyByCallable(Callable callable);

  /**
   * @param parent
   * @return True iff this and parent are the same or parent is one of our superclasses.
   */
  boolean isSubclass(PyClass parent);

  boolean isSubclass(@NotNull String superClassQName);

  /**
   * Returns the aggregated list of names defined in __slots__ attributes of the class and its ancestors.
   */
  @Nullable
  List<String> getSlots();

  /**
   * Returns the list of names in the class' __slots__ attribute, or null if the class
   * does not define such an attribute.
   *
   * @return the list of names or null.
   */
  @Nullable
  List<String> getOwnSlots();

  @Nullable
  String getDocStringValue();

  boolean processClassLevelDeclarations(@NotNull PsiScopeProcessor processor);
  boolean processInstanceLevelDeclarations(@NotNull PsiScopeProcessor processor, @Nullable PsiElement location);
}
