package com.intellij.execution;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.Iterator;

public abstract class Location<E extends PsiElement> {
  public static final String LOCATION = "Location";

  public abstract E getPsiElement();
  public abstract Project getProject();
  public abstract <Ancestor extends PsiElement> Iterator<Location<? extends Ancestor>> getAncestors(Class<Ancestor> ancestorClass, boolean strict);

  public OpenFileDescriptor getOpenFileDescriptor() {
    final E psiElement = getPsiElement();
    final PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) return null;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;
    return new OpenFileDescriptor(getProject(), virtualFile, psiElement.getTextOffset());
  }

  public <Ancestor extends PsiElement> Location<Ancestor> getParent(final Class<Ancestor> parentClass) {
    final Iterator<Location<? extends PsiElement>> ancestors = getAncestors(PsiElement.class, true);
    if (!ancestors.hasNext()) return null;
    final Location<? extends PsiElement> parent = ancestors.next();
    if (parentClass.isInstance(parent.getPsiElement())) return (Location<Ancestor>)parent;
    return null;
  }

  public <Ancestor extends PsiElement> Location<? extends Ancestor> getAncestorOrSelf(final Class<Ancestor> ancestorClass) {
    final Iterator<Location<? extends Ancestor>> ancestors = getAncestors(ancestorClass, false);
    if (!ancestors.hasNext()) return null;
    return ancestors.next();
  }

  public <Ancestor extends PsiElement> Ancestor getParentElement(final Class<Ancestor> parentClass) {
    return safeGetPsiElement(getParent(parentClass));
  }

  public static <T extends PsiElement> T safeGetPsiElement(final Location<T> location) {
    return location != null ? location.getPsiElement() : null;
  }

  public static <T> T safeCast(final Object obj, final Class<T> expectedClass) {
    if (expectedClass.isInstance(obj)) return (T)obj;
    return null;
  }

  public PsiLocation<E> toPsiLocation() {
    return new PsiLocation<E>(getProject(), getPsiElement());
  }
}
