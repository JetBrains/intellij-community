// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.nameResolver;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiCacheKey;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Function;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Ilya.Kazakevich
 */
public final class NameResolverTools {

  private static final Logger LOG = Logger.getInstance(NameResolverTools.class);

  /**
   * Cache: pair [qualified element name, class name (may be null)] by any psi element.
   */
  private static final PsiCacheKey<Pair<String, String>, PyElement> QUALIFIED_AND_CLASS_NAME =
    PsiCacheKey.create(NameResolverTools.class.getName(), new QualifiedAndClassNameObtainer());

  private NameResolverTools() {

  }

  /**
   * For each provided element checks if FQ element name is one of provided names
   *
   * @param elements       element to check
   * @param namesProviders some enum that has one or more names
   * @return true if element's fqn is one of names, provided by provider
   */
  public static boolean isElementWithName(final @NotNull Collection<? extends PyElement> elements,
                                          final FQNamesProvider @NotNull ... namesProviders) {
    for (final PyElement element : elements) {
      if (isName(element, namesProviders)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Same as {@link #isName(PyElement, FQNamesProvider...)} for named elements, but only checks name.
   * Aliases not supported, but much lighter that way
   */
  public static boolean isNameShortCut(@NotNull PyElement element, FQNamesProvider @NotNull ... namesProviders) {
    String name = element.getName();
    if (name == null) {
      return false;
    }
    return Arrays.stream(namesProviders).anyMatch(o -> getLastComponents(o).contains(name));
  }

  /**
   * Checks if FQ element name is one of provided names. May be <strong>heavy</strong>.
   * It is always better to use less accurate but lighter {@link #isCalleeShortCut(PyCallExpression, FQNamesProvider)}
   * and {@link #isNameShortCut(PyElement, FQNamesProvider...)}
   *
   * @param element        element to check
   * @param namesProviders some enum that has one or more names
   * @return true if element's fqn is one of names, provided by provider
   */
  public static boolean isName(final @NotNull PyElement element, final FQNamesProvider @NotNull ... namesProviders) {
    assert element.isValid();
    final Pair<String, String> qualifiedAndClassName = RecursionManager.doPreventingRecursion(element, false,
                                                                                              () -> QUALIFIED_AND_CLASS_NAME
                                                                                                .getValue(element));
    if (qualifiedAndClassName == null) {
      LOG.debug("Could not get qualified name for " + element);
      return false;
    }
    final String qualifiedName = qualifiedAndClassName.first;
    final String className = qualifiedAndClassName.second;

    for (final FQNamesProvider provider : namesProviders) {
      final List<String> names = Arrays.asList(provider.getNames());
      if (qualifiedName != null && names.contains(qualifiedName)) {
        return true;
      }
      if (className != null && provider.isClass() && names.contains(className)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Looks for parent call of certain function
   *
   * @param anchor       element to look parent for
   * @param functionName function to find
   * @return parent call or null if not found
   */
  public static @Nullable PyCallExpression findCallExpParent(final @NotNull PsiElement anchor, final @NotNull FQNamesProvider functionName) {
    final PsiElement parent = PsiTreeUtil.findFirstParent(anchor, new MyFunctionCondition(functionName));
    if (parent instanceof PyCallExpression) {
      return (PyCallExpression)parent;
    }
    return null;
  }

  /**
   * Same as {@link #isName(PyElement, FQNamesProvider...)} for call expr, but only checks name.
   * Aliases not supported, but much lighter that way
   *
   * @param call     expr
   * @param function names to check
   * @return true if callee is correct
   */
  public static boolean isCalleeShortCut(final @NotNull PyCallExpression call,
                                         final FQNamesProvider @NotNull ... function) {
    final PyExpression callee = call.getCallee();
    if (callee == null) {
      return false;
    }

    final String callableName = callee.getName();

    return Arrays.stream(function).anyMatch(o -> getLastComponents(o).contains(callableName));
  }

  private static @NotNull List<String> getLastComponents(final @NotNull FQNamesProvider provider) {
    final List<String> result = new ArrayList<>();
    for (final String name : provider.getNames()) {
      final String component = QualifiedName.fromDottedString(name).getLastComponent();
      if (component != null) {
        result.add(component);
      }
    }
    return result;
  }

  /**
   * Checks if some string contains last component one of name
   *
   * @param text  test to check
   */
  public static boolean isContainsName(final @NotNull String text, final @NotNull FQNamesProvider names) {
    for (final String lastComponent : getLastComponents(names)) {
      if (text.contains(lastComponent)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if some file contains last component one of name
   *
   * @param file  file to check
   */
  public static boolean isContainsName(final @NotNull PsiFile file, final @NotNull FQNamesProvider names) {
    return isContainsName(file.getText(), names);
  }

  /**
   * Check if class has parent with some name
   *
   * @param child class to check
   */
  public static boolean isSubclass(final @NotNull PyClass child,
                                   final @NotNull FQNamesProvider parentName,
                                   final @NotNull TypeEvalContext context) {
    for (final String nameToCheck : parentName.getNames()) {
      if (child.isSubclass(nameToCheck, context)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Looks for call of some function
   */
  private static class MyFunctionCondition implements Condition<PsiElement> {
    private final @NotNull FQNamesProvider myNameToSearch;

    MyFunctionCondition(final @NotNull FQNamesProvider name) {
      myNameToSearch = name;
    }

    @Override
    public boolean value(final PsiElement element) {
      if (element instanceof PyCallExpression callExpression) {
        return isCalleeShortCut(callExpression, myNameToSearch);
      }
      return false;
    }
  }

  /**
   * Returns pair [qualified name, class name (may be null)] by psi element
   */
  private static class QualifiedAndClassNameObtainer implements Function<PyElement, Pair<String, String>> {
    @Override
    public @NotNull Pair<String, String> fun(final @NotNull PyElement param) {
      PyElement elementToCheck = param;

      // Trying to use no implicit context if possible...
      final PsiReference reference;
      if (param instanceof PyReferenceOwner) {
        final var context = TypeEvalContext.codeInsightFallback(param.getProject());
        reference = ((PyReferenceOwner)param).getReference(PyResolveContext.defaultContext(context));
      }
      else {
        reference = param.getReference();
      }

      if (reference != null) {
        final PsiElement resolvedElement = reference.resolve();
        if (resolvedElement instanceof PyElement) {
          elementToCheck = (PyElement)resolvedElement;
        }
      }
      String qualifiedName = null;
      if (elementToCheck instanceof PyQualifiedNameOwner) {
        qualifiedName = ((PyQualifiedNameOwner)elementToCheck).getQualifiedName();
      }
      String className = null;
      if (elementToCheck instanceof PyFunction) {
        final PyClass aClass = ((PyFunction)elementToCheck).getContainingClass();
        if (aClass != null) {
          className = aClass.getQualifiedName();
        }
      }
      return Pair.create(qualifiedName, className);
    }
  }
}
