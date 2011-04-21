package com.jetbrains.python.documentation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.impl.ParamHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      return resolveParameter((PyFunction)owner);
    }
    if (owner instanceof PyClass) {
      final PyFunction init = ((PyClass)owner).findMethodByName(PyNames.INIT, false);
      if (init != null) {
        return resolveParameter(init);
      }
    }
    return null;
  }

  @Nullable
  private PsiElement resolveParameter(PyFunction owner) {
    return owner.getParameterList().getElementNamed(getCanonicalText());
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
