package com.jetbrains.python;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.parameterInfo.*;
import com.jetbrains.python.psi.*;
import static com.jetbrains.python.psi.PyCallExpression.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyParameterInfoHandler implements ParameterInfoHandler<PyArgumentList, PyCallExpression.PyMarkedFunction> {
  
  public boolean couldShowInLookup() {
    return true;
  }
  public Object[] getParametersForLookup(final LookupElement item, final ParameterInfoContext context) {
    return new Object[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  public Object[] getParametersForDocumentation(final PyCallExpression.PyMarkedFunction p, final ParameterInfoContext context) {
    return new Object[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  public PyArgumentList findElementForParameterInfo(final CreateParameterInfoContext context) {
    PyArgumentList result = findArgumentList(context);
    if (result != null) {
      PyCallExpression callExpression = result.getCallExpression(); /*PsiTreeUtil.getParentOfType(result, PyCallExpression.class);*/
      if (callExpression == null) return null;
      /*
      boolean is_inst = callExpression.isByInstance();
      PyElement callee = callExpression.resolveCallee();
      if (callee instanceof PyClass) { // constructor call
        final PyClass cls = (PyClass)callee;
        callee = cls.findMethodByName("__init__");
        is_inst |= true;
      }
      if (!(callee instanceof PyFunction)) return null;
      PyFunction function = (PyFunction) callee;

      context.setItemsToShow(new Object[] { new PyCallExpression.PyMarkedFunction(function, is_inst) });
      */
      context.setItemsToShow(new Object[] { callExpression.resolveCallee() });
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

  public void updateUI(final PyMarkedFunction p, final ParameterInfoUIContext context) {
    if (p == null) return;
    final PyFunction py_function = p.getFunction();
    if (py_function == null) return; // resolution failed
    final PyParameterList parameterList = py_function.getParameterList();
    final PyParameter[] params = parameterList.getParameters();
    int currentParameterIndex = context.getCurrentParameterIndex() >= 0 ? context.getCurrentParameterIndex():params.length;

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
      else if (i == 0 && p.getFlags().contains(Flag.IMPLICIT_FIRST_ARG)) {
        currentParameterIndex += 1;
        continue;
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
