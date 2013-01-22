package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.types.PyClassType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyClassRef {
  @Nullable private final PsiElement myElement;
  @Nullable private final String myQName;
  @Nullable private final PyClassType myType;

  public PyClassRef(@Nullable PsiElement element) {
    myElement = element;
    myQName = null;
    myType = null;
  }

  public PyClassRef(@Nullable String qName) {
    myElement = null;
    myQName = qName;
    myType = null;
  }

  public PyClassRef(@Nullable PyClassType type) {
    myElement = null;
    myQName = null;
    myType = type;
  }

  @Nullable
  public PyClass getPyClass() {
    if (myElement instanceof PyClass) {
      return (PyClass) myElement;
    }
    return null;
  }

  @Nullable
  public PsiElement getElement() {
    return myElement;
  }

  @Nullable
  public PyClassType getType() {
    return myType;
  }

  @Nullable
  public String getClassName() {
    if (myElement instanceof PyClass) {
      return ((PyClass)myElement).getName();
    }
    else if (myQName != null) {
      final PyQualifiedName qname = PyQualifiedName.fromDottedString(myQName);
      if (qname != null) {
        return qname.getLastComponent();
      }
    }
    else if (myType != null) {
      return myType.getName();
    }
    return null;
  }

  @Nullable
  public String getQualifiedName() {
    if (myElement instanceof PyClass) {
      return ((PyClass)myElement).getQualifiedName();
    }
    else if (myQName != null) {
      return myQName;
    }
    else if (myType != null) {
      return myType.getName();
    }
    return null;
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
