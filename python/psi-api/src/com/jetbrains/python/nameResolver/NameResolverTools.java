package com.jetbrains.python.nameResolver;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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

}
