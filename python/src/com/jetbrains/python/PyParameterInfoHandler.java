package com.jetbrains.python;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.parameterInfo.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyParameterInfoHandler implements ParameterInfoHandler<PyArgumentList, PyFunction> {
  public boolean couldShowInLookup() {
    return true;
  }

  public Object[] getParametersForLookup(final LookupElement item, final ParameterInfoContext context) {
    return new Object[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  public Object[] getParametersForDocumentation(final PyFunction p, final ParameterInfoContext context) {
    return new Object[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  public PyArgumentList findElementForParameterInfo(final CreateParameterInfoContext context) {
    PyArgumentList result = findArgumentList(context);
    if (result != null) {
      PyCallExpression callExpression = PsiTreeUtil.getParentOfType(result, PyCallExpression.class);
      if (callExpression == null) return null;
      PyElement callee = callExpression.resolveCallee();
      if (!(callee instanceof PyFunction)) return null;
      PyFunction function = (PyFunction) callee;
      context.setItemsToShow(new Object[] { function });
    }
    return result;
  }

  private static PyArgumentList findArgumentList(final ParameterInfoContext context) {
    return ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PyArgumentList.class);
  }

  public void showParameterInfo(@NotNull final PyArgumentList element, final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextOffset(), this);
  }

  public PyArgumentList findElementForUpdatingParameterInfo(final UpdateParameterInfoContext context) {
    return findArgumentList(context);
  }

  public void updateParameterInfo(@NotNull final PyArgumentList o, final UpdateParameterInfoContext context) {
    if (context.getParameterOwner() != o) {
      context.removeHint();
      return;
    }
    final int currentParameterIndex = ParameterInfoUtils.getCurrentParameterIndex(o.getNode(), context.getOffset(), PyTokenTypes.COMMA);
    context.setCurrentParameter(currentParameterIndex);
  }

  public String getParameterCloseChars() {
    return ",)";
  }

  public boolean tracksParameterIndex() {
    return true;
  }

  public void updateUI(final PyFunction p, final ParameterInfoUIContext context) {
    final PyParameterList parameterList = p.getParameterList();
    final PyParameter[] params = parameterList.getParameters();
    final int currentParameterIndex = context.getCurrentParameterIndex() >= 0 ? context.getCurrentParameterIndex():params.length;

    int highlightStartOffset = -1;
    int highlightEndOffset = -1;

    StringBuilder signatureBuilder = new StringBuilder();
    for(int i=0; i<params.length; i++) {
      if (i == currentParameterIndex) {
        highlightStartOffset = signatureBuilder.length();
      }
      if (params [i].isPositionalContainer()) {
        signatureBuilder.append("*");
      }
      else if (params [i].isKeywordContainer()) {
        signatureBuilder.append("**");
      }
      signatureBuilder.append(params [i].getName());
      if (i == currentParameterIndex) {
        highlightEndOffset = signatureBuilder.length();
      }
      if (i < params.length-1) {
        signatureBuilder.append(", ");
      }
    }

    context.setupUIComponentPresentation(signatureBuilder.toString(), highlightStartOffset, highlightEndOffset, false, false, false,
                                         context.getDefaultParameterColor());
  }
}
