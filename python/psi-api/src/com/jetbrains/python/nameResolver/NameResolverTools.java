package com.jetbrains.python.nameResolver;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Ilya.Kazakevich
 */
public final class NameResolverTools {
  private NameResolverTools() {

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
    if (elementToCheck instanceof PyQualifiedNameOwner) {
      final String qualifiedName = ((PyQualifiedNameOwner)elementToCheck).getQualifiedName();
      return getNames(namesProviders).contains(qualifiedName);
    }
    return false;
  }

  /**
   * Returns set of names all providers provide
   * @param providers providers to check
   * @return set of names
   */
  @NotNull
  private static Collection<String> getNames(@NotNull final FQNamesProvider... providers) {
    final Set<String> result = new HashSet<String>();
    for (final FQNamesProvider provider : providers) {
      result.addAll(Arrays.asList(provider.getNames()));
    }
    return result;
  }
}
