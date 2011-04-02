package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;

public class PyElementType extends IElementType {
  protected Class<? extends PsiElement> _psiElementClass;
  private static final Class[] PARAMETER_TYPES = new Class[]{ASTNode.class};
  private Constructor<? extends PsiElement> myConstructor;

  private String mySpecialMethodName;

  public PyElementType(@NotNull @NonNls String debugName) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
  }


  public PyElementType(@NonNls String debugName, Class<? extends PsiElement> psiElementClass) {
    this(debugName);
    _psiElementClass = psiElementClass;
  }

  public PyElementType(@NotNull @NonNls String debugName, @NotNull @NonNls String specialMethodName) {
    this(debugName);
    mySpecialMethodName = specialMethodName;
  }

  @Nullable
  public PsiElement createElement(ASTNode node) {
    if (_psiElementClass == null) {
      return null;
    }

    try {
      if (myConstructor == null) {
        myConstructor = _psiElementClass.getConstructor(PARAMETER_TYPES);
      }

      return myConstructor.newInstance(node);
    }
    catch (Exception e) {
      throw new IllegalStateException("No necessary constructor for " + node.getElementType(), e);
    }
  }

  /**
   * @return name of special method for operation marked by this token; e.g. "__add__" for "+".
   */
  public String getSpecialMethodName() {
    return mySpecialMethodName;
  }

  @Override
  public String toString() {
    return "Py:" + super.toString();
  }
}
