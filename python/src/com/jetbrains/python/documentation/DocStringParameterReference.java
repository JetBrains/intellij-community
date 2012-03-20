package com.jetbrains.python.documentation;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class DocStringParameterReference extends PsiReferenceBase<PsiElement> {
  String myType;
  public DocStringParameterReference(PsiElement element,
                                     TextRange range,
                                     String refType) {
    super(element, range);
    myType = refType;
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
    return owner.getParameterList().findParameterByName(getCanonicalText());
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
    if (owner instanceof PyFunction) {
      List <PyNamedParameter> result = Lists.newArrayList();
      final List<PyNamedParameter> namedParameters = ParamHelper.collectNamedParameters(((PyFunction)owner).getParameterList());
      Set<String> usedParameters = new HashSet<String>();
      PyStringLiteralExpression expression = PsiTreeUtil.getParentOfType(getElement(), PyStringLiteralExpression.class, false);
      if (expression != null) {
        PsiReference[] references = expression.getReferences();
        for (PsiReference ref : references) {
          if (ref instanceof DocStringParameterReference && ((DocStringParameterReference)ref).getType().equals(myType))
            usedParameters.add(ref.getCanonicalText());
        }
      }
      for (PyNamedParameter param : namedParameters) {
        if (!usedParameters.contains(param.getName()))
          result.add(param);
      }

      return ArrayUtil.toObjectArray(result);
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
  
  public String getType() {
    return myType;
  }
}
