package com.intellij.execution;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class PsiLocation<E extends PsiElement> extends Location<E> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.PsiLocation");
  private final E myPsiElement;
  private final Project myProject;

  public PsiLocation(final Project project, final E psiElement) {
    LOG.assertTrue(psiElement != null);
    LOG.assertTrue(project != null);
    myPsiElement = psiElement;
    myProject = project;
  }

  public E getPsiElement() {
    return myPsiElement;
  }

  public Project getProject() {
    return myProject;
  }

  public <Ancestor extends PsiElement> Iterator<Location<? extends Ancestor>> getAncestors(final Class<Ancestor> ancestorClass, final boolean strict) {
    final Ancestor first;
    if (!strict && ancestorClass.isInstance(myPsiElement)) first = (Ancestor)myPsiElement;
    else first = findNext(myPsiElement, ancestorClass);
    return new Iterator<Location<? extends Ancestor>>() {
      private Ancestor myCurrent = first;
      public boolean hasNext() {
        return myCurrent != null;
      }

      public Location<? extends Ancestor> next() {
        if (myCurrent == null) throw new NoSuchElementException();
        final PsiLocation<Ancestor> psiLocation = new PsiLocation<Ancestor>(myProject, myCurrent);
        myCurrent = findNext(myCurrent, ancestorClass);
        return psiLocation;
      }

      public void remove() {
        LOG.assertTrue(false);
      }
    };
  }

  public PsiLocation<E> toPsiLocation() {
    return this;
  }

  private <ElementClass extends PsiElement> ElementClass findNext(final PsiElement psiElement, final Class<ElementClass> ancestorClass) {
    PsiElement element = psiElement;
    while ((element = element.getParent()) != null) {
      final ElementClass ancestor = safeCast(element, ancestorClass);
      if (ancestor != null) return ancestor;
    }
    return null;
  }

  public static Location<PsiClass> fromClassQualifiedName(final Project project, final String qualifiedName) {
    final PsiClass psiClass = PsiManager.getInstance(project).findClass(qualifiedName.replace('$', '.'), GlobalSearchScope.allScope(project));
    return psiClass != null ? new PsiLocation<PsiClass>(project, psiClass) : null;
  }

  public static <ElementClass extends PsiElement> Location<ElementClass> fromPsiElement(final Project project, final ElementClass element) {
    if (element == null) return null;
    return new PsiLocation<ElementClass>(project, element);
  }

  public static <ElementClass extends PsiElement> Location<ElementClass> fromPsiElement(final ElementClass element) {
    if (element == null) return null;
    if (!element.isValid()) return null;
    return new PsiLocation<ElementClass>(element.getProject(), element);
  }
}
