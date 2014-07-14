package com.jetbrains.python.nameResolver;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Ilya.Kazakevich
 */
public final class NameResolverTools {
  private NameResolverTools() {

  }

  /**
   * For each provided element checks if FQ element name is one of provided names
   *
   * @param elements       element to check
   * @param namesProviders some enum that has one or more names
   * @return true if element's fqn is one of names, provided by provider
   */
  public static boolean isElementWithName(@NotNull final Collection<? extends PyElement> elements,
                                          @NotNull final FQNamesProvider... namesProviders) {
    for (final PyElement element : elements) {
      if (isName(element, namesProviders)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if FQ element name is one of provided names
   *
   * @param element        element to check
   * @param namesProviders some enum that has one or more names
   * @return true if element's fqn is one of names, provided by provider
   */
  public static boolean isName(@NotNull final PyElement element, @NotNull final FQNamesProvider... namesProviders) {
    PyElement elementToCheck = element;
    final PsiReference reference = element.getReference();
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
   * @param anchor element to look parent for
   * @param functionName function to find
   * @return parent call or null if not found
   */
  @Nullable
  public static PyCallExpression findCallExpParent(@NotNull final PsiElement anchor, @NotNull final FQNamesProvider functionName) {
    final PsiElement parent = PsiTreeUtil.findFirstParent(anchor, new MyFunctionCondition(functionName));
    if (parent instanceof PyCallExpression) {
      return (PyCallExpression)parent;
    }
    return null;
  }

  /**
   * Looks for call of some function
   */
  private static class MyFunctionCondition implements Condition<PsiElement> {
    @NotNull
    private final FQNamesProvider myNameToSearch;

    MyFunctionCondition(@NotNull final FQNamesProvider name) {
      myNameToSearch = name;
    }

    @Override
    public boolean value(final PsiElement element) {
      if (element instanceof PyCallExpression) {
        return ((PyCallExpression)element).isCallee(myNameToSearch);
      }
      return false;
    }
  }
}
