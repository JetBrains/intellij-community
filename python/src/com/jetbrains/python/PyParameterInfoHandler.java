package com.jetbrains.python;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.FP;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.jetbrains.python.psi.PyCallExpression.PyMarkedCallee;

/**
 * @author dcheryasov
 */
public class PyParameterInfoHandler implements ParameterInfoHandler<PyArgumentList, PyArgumentList.AnalysisResult> {
  
  public boolean couldShowInLookup() {
    return true;
  }
  public Object[] getParametersForLookup(final LookupElement item, final ParameterInfoContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;  // we don't
  }

  public Object[] getParametersForDocumentation(final PyArgumentList.AnalysisResult p, final ParameterInfoContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;  // we don't
  }

  public PyArgumentList findElementForParameterInfo(final CreateParameterInfoContext context) {
    PyArgumentList arglist = findArgumentList(context);
    if (arglist != null) {
      PyArgumentList.AnalysisResult result = arglist.analyzeCall(TypeEvalContext.fast());
      if (result.getMarkedCallee() != null && !result.isImplicitlyResolved()) {
        context.setItemsToShow(new Object[] { result });
        return arglist;
      }
    }
    return null;
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

  /**
   <b>Note: instead of parameter index, we directly store parameter's offset for later use.</b><br/>
   We cannot store an index since we cannot determine what is an argument until we actually map arguments to parameters.
   This is because a tuple in arguments may be a whole argument or map to a tuple parameter.
  */
  public void updateParameterInfo(@NotNull final PyArgumentList arglist, final UpdateParameterInfoContext context) {
    if (context.getParameterOwner() != arglist) {
      context.removeHint();
      return;
    }
    // align offset to nearest expression; context may point to a space, etc.
    List<PyExpression> flat_args = PyUtil.flattenedParensAndLists(arglist.getArguments());
    int alleged_cursor_offset = context.getOffset(); // this is already shifted backwards to skip spaces
    PsiFile file = context.getFile();
    CharSequence chars = file.getViewProvider().getContents();
    int offset = -1;
    for (PyExpression arg : flat_args) {
      TextRange range = arg.getTextRange();
      // widen the range to include all whitespace around the arg
      int left = CharArrayUtil.shiftBackward(chars, range.getStartOffset()-1, " \t\r\n");
      int right = CharArrayUtil.shiftForwardCarefully(chars, range.getEndOffset(), " \t\r\n");
      if (left <= alleged_cursor_offset && right >= alleged_cursor_offset) {
        offset = range.getStartOffset();
        break;
      }
    }
    context.setCurrentParameter(offset);
  }

  public String getParameterCloseChars() {
    return ",()"; // lpar may mean a nested tuple param, so it's included
  }

  public boolean tracksParameterIndex() {
    return false;
  }

  public void updateUI(final PyArgumentList.AnalysisResult prev_result, final ParameterInfoUIContext context) {
    if (prev_result == null) return;
    final PyArgumentList arglist = prev_result.getArgumentList();
    if (!arglist.isValid()) return;
    // really we need to redo analysis every UI update; findElementForParameterInfo isn't called while typing
    PyArgumentList.AnalysisResult result = arglist.analyzeCall(TypeEvalContext.fast());
    PyMarkedCallee marked = result.getMarkedCallee();
    assert marked != null : "findElementForParameterInfo() did it wrong!";
    final Callable callable = marked.getCallable();
    if (callable == null) return; // resolution failed

    final List<PyParameter> raw_params = Arrays.asList(callable.getParameterList().getParameters());
    final List<PyNamedParameter> n_param_list = new ArrayList<PyNamedParameter>(raw_params.size());
    final List<String> hint_texts = new ArrayList<String>(raw_params.size());

    // param -> hint index. indexes are not contiguous, because some hints are parentheses.
    final Map<PyNamedParameter, Integer> param_indexes = new HashMap<PyNamedParameter, Integer>();
    // formatting of hints: hint index -> flags. this includes flags for parens.
    final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hint_flags = new HashMap<Integer, EnumSet<ParameterInfoUIContextEx.Flag>>();

    // build the textual picture and the list of named parameters
    ParamHelper.walkDownParamArray(
      callable.getParameterList().getParameters(),
      new ParamHelper.ParamWalker() {
        public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          hint_flags.put(hint_texts.size(), EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
          hint_texts.add("(");
        }

        public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          hint_flags.put(hint_texts.size(), EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
          if (last) hint_texts.add(")");
          else hint_texts.add("), ");
        }

        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          n_param_list.add(param);
          StringBuilder strb = new StringBuilder();
          strb.append(param.getRepr(true));
          if (! last) strb.append(", ");
          int hint_index = hint_texts.size();
          param_indexes.put(param, hint_index);
          hint_flags.put(hint_index, EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
          hint_texts.add(strb.toString());
        }

        public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) {
          hint_flags.put(hint_texts.size(), EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
          if (last) hint_texts.add("*");
          else hint_texts.add("*, ");
        }
      }
    );


    final int current_param_offset = context.getCurrentParameterIndex(); // in Python mode, we get an offset here, not an index!

    // gray out enough first parameters as implicit
    for (int i=0; i < marked.getImplicitOffset(); i += 1) {
      hint_flags.get(param_indexes.get(n_param_list.get(i))).add(ParameterInfoUIContextEx.Flag.DISABLE); // show but mark as absent
    }

    // match params to available args, highlight current param(s)
    boolean can_offer_next = true; // can we highlight next unfilled parameter
    int last_param_index = marked.getImplicitOffset();
    final List<PyExpression> flat_args = PyUtil.flattenedParensAndLists(arglist.getArguments());
    for (PyExpression arg : flat_args) {
      can_offer_next &= !(arg instanceof  PyKeywordArgument);
      final boolean must_highlight = arg.getTextRange().contains(current_param_offset);
      PsiElement seeker = arg;
      while (seeker != arglist && seeker != null && !result.getPlainMappedParams().containsKey(seeker)) {
        seeker = seeker.getParent(); // flattener may have flattened a tuple arg that is mapped to a plain param; find it.
      }
      if (seeker instanceof PyExpression) {
        PyNamedParameter param = result.getPlainMappedParams().get((PyExpression)seeker);
        last_param_index = Math.max(last_param_index, raw_params.indexOf(param));
        if (must_highlight && param != null) {
          final Integer param_index = param_indexes.get(param);
          if  (param_index < hint_flags.size()) {
            hint_flags.get(param_index).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
            can_offer_next = false;
          }
        }
      }
      else if (arg == result.getTupleArg()) {
        // mark all params that map to *arg
        for (PyNamedParameter tpar : result.getTupleMappedParams()) {
          last_param_index = Math.max(last_param_index, raw_params.indexOf(tpar));
          final Integer param_index = param_indexes.get(tpar);
          if (must_highlight && param_index != null && param_index < hint_flags.size()) {
            hint_flags.get(param_index).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
            can_offer_next = false;
          }
        }
      }
      else if (arg == result.getKwdArg()) {
        // mark all n_params that map to **arg
        for (PyNamedParameter tpar : result.getKwdMappedParams()) {
          last_param_index = Math.max(last_param_index, raw_params.indexOf(tpar));
          final Integer param_index = param_indexes.get(tpar);
          if (must_highlight && param_index != null && param_index < hint_flags.size()) {
            hint_flags.get(param_index).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
            can_offer_next = false;
          }
        }
      }
      else {
        // maybe it's mapped to a nested tuple?
        List<PyNamedParameter> nparams = result.getNestedMappedParams().get(arg);
        if (nparams != null) {
          for (PyNamedParameter tpar : nparams) {
            last_param_index = Math.max(last_param_index, raw_params.indexOf(tpar));
            final Integer param_index = param_indexes.get(tpar);
            if (must_highlight && param_index != null && param_index < hint_flags.size()) {
              hint_flags.get(param_index).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
              can_offer_next = false;
            }
          }
        }
      }
      // else: stay unhilited
    }

    // highlight the next parameter to be filled
    if (can_offer_next) {
      int highlight_index = Integer.MAX_VALUE; // initially beyond reason = no highlight
      if (last_param_index < raw_params.size() - 1 || flat_args.size() == 0) { // last_param not at end, or no args
        if (flat_args.size() == 0) highlight_index = marked.getImplicitOffset(); // no args, highlight first (PY-3690)
        else {
          if (n_param_list.get(last_param_index).isPositionalContainer()) highlight_index = last_param_index; // stick to *arg
          else highlight_index = last_param_index+1; // highlight next
        }
      }
      else if (last_param_index == raw_params.size() - 1) { // we're right after the end of param list
        if (n_param_list.get(last_param_index).isPositionalContainer()) highlight_index = last_param_index; // stick to *arg
      }
      if (highlight_index < n_param_list.size()) {
        hint_flags.get(param_indexes.get(n_param_list.get(highlight_index))).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
      }
    }

    final String NO_PARAMS_MSG = "<No parameters>";
    String[] hints = ArrayUtil.toStringArray(hint_texts);
    if (context instanceof ParameterInfoUIContextEx) {
      final ParameterInfoUIContextEx pic = (ParameterInfoUIContextEx)context;
      EnumSet<ParameterInfoUIContextEx.Flag>[] flags = new EnumSet[hint_flags.size()];
      for (int i = 0; i < flags.length; i += 1) flags[i] = hint_flags.get(i);
      if (hints.length < 1) {
        hints = new String[]{NO_PARAMS_MSG};
        flags = new EnumSet[]{EnumSet.of(ParameterInfoUIContextEx.Flag.DISABLE)};
      }
      pic.setupUIComponentPresentation(hints, flags, context.getDefaultParameterColor());
    }
    else { // fallback, no hilite
      StringBuilder signatureBuilder = new StringBuilder();
      if (hints.length > 1) {
        for (String s : hints) signatureBuilder.append(s);
      }
      else signatureBuilder.append(NO_PARAMS_MSG);
      context.setupUIComponentPresentation(
        signatureBuilder.toString(), -1, 0, false, false, false, context.getDefaultParameterColor()
      );
    }
  }
}
