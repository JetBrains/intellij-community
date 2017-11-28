// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.PyStructuralType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyCallExpression.PyMarkedCallee;

public class PyParameterInfoHandler implements ParameterInfoHandler<PyArgumentList, Pair<PyCallExpression, PyMarkedCallee>> {

  @NotNull
  private static final String NO_PARAMS_MSG = CodeInsightBundle.message("parameter.info.no.parameters");

  @Override
  public boolean couldShowInLookup() {
    return true;
  }

  @Override
  @NotNull
  public Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  @Nullable
  public PyArgumentList findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
    final PyArgumentList argumentList = findArgumentList(context, -1);

    if (argumentList != null) {
      final PyCallExpression call = argumentList.getCallExpression();
      if (call != null) {
        final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(argumentList.getProject(), argumentList.getContainingFile());
        final PyResolveContext resolveContext = PyResolveContext.noImplicits().withRemote().withTypeEvalContext(typeEvalContext);

        context.setItemsToShow(
          PyUtil
            .filterTopPriorityResults(call.multiResolveCallee(resolveContext))
            .stream()
            .filter(markedCallee -> markedCallee.getCallableType().getParameters(typeEvalContext) != null)
            .map(markedCallee -> Pair.createNonNull(call, markedCallee))
            .toArray()
        );

        return argumentList;
      }
    }

    return null;
  }

  @Nullable
  private static PyArgumentList findArgumentList(@NotNull ParameterInfoContext context, int parameterListStart) {
    final PyArgumentList argumentList =
      ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset() - 1, PyArgumentList.class);

    if (argumentList != null && parameterListStart >= 0 && argumentList.getTextRange().getStartOffset() != parameterListStart) {
      return PsiTreeUtil.getParentOfType(argumentList, PyArgumentList.class);
    }

    return argumentList;
  }

  @Override
  public void showParameterInfo(@NotNull PyArgumentList element, @NotNull CreateParameterInfoContext context) {
    context.showHint(element, element.getTextOffset(), this);
  }

  @Override
  @Nullable
  public PyArgumentList findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
    return findArgumentList(context, context.getParameterListStart());
  }

  /*
   <b>Note: instead of parameter index, we directly store parameter's offset for later use.</b><br/>
   We cannot store an index since we cannot determine what is an argument until we actually map arguments to parameters.
   This is because a tuple in arguments may be a whole argument or map to a tuple parameter.
   */
  @Override
  public void updateParameterInfo(@NotNull PyArgumentList argumentList, @NotNull UpdateParameterInfoContext context) {
    if (context.getParameterOwner() != argumentList) {
      context.removeHint();
      return;
    }

    // align offset to nearest expression; context may point to a space, etc.
    final List<PyExpression> flattenedArguments = PyUtil.flattenedParensAndLists(argumentList.getArguments());
    final int allegedCursorOffset = context.getOffset(); // this is already shifted backwards to skip spaces

    if (!argumentList.getTextRange().contains(allegedCursorOffset) && argumentList.getText().endsWith(")")) {
      context.removeHint();
      return;
    }

    final PsiFile file = context.getFile();
    final CharSequence chars = file.getViewProvider().getContents();

    int offset = -1;
    for (PyExpression argument : flattenedArguments) {
      final TextRange range = argument.getTextRange();

      // widen the range to include all whitespace around the argument
      final int left = CharArrayUtil.shiftBackward(chars, range.getStartOffset() - 1, " \t\r\n");
      int right = CharArrayUtil.shiftForwardCarefully(chars, range.getEndOffset(), " \t\r\n");
      if (argument.getParent() instanceof PyListLiteralExpression || argument.getParent() instanceof PyTupleExpression) {
        right = CharArrayUtil.shiftForward(chars, range.getEndOffset(), " \t\r\n])");
      }

      if (left <= allegedCursorOffset && right >= allegedCursorOffset) {
        offset = range.getStartOffset();
        break;
      }
    }

    context.setCurrentParameter(offset);
  }

  @Override
  public void updateUI(@NotNull Pair<PyCallExpression, PyMarkedCallee> callAndCallee, @NotNull ParameterInfoUIContext context) {
    final PyCallExpression callExpression = callAndCallee.getFirst();
    PyPsiUtils.assertValid(callExpression);

    final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(callExpression.getProject(), callExpression.getContainingFile());
    final PyMarkedCallee markedCallee = callAndCallee.getSecond();

    final List<PyCallableParameter> parameters = markedCallee.getCallableType().getParameters(typeEvalContext);
    if (parameters == null) return;

    final PyCallExpression.PyArgumentsMapping mapping = PyCallExpressionHelper.mapArguments(callExpression, markedCallee, typeEvalContext);
    if (mapping.getMarkedCallee() == null) return;

    final Map<Integer, PyCallableParameter> indexToNamedParameter = new HashMap<>(parameters.size());

    // param -> hint index. indexes are not contiguous, because some hints are parentheses.
    final Map<PyCallableParameter, Integer> parameterToHintIndex = new HashMap<>();
    // formatting of hints: hint index -> flags. this includes flags for parens.
    final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hintFlags = new HashMap<>();

    final List<String> hintsList =
      buildParameterListHint(parameters, indexToNamedParameter, parameterToHintIndex, hintFlags, typeEvalContext);

    final int currentParamOffset = context.getCurrentParameterIndex(); // in Python mode, we get an offset here, not an index!

    // gray out enough first parameters as implicit (self, cls, ...)
    for (int i = 0; i < markedCallee.getImplicitOffset(); i++) {
      if (indexToNamedParameter.containsKey(i)) {
        final PyCallableParameter parameter = indexToNamedParameter.get(i);
        hintFlags.get(parameterToHintIndex.get(parameter)).add(ParameterInfoUIContextEx.Flag.DISABLE); // show but mark as absent
      }
    }

    final List<PyExpression> flattenedArguments = PyUtil.flattenedParensAndLists(callExpression.getArguments());
    final int lastParamIndex =
      collectHighlights(mapping, parameters, parameterToHintIndex, hintFlags, flattenedArguments, currentParamOffset);

    highlightNext(markedCallee, parameters, indexToNamedParameter, parameterToHintIndex, hintFlags, flattenedArguments.isEmpty(),
                  lastParamIndex);

    String[] hints = ArrayUtil.toStringArray(hintsList);
    if (context instanceof ParameterInfoUIContextEx) {
      final ParameterInfoUIContextEx pic = (ParameterInfoUIContextEx)context;
      EnumSet[] flags = new EnumSet[hintFlags.size()];
      for (int i = 0; i < flags.length; i++) flags[i] = hintFlags.get(i);
      if (hints.length < 1) {
        hints = new String[]{NO_PARAMS_MSG};
        flags = new EnumSet[]{EnumSet.of(ParameterInfoUIContextEx.Flag.DISABLE)};
      }

      //noinspection unchecked
      pic.setupUIComponentPresentation(hints, flags, context.getDefaultParameterColor());
    }
    else { // fallback, no highlight
      final StringBuilder signatureBuilder = new StringBuilder();
      if (hints.length > 1) {
        for (String s : hints) signatureBuilder.append(s);
      }
      else {
        signatureBuilder.append(XmlStringUtil.escapeString(NO_PARAMS_MSG));
      }
      context.setupUIComponentPresentation(
        signatureBuilder.toString(), -1, 0, false, false, false, context.getDefaultParameterColor()
      );
    }
  }

  private static void highlightNext(@NotNull final PyMarkedCallee marked,
                                    @NotNull final List<PyCallableParameter> parameterList,
                                    @NotNull final Map<Integer, PyCallableParameter> indexToNamedParameter,
                                    @NotNull final Map<PyCallableParameter, Integer> parameterToHintIndex,
                                    @NotNull final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hintFlags,
                                    boolean isArgsEmpty, int lastParamIndex) {
    boolean canOfferNext = true; // can we highlight next unfilled parameter
    for (EnumSet<ParameterInfoUIContextEx.Flag> set : hintFlags.values()) {
      if (set.contains(ParameterInfoUIContextEx.Flag.HIGHLIGHT)) {
        canOfferNext = false;
      }
    }
    // highlight the next parameter to be filled
    if (canOfferNext) {
      int highlightIndex = Integer.MAX_VALUE; // initially beyond reason = no highlight
      if (isArgsEmpty) {
        highlightIndex = marked.getImplicitOffset(); // no args, highlight first (PY-3690)
      }
      else if (lastParamIndex < parameterList.size() - 1) { // lastParamIndex not at end, or no args
        if (indexToNamedParameter.get(lastParamIndex).isPositionalContainer()) {
          highlightIndex = lastParamIndex; // stick to *arg
        }
        else {
          highlightIndex = lastParamIndex + 1; // highlight next
        }
      }
      else if (lastParamIndex == parameterList.size() - 1) { // we're right after the end of param list
        final PyCallableParameter parameter = indexToNamedParameter.get(lastParamIndex);
        if (parameter.isPositionalContainer() || parameter.isKeywordContainer()) {
          highlightIndex = lastParamIndex; // stick to *arg
        }
      }
      if (indexToNamedParameter.containsKey(highlightIndex)) {
        hintFlags.get(parameterToHintIndex.get(indexToNamedParameter.get(highlightIndex))).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
      }
    }
  }

  /**
   * match params to available args, highlight current param(s)
   *
   * @return index of last parameter
   */
  private static int collectHighlights(@NotNull final PyCallExpression.PyArgumentsMapping mapping,
                                       @NotNull final List<PyCallableParameter> parameterList,
                                       @NotNull final Map<PyCallableParameter, Integer> parameterHintToIndex,
                                       @NotNull final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hintFlags,
                                       @NotNull final List<PyExpression> flatArgs,
                                       int currentParamOffset) {
    final PyMarkedCallee callee = mapping.getMarkedCallee();
    assert callee != null;
    int lastParamIndex = callee.getImplicitOffset();
    final Map<PyExpression, PyCallableParameter> mappedParameters = mapping.getMappedParameters();
    final Map<PyExpression, PyCallableParameter> mappedTupleParameters = mapping.getMappedTupleParameters();
    for (PyExpression arg : flatArgs) {
      final boolean mustHighlight = arg.getTextRange().contains(currentParamOffset);
      PsiElement seeker = arg;
      // An argument tuple may have been flattened; find it
      while (!(seeker instanceof PyArgumentList) && seeker instanceof PyExpression && !mappedParameters.containsKey(seeker)) {
        seeker = seeker.getParent();
      }
      if (seeker instanceof PyExpression) {
        final PyCallableParameter parameter = mappedParameters.get((PyExpression)seeker);
        lastParamIndex = Math.max(lastParamIndex, parameterList.indexOf(parameter));
        if (parameter != null) {
          highlightParameter(parameter, parameterHintToIndex, hintFlags, mustHighlight);
        }
      }
      else if (PyCallExpressionHelper.isVariadicPositionalArgument(arg)) {
        for (PyCallableParameter parameter : mapping.getParametersMappedToVariadicPositionalArguments()) {
          lastParamIndex = Math.max(lastParamIndex, parameterList.indexOf(parameter));
          highlightParameter(parameter, parameterHintToIndex, hintFlags, mustHighlight);
        }
      }
      else if (PyCallExpressionHelper.isVariadicKeywordArgument(arg)) {
        for (PyCallableParameter parameter : mapping.getParametersMappedToVariadicKeywordArguments()) {
          lastParamIndex = Math.max(lastParamIndex, parameterList.indexOf(parameter));
          highlightParameter(parameter, parameterHintToIndex, hintFlags, mustHighlight);
        }
      }
      else {
        final PyTupleParameter tupleParameter = Optional
        .ofNullable(mappedTupleParameters.get(arg))
        .map(PyCallableParameter::getParameter)
        .map(psi -> PyUtil.as(psi, PyTupleParameter.class))
        .orElse(null);

        if (tupleParameter != null) {
          for (PyCallableParameter parameter : getFlattenedTupleParameterComponents(tupleParameter)) {
            lastParamIndex = Math.max(lastParamIndex, parameterList.indexOf(parameter));
            highlightParameter(parameter, parameterHintToIndex, hintFlags, mustHighlight);
          }
        }
      }
    }
    return lastParamIndex;
  }

  @NotNull
  private static List<PyCallableParameter> getFlattenedTupleParameterComponents(@NotNull PyTupleParameter parameter) {
    final List<PyCallableParameter> results = new ArrayList<>();
    for (PyParameter component : parameter.getContents()) {
      if (component instanceof PyNamedParameter) {
        results.add(PyCallableParameterImpl.psi(component));
      }
      else if (component instanceof PyTupleParameter) {
        results.addAll(getFlattenedTupleParameterComponents((PyTupleParameter)component));
      }
    }
    return results;
  }

  private static void highlightParameter(@NotNull final PyCallableParameter parameter,
                                         @NotNull final Map<PyCallableParameter, Integer> parameterToHintIndex,
                                         @NotNull final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hintFlags,
                                         boolean mustHighlight) {
    final Integer hintIndex = parameterToHintIndex.get(parameter);
    if (mustHighlight && hintIndex != null && hintFlags.containsKey(hintIndex)) {
      hintFlags.get(hintIndex).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
    }
  }

  /**
   * builds the textual picture and the list of named parameters
   *
   * @param parameters            parameters of a callable
   * @param indexToNamedParameter used to collect all named parameters of callable
   * @param parameterToHintIndex  used to collect info about parameter hints
   * @param hintFlags             mark parameter as deprecated/highlighted/strikeout
   * @param context               context to be used to get parameter representation
   */
  private static List<String> buildParameterListHint(@NotNull List<PyCallableParameter> parameters,
                                                     @NotNull final Map<Integer, PyCallableParameter> indexToNamedParameter,
                                                     @NotNull final Map<PyCallableParameter, Integer> parameterToHintIndex,
                                                     @NotNull final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hintFlags,
                                                     @NotNull TypeEvalContext context) {
    final List<String> hintsList = new ArrayList<>();
    final int[] currentParameterIndex = new int[]{0};
    ParamHelper.walkDownParameters(
      parameters,
      new ParamHelper.ParamWalker() {
        @Override
        public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          hintFlags.put(hintsList.size(), EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
          hintsList.add("(");
        }

        @Override
        public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          hintFlags.put(hintsList.size(), EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
          hintsList.add(last ? ")" : "), ");
        }

        @Override
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          visitNonPsiParameter(PyCallableParameterImpl.psi(param), first, last);
        }

        @Override
        public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) {
          hintFlags.put(hintsList.size(), EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
          hintsList.add(last ? "*" : "*, ");
          currentParameterIndex[0]++;
        }

        @Override
        public void visitNonPsiParameter(@NotNull PyCallableParameter parameter, boolean first, boolean last) {
          indexToNamedParameter.put(currentParameterIndex[0], parameter);
          final StringBuilder stringBuilder = new StringBuilder();
          stringBuilder.append(parameter.getPresentableText(true, context, type -> type == null || type instanceof PyStructuralType));
          if (!last) stringBuilder.append(", ");
          final int hintIndex = hintsList.size();
          parameterToHintIndex.put(parameter, hintIndex);
          hintFlags.put(hintIndex, EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
          hintsList.add(stringBuilder.toString());
          currentParameterIndex[0]++;
        }
      }
    );
    return hintsList;
  }
}
