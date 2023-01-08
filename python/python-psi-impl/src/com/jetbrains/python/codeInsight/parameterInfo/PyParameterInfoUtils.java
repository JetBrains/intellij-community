package com.jetbrains.python.codeInsight.parameterInfo;

import com.intellij.codeInsight.parameterInfo.ParameterFlag;
import com.intellij.lang.parameterInfo.ParameterInfoUtilsBase;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class PyParameterInfoUtils {

  @Nullable
  public static PyArgumentList findArgumentList(PsiFile file, int contextOffset, int parameterListStart) {
    final PyArgumentList argumentList =
      ParameterInfoUtilsBase.findParentOfType(file, contextOffset - 1, PyArgumentList.class);

    if (argumentList != null && parameterListStart >= 0 && argumentList.getTextRange().getStartOffset() != parameterListStart) {
      return PsiTreeUtil.getParentOfType(argumentList, PyArgumentList.class);
    }

    return argumentList;
  }

  public static List<Pair<PyCallExpression, PyCallableType>> findCallCandidates(PyArgumentList argumentList) {
    if (argumentList != null) {
      final PyCallExpression call = argumentList.getCallExpression();
      if (call != null) {
        final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(argumentList.getProject(), argumentList.getContainingFile());
        final PyResolveContext resolveContext = PyResolveContext.defaultContext(typeEvalContext).withRemote();

        return call.multiResolveCallee(resolveContext)
          .stream()
          .filter(callableType -> callableType.getParameters(typeEvalContext) != null)
          .map(callableType -> Pair.createNonNull(call, callableType))
          .collect(Collectors.toList());
      }
    }
    return null;
  }

  /**
   * Builds a list of textual representation of parameters
   * Returns two lists: parameters with type hints and parameters with type annotations
   *
   * @param parameters            parameters of a callable
   * @param indexToNamedParameter used to collect all named parameters of callable
   * @param parameterToHintIndex  used to collect info about parameter hints
   * @param hintFlags             mark parameter as deprecated/highlighted/strikeout
   * @param context               context to be used to get parameter representation
   */
  public static Pair<List<String>, List<String>> buildParameterListHint(@NotNull List<PyCallableParameter> parameters,
                                                                        @NotNull final Map<Integer, PyCallableParameter> indexToNamedParameter,
                                                                        @NotNull final Map<PyCallableParameter, Integer> parameterToHintIndex,
                                                                        @NotNull final Map<Integer, EnumSet<ParameterFlag>> hintFlags,
                                                                        @NotNull TypeEvalContext context) {
    final List<String> hintsList = new ArrayList<>();
    final List<String> annotations = new ArrayList<>();
    final int[] currentParameterIndex = new int[]{0};
    ParamHelper.walkDownParameters(
      parameters,
      new ParamHelper.ParamWalker() {
        @Override
        public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          hintFlags.put(hintsList.size(), EnumSet.noneOf(ParameterFlag.class));
          hintsList.add("(");
          annotations.add("");
        }

        @Override
        public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          hintFlags.put(hintsList.size(), EnumSet.noneOf(ParameterFlag.class));
          hintsList.add(last ? ")" : "), ");
          annotations.add("");
        }

        @Override
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          visitNonPsiParameter(PyCallableParameterImpl.psi(param), first, last);
        }

        @Override
        public void visitSlashParameter(@NotNull PySlashParameter param, boolean first, boolean last) {
          hintFlags.put(hintsList.size(), EnumSet.noneOf(ParameterFlag.class));
          hintsList.add(last ? PySlashParameter.TEXT : (PySlashParameter.TEXT + ", "));
          annotations.add("");
          currentParameterIndex[0]++;
        }

        @Override
        public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) {
          hintFlags.put(hintsList.size(), EnumSet.noneOf(ParameterFlag.class));
          hintsList.add(last ? PySingleStarParameter.TEXT : (PySingleStarParameter.TEXT + ", "));
          annotations.add("");
          currentParameterIndex[0]++;
        }

        @Override
        public void visitNonPsiParameter(@NotNull PyCallableParameter parameter, boolean first, boolean last) {
          indexToNamedParameter.put(currentParameterIndex[0], parameter);
          final StringBuilder stringBuilder = new StringBuilder();
          boolean annotationAdded = false;
          if (parameter.getParameter() instanceof PyNamedParameter) {
            final String annotation = ((PyNamedParameter)parameter.getParameter()).getAnnotationValue();
            if (annotation != null) {
              String annotationText = ParamHelper.getNameInSignature(parameter) + ": " +
                                      annotation.replaceAll("\n", "").replaceAll("\\s+", " ");
              annotations.add(last ? annotationText : (annotationText + ", "));
              annotationAdded = true;
            }
          }
          if (!annotationAdded) {
            annotations.add("");
          }
          stringBuilder.append(parameter.getPresentableText(true, context, type -> type == null || type instanceof PyStructuralType));
          if (!last) stringBuilder.append(", ");
          final int hintIndex = hintsList.size();
          parameterToHintIndex.put(parameter, hintIndex);
          hintFlags.put(hintIndex, EnumSet.noneOf(ParameterFlag.class));
          hintsList.add(stringBuilder.toString());
          currentParameterIndex[0]++;
        }
      }
    );
    return new Pair<>(hintsList, annotations);
  }

  public static void highlightParameter(@NotNull final PyCallableParameter parameter,
                                        @NotNull final Map<PyCallableParameter, Integer> parameterToHintIndex,
                                        @NotNull final Map<Integer, EnumSet<ParameterFlag>> hintFlags,
                                        boolean mustHighlight) {
    final Integer hintIndex = parameterToHintIndex.get(parameter);
    if (mustHighlight && hintIndex != null && hintFlags.containsKey(hintIndex)) {
      hintFlags.get(hintIndex).add(ParameterFlag.HIGHLIGHT);
    }
  }

  @NotNull
  public static List<PyCallableParameter> getFlattenedTupleParameterComponents(@NotNull PyTupleParameter parameter) {
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

  /**
   * match params to available args, highlight current param(s)
   *
   * @return index of last parameter
   */
  public static int collectHighlights(@NotNull final PyCallExpression.PyArgumentsMapping mapping,
                                      @NotNull final List<PyCallableParameter> parameterList,
                                      @NotNull final Map<PyCallableParameter, Integer> parameterHintToIndex,
                                      @NotNull final Map<Integer, EnumSet<ParameterFlag>> hintFlags,
                                      @NotNull final List<PyExpression> flatArgs,
                                      int currentParamOffset) {
    final PyCallableType callableType = mapping.getCallableType();
    assert callableType != null;
    int lastParamIndex = callableType.getImplicitOffset();
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

  public static void highlightNext(@NotNull final PyCallableType callableType,
                                   @NotNull final List<PyCallableParameter> parameterList,
                                   @NotNull final Map<Integer, PyCallableParameter> indexToNamedParameter,
                                   @NotNull final Map<PyCallableParameter, Integer> parameterToHintIndex,
                                   @NotNull final Map<Integer, EnumSet<ParameterFlag>> hintFlags,
                                   boolean isArgsEmpty, int lastParamIndex) {
    boolean canOfferNext = true; // can we highlight next unfilled parameter
    for (EnumSet<ParameterFlag> set : hintFlags.values()) {
      if (set.contains(ParameterFlag.HIGHLIGHT)) {
        canOfferNext = false;
        break;
      }
    }
    // highlight the next parameter to be filled
    if (canOfferNext) {
      int highlightIndex = Integer.MAX_VALUE; // initially beyond reason = no highlight
      if (isArgsEmpty) {
        highlightIndex = callableType.getImplicitOffset(); // no args, highlight first (PY-3690)
      }
      else if (lastParamIndex < parameterList.size() - 1) { // lastParamIndex not at end, or no args
        if (!indexToNamedParameter.containsKey(lastParamIndex) || indexToNamedParameter.get(lastParamIndex).isPositionalContainer()) {
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
        hintFlags.get(parameterToHintIndex.get(indexToNamedParameter.get(highlightIndex))).add(ParameterFlag.HIGHLIGHT);
      }
    }
  }

  public static void highlightParameters(PyCallExpression callExpression,
                                         PyCallableType callableType,
                                         List<PyCallableParameter> parameters,
                                         PyCallExpression.PyArgumentsMapping mapping,
                                         Map<Integer, PyCallableParameter> indexToNamedParameter,
                                         Map<PyCallableParameter, Integer> parameterToHintIndex,
                                         Map<Integer, EnumSet<ParameterFlag>> hintFlags,
                                         int currentParamOffset) {
    // gray out enough first parameters as implicit (self, cls, ...)
    for (int i = 0; i < callableType.getImplicitOffset(); i++) {
      if (indexToNamedParameter.containsKey(i)) {
        final PyCallableParameter parameter = indexToNamedParameter.get(i);
        hintFlags.get(parameterToHintIndex.get(parameter)).add(ParameterFlag.DISABLE); // show but mark as absent
      }
    }

    final List<PyExpression> flattenedArguments = PyUtil.flattenedParensAndLists(callExpression.getArguments());
    final int lastParamIndex =
      collectHighlights(mapping, parameters, parameterToHintIndex, hintFlags, flattenedArguments, currentParamOffset);

    highlightNext(callableType, parameters, indexToNamedParameter, parameterToHintIndex, hintFlags, flattenedArguments.isEmpty(),
                  lastParamIndex);
  }

  public static int findCurrentParameter(@NotNull PyArgumentList argumentList, int allegedCursorOffset, PsiFile file) {
    CharSequence chars = file.getViewProvider().getContents();
    // align offset to nearest expression; context may point to a space, etc.
    final List<PyExpression> flattenedArguments = PyUtil.flattenedParensAndLists(argumentList.getArguments());
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
    return offset;
  }

  @Nullable
  public static ParameterHints buildParameterHints(@NotNull Pair<PyCallExpression, PyCallableType> callAndCallee,
                                                   int currentParamOffset) {
    final PyCallExpression callExpression = callAndCallee.getFirst();
    PyPsiUtils.assertValid(callExpression);

    final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(callExpression.getProject(), callExpression.getContainingFile());
    final PyCallableType callableType = callAndCallee.getSecond();

    final List<PyCallableParameter> parameters = callableType.getParameters(typeEvalContext);
    if (parameters == null) return null;

    final PyCallExpression.PyArgumentsMapping mapping = PyCallExpressionHelper.mapArguments(callExpression, callableType, typeEvalContext);
    if (mapping.getCallableType() == null) return null;

    final Map<Integer, PyCallableParameter> indexToNamedParameter = new HashMap<>(parameters.size());

    // param -> hint index. indexes are not contiguous, because some hints are parentheses.
    final Map<PyCallableParameter, Integer> parameterToHintIndex = new HashMap<>();
    // formatting of hints: hint index -> flags. this includes flags for parens.
    final Map<Integer, EnumSet<ParameterFlag>> hintFlags = new HashMap<>();

    final Pair<List<String>, List<String>> hintsAndAnnotations =
      buildParameterListHint(parameters, indexToNamedParameter, parameterToHintIndex, hintFlags, typeEvalContext);
    final List<String> hintsList = hintsAndAnnotations.first;
    final List<String> annotations = hintsAndAnnotations.second;

    highlightParameters(callExpression, callableType, parameters, mapping, indexToNamedParameter, parameterToHintIndex, hintFlags,
                        currentParamOffset);

    return new ParameterHints(hintsList, hintFlags, annotations);
  }
}
