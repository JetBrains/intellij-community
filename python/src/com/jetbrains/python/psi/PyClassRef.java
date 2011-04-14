package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyClassRef {
  @Nullable
  private final PsiElement myElement;

  @Nullable
  private final String myQName;

  public PyClassRef(PsiElement element) {
    myElement = element;
    myQName = null;
  }

  public PyClassRef(String qName) {
    myElement = null;
    myQName = qName;
  }

  @Nullable
  public PyClass getPyClass() {
    return myElement instanceof PyClass ? (PyClass) myElement : null;
  }

  @Nullable
  public PsiElement getElement() {
    return myElement;
  }

  @Nullable
  public String getClassName() {
    return myElement instanceof PyClass ? ((PyClass) myElement).getName() : null;
  }

  @Nullable
  public String getQualifiedName() {
    if (myQName != null) {
      return myQName;
    }
    return myElement instanceof PyClass ? ((PyClass)myElement).getQualifiedName() : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyClassRef that = (PyClassRef)o;

    if (myElement != null ? !myElement.equals(that.myElement) : that.myElement != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myElement != null ? myElement.hashCode() : 0;
  }
}
