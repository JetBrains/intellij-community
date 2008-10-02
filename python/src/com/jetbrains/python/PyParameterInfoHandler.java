package com.jetbrains.python;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.parameterInfo.*;
import com.jetbrains.python.psi.*;
import static com.jetbrains.python.psi.PyCallExpression.Flag;
import static com.jetbrains.python.psi.PyCallExpression.PyMarkedFunction;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class PyParameterInfoHandler implements ParameterInfoHandler<PyArgumentList, PyArgumentList.AnalysisResult> {
  
  public boolean couldShowInLookup() {
    return true;
  }
  public Object[] getParametersForLookup(final LookupElement item, final ParameterInfoContext context) {
    return new Object[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  public Object[] getParametersForDocumentation(final PyArgumentList.AnalysisResult p, final ParameterInfoContext context) {
    return new Object[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  public PyArgumentList findElementForParameterInfo(final CreateParameterInfoContext context) {
    PyArgumentList arglist = findArgumentList(context);
    if (arglist != null) {
      PyArgumentList.AnalysisResult result = arglist.analyzeCall();  
      context.setItemsToShow(new Object[] { result });
    }
    return arglist;
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

  public void updateUI(final PyArgumentList.AnalysisResult result, final ParameterInfoUIContext context) {
    if (result == null) return;
    PyMarkedFunction marked = result.getMarkedFunction();
    if (marked == null) return;
    final PyFunction py_function = marked.getFunction();
    if (py_function == null) return; // resolution failed
    final PyParameter[] params = py_function.getParameterList().getParameters();
    final PyArgumentList arglist = result.getArgumentList();
    int arg_index = context.getCurrentParameterIndex() >= 0 ? context.getCurrentParameterIndex():params.length;

    // param texts
    String[] param_texts = new String[params.length];
    for (int i = 0; i < param_texts.length; i += 1) {
      StringBuilder strb = new StringBuilder();
      final PyParameter param = params[i];
      if (param.isKeywordContainer()) strb.append("**");
      else if (param.isPositionalContainer()) strb.append("*");
      strb.append(param.getName());
      PyExpression default_v = param.getDefaultValue(); 
      if (default_v != null) strb.append("=").append(PyUtil.getReadableRepr(default_v, true));
      if (i < param_texts.length-1) strb.append(",");
      param_texts[i] = strb.toString();
    }

    // formatting
    Map<PyParameter, Integer> param_indexes = new HashMap<PyParameter, Integer>();
    for (int i=0; i < params.length; i += 1) param_indexes.put(params[i], i);
    EnumSet<ParameterInfoUIContextEx.Flag>[] flags = (EnumSet<ParameterInfoUIContextEx.Flag>[])Array.newInstance(EnumSet.class, params.length);
    // ^^ gotta hate the covariance issues
    for (int i =0; i < flags.length; i += 1) flags[i] = EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class); 

    if (marked.getFlags().contains(Flag.IMPLICIT_FIRST_ARG)) {
      //arg_index -= 1; // argument 0 is parameter 1, thus kipping para,eter 0 which is 'self'
      flags[0].add(ParameterInfoUIContextEx.Flag.DISABLE); // show but mark as absent
    }
    int cur_arg_index = 0;
    for (PyExpression arg : arglist.getArguments()) {
      if (cur_arg_index == arg_index) {
        PyParameter param = result.getPlainMappedParams().get(arg);
        if (param != null) {
          final Integer param_index = param_indexes.get(param);
          if  (param_index < flags.length) {
            flags[param_index].add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
          }
        }
        else if (arg == result.getTupleArg()) {
          // mark all params that map to *arg
          for (PyParameter tpar : result.getTupleMappedParams()) {
            final Integer param_index = param_indexes.get(tpar);
            if (param_index != null && param_index.intValue() < flags.length) flags[param_index.intValue()].add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
          }
        }
        else if (arg == result.getKwdArg()) {
          // mark all params that map to **arg
          for (PyParameter tpar : result.getKwdMappedParams()) {
            final Integer param_index = param_indexes.get(tpar);
            if (param_index != null && param_index < flags.length) flags[param_index].add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
          }
        }
      }
      // else: stay unhilited
      cur_arg_index += 1;
    }

    final String NO_PARAMS_MSG = "<No parameters>";
    if (context instanceof ParameterInfoUIContextEx) {
      final ParameterInfoUIContextEx pic = (ParameterInfoUIContextEx)context;
      if (param_texts.length < 1) {
        param_texts = new String[]{NO_PARAMS_MSG};
        flags = new EnumSet[]{EnumSet.of(ParameterInfoUIContextEx.Flag.DISABLE)};
      }
      pic.setupUIComponentPresentation(param_texts, flags, context.getDefaultParameterColor());
    }
    else { // fallback, no hilite
      StringBuffer signatureBuilder = new StringBuffer();
      if (param_texts.length > 1) {
        for (String s : param_texts) signatureBuilder.append(s);
      }
      else signatureBuilder.append(NO_PARAMS_MSG);
      context.setupUIComponentPresentation(signatureBuilder.toString(), -1, 0, false, false, false,
                                           context.getDefaultParameterColor());
    }
  }
}
