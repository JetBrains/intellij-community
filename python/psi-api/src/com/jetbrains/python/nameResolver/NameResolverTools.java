package com.jetbrains.python.nameResolver;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Kazakevich
 */
public final class NameResolverTools {
  private NameResolverTools() {

  }

  /**
   * Checks if FQ element name is one of provided names
   *
   * @param element       element to check
   * @param namesProvider some enum that has one or more names
   * @return true if element's fqn is one of names, provided by provider
   */
  public static boolean isName(@NotNull final PyElement element, @NotNull final FQNamesProvider namesProvider) {
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
      return ArrayUtil.contains(qualifiedName, namesProvider.getNames());
    }
    return false;
  }
}
