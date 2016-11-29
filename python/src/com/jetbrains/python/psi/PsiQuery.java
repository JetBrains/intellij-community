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
package com.jetbrains.python.psi;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.nameResolver.FQNamesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

// TODO: Propogate typization to all class like in PsiTypedQuery

/**
 * JQuery-like tool that makes PSI navigation easier.
 * You just "drive" your query filtering results, and no need to check result for null.
 *
 * @author Ilya.Kazakevich
 */
public class PsiQuery {
  private static final PsiQuery EMPTY = new PsiQuery();
  @NotNull
  private final PsiElement[] myPsiElements;

  /**
   * @param psiElement one or more elements to start
   */
  public PsiQuery(@NotNull final PsiElement... psiElement) {
    myPsiElements = psiElement.clone();
  }


  /**
   * @param psiElements one or more elements to start
   */
  public PsiQuery(@NotNull final List<? extends PsiElement> psiElements) {
    this(psiElements.toArray(new PsiElement[psiElements.size()]));
  }

  /**
   * Filter children by name
   */
  @NotNull
  public PsiQuery childrenNamed(@NotNull final String name) {
    return childrenNamed(PsiNamedElement.class, name);
  }

  /**
   * Filter children by name and class
   */
  @NotNull
  public PsiQuery childrenNamed(@NotNull final Class<? extends PsiNamedElement> clazz, @NotNull final String name) {
    final List<PsiElement> result = new ArrayList<>();
    for (final PsiElement element : myPsiElements) {
      for (final PsiNamedElement child : PsiTreeUtil.findChildrenOfType(element, clazz)) {
        if (name.equals(child.getName())) {
          result.add(child);
        }
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * Searches for string literals with specific text
   * @param clazz        string literal class
   * @param expectedText expected text
   * @return query {@link com.jetbrains.python.psi.PsiQuery}
   */
  @NotNull
  public final PsiQuery childrenStringLiterals(@NotNull final Class<? extends PyStringLiteralExpression> clazz,
                                               @NotNull final String expectedText) {
    final List<PsiElement> result = new ArrayList<>();
    for (final PyStringLiteralExpression element : getChildrenElements(clazz)) {
      if (element.getStringValue().equals(expectedText)) {
        result.add(element);
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * TODO: Support types?
   * Filter children by function call
   *
   * @return {@link com.jetbrains.python.psi.PsiQuery} backed by {@link com.jetbrains.python.psi.PyCallExpression}
   */
  @NotNull
  public PsiQuery childrenCall(@NotNull final FQNamesProvider name) {
    final List<PsiElement> result = new ArrayList<>();
    for (final PsiElement element : myPsiElements) {
      for (final PyCallExpression call : PsiTreeUtil.findChildrenOfType(element, PyCallExpression.class)) {
        if (call.isCallee(name)) {
          result.add(call);
        }
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }

  /**
   * Filter children by class
   */
  @NotNull
  public PsiQuery children(@NotNull final Class<? extends PsiElement> clazz) {
    final List<PsiElement> result = new ArrayList<>();
    for (final PsiElement element : myPsiElements) {
      result.addAll(PsiTreeUtil.findChildrenOfType(element, clazz));
    }

    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * Filter parents by name
   */
  @NotNull
  public PsiQuery parents(@NotNull final String name) {
    throw new RuntimeException("Not implemented");
  }


  /**
   * Filter parents by name and class
   */
  @NotNull
  public PsiQuery parents(@NotNull final Class<? extends PsiElement> clazz) {
    final List<PsiElement> result = new ArrayList<>();
    for (final PsiElement element : myPsiElements) {
      final PsiElement parent = PsiTreeUtil.getParentOfType(element, clazz);
      if (parent != null) {
        result.add(parent);
      }
    }
    return new PsiQuery(result);
  }

  /**
   * Get qualifiers of all elements if elements do have any
   */
  @NotNull
  public PsiQuery qualifiers() {
    return new PsiQuery(Arrays.stream(myPsiElements)
                          .filter(o -> o instanceof PyQualifiedExpression)
                          .map(o -> ((PyQualifiedExpression)o).getQualifier())
                          .filter(o -> o != null)
                          .collect(Collectors.toList())
    );
  }


  /**
   * Filter parents by condition
   */
  @NotNull
  public PsiQuery parents(@NotNull final Condition<Class<? extends PsiElement>> condition) {
    throw new RuntimeException("Not impl");
  }


  /**
   * Filter parents by class and name
   */
  @NotNull
  public PsiQuery parents(@NotNull final Class<? extends PsiElement> clazz, @NotNull final String name) {
    throw new RuntimeException("Not impl");
  }


  /**
   * Filter parents by function call
   */
  @NotNull
  public PsiQuery parents(@NotNull final FQNamesProvider name) {
    throw new RuntimeException("Not impl");
  }


  /**
   * Filter siblings by name
   */
  @NotNull
  public PsiQuery siblings(@NotNull final String name) {
    return siblings(PsiNamedElement.class, name);
  }


  /**
   * Filter siblings by class returning typed result
   */
  @NotNull
  public <T extends PsiElement> PsiTypedQuery<T> siblings(@NotNull final Class<T> clazz) {
    // TODO: Rewrite function, get rid of inner class
    final List<T> result = new ArrayList<>();
    for (final PsiElement element : myPsiElements) {
      final PsiElement parent = element.getParent();
      for (final T sibling : PsiTreeUtil.findChildrenOfType(parent, clazz)) {
        if ((!sibling.equals(element))) {
          result.add(sibling);
        }
      }
    }
    return new PsiTypedQuery<>(clazz, result);
  }


  /**
   * Filter siblings by name and class
   */
  @NotNull
  public PsiQuery siblings(@NotNull final Class<? extends PsiNamedElement> clazz, @NotNull final String name) {
    final List<PsiElement> result = new ArrayList<>();
    for (final PsiElement element : myPsiElements) {
      final PsiElement parent = element.getParent();
      for (final PsiNamedElement namedSibling : PsiTreeUtil.findChildrenOfType(parent, clazz)) {
        if ((!namedSibling.equals(element)) && (name.equals(namedSibling.getName()))) {
          result.add(namedSibling);
        }
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * Filter siblings by function call name
   */
  @NotNull
  public PsiQuery siblings(@NotNull final FQNamesProvider name) {
    final List<PsiElement> result = new ArrayList<>();
    for (final PsiElement element : myPsiElements) {
      final PsiElement parent = element.getParent();
      for (final PyCallExpression callSibling : PsiTreeUtil.findChildrenOfType(parent, PyCallExpression.class)) {
        if ((!callSibling.equals(element)) && (callSibling.isCallee(name))) {
          result.add(callSibling);
        }
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * Get first element from result only
   */
  @NotNull
  public PsiQuery first() {
    return (myPsiElements.length > 0) ? new PsiQuery(myPsiElements[0]) : EMPTY;
  }


  /**
   * Get last element from result only
   */
  @NotNull
  public PsiQuery last() {
    return (myPsiElements.length > 0) ? new PsiQuery(myPsiElements[myPsiElements.length - 1]) : EMPTY;
  }


  /**
   * Get first element from result only if certain class
   */
  @Nullable
  public <T extends PsiElement> T getFirstElement(@NotNull final Class<T> expectedClass) {
    final List<T> elements = getChildrenElements(expectedClass);
    if (!elements.isEmpty()) {
      return elements.get(0);
    }
    return null;
  }


  /**
   * Get last element from result only if certain class
   */
  @Nullable
  public <T extends PsiElement> T getLastElement(@NotNull final Class<T> expectedClass) {
    final List<T> elements = getChildrenElements(expectedClass);
    if (!elements.isEmpty()) {
      return elements.get(elements.size() - 1);
    }
    return null;
  }


  /**
   * Get children elements filtered by class
   */
  @NotNull
  public <T extends PsiElement> List<T> getChildrenElements(@NotNull final Class<T> expectedClass) {
    final List<T> result = new ArrayList<>();
    for (final PsiElement element : myPsiElements) {
      final T typedElement = PyUtil.as(element, expectedClass);
      if (typedElement != null) {
        result.add(typedElement);
      }
      else {
        final T[] children = PsiTreeUtil.getChildrenOfType(element, expectedClass);
        if (children != null) {
          Collections.addAll(result, children);
        }
      }
    }
    return result;
  }


  /**
   * Filter by function call
   */
  @NotNull
  public PsiQuery filter(@NotNull final FQNamesProvider name) {
    final Set<PsiElement> result = new HashSet<>(Arrays.asList(myPsiElements));
    for (final PsiElement element : myPsiElements) {
      final PyCallExpression callExpression = PyUtil.as(element, PyCallExpression.class);
      if ((callExpression == null) || (!callExpression.isCallee(name))) {
        result.remove(element);
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * Filter by element name
   */
  @NotNull
  public PsiQuery filter(@NotNull final String name) {
    return filter(PsiNamedElement.class, name);
  }


  /**
   * Filter elements by class
   */
  @NotNull
  public <T extends PsiElement> PsiTypedQuery<T> filter(@NotNull final Class<T> clazz) {
    final Set<PsiElement> result = new HashSet<>(Arrays.asList(myPsiElements));
    for (final PsiElement element : myPsiElements) {
      if (!(clazz.isInstance(element))) {
        result.remove(element);
      }
    }
    // We checked it in runtime
    @SuppressWarnings("unchecked")
    final List<T> toAdd = (List<T>)new ArrayList<>(result);
    return new PsiTypedQuery<>(clazz, toAdd);
  }


  /**
   * Filter elements by class and name
   */
  @NotNull
  public PsiQuery filter(@NotNull final Class<? extends PsiNamedElement> clazz, @NotNull final String name) {
    final Set<PsiElement> result = new HashSet<>(Arrays.asList(myPsiElements));
    for (final PsiElement element : myPsiElements) {
      final PsiNamedElement namedElement = PyUtil.as(element, clazz);
      if ((namedElement == null) || (!name.equals(namedElement.getName()))) {
        result.remove(element);
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }

  /**
   * @return is result empty or not
   */
  public boolean isEmpty() {
    return myPsiElements.length == 0;
  }

  /**
   * Typed class that returns elements of certian type
   *
   * @param <T> class type
   */
  public static class PsiTypedQuery<T extends PsiElement> extends PsiQuery {
    @NotNull
    private final Class<T> myClass;
    @NotNull
    private final List<T> myElements;

    /**
     * @param clazz    type
     * @param elements elements
     */
    private PsiTypedQuery(@NotNull final Class<T> clazz, @NotNull final List<T> elements) {
      super(elements);
      myClass = clazz;
      myElements = elements;
    }

    /**
     * @return First element of certain type
     */
    @Nullable
    public T getFirstElement() {
      return getFirstElement(myClass);
    }

    /**
     * @return Last element of certain type
     */
    @Nullable
    public T getLastElement() {
      return getLastElement(myClass);
    }

    /**
     * @return All elements of certain type
     */
    @NotNull
    public List<T> getElements() {
      return Collections.unmodifiableList(myElements);
    }
  }
}
