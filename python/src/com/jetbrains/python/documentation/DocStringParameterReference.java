package com.jetbrains.python.documentation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.impl.ParamHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class DocStringParameterReference extends PsiReferenceBase<PsiElement> {
  public DocStringParameterReference(PsiElement element, TextRange range) {
    super(element, range);
  }

  @Override
  public PsiElement resolve() {
    PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
    if (owner instanceof PyFunction) {
      final String paramName = getCanonicalText();
      return ((PyFunction) owner).getParameterList().getElementNamed(paramName);
    }
    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
    if (owner instanceof PyFunction) {
      final List<PyNamedParameter> namedParameters = ParamHelper.collectNamedParameters(((PyFunction)owner).getParameterList());
      return ArrayUtil.toObjectArray(namedParameters);
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
