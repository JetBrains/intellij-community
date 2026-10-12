// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.parameterInfo;

import com.intellij.codeInsight.parameterInfo.ParameterFlag;
import com.intellij.lang.parameterInfo.ParameterInfoUtilsBase;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.ast.PyAstSingleStarParameter;
import com.jetbrains.python.ast.PyAstSlashParameter;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyStructuralType;
import com.jetbrains.python.psi.types.PyTypeRendererFeature;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.jetbrains.python.psi.types.PyTypeUtilKt.isAnyOrUnknown;

@ApiStatus.Internal
public final class PyParameterInfoUtils {

  private static final Pattern SYNTHETIC_POSITIONAL_NAME = Pattern.compile("__p\\d+");

  /**
   * Matches a parameter by its PSI, or by identity when it has no PSI.
   * Two parameters without PSI can be equal, for example the parameters of {@code Callable[[int, int], None]}.
   */
  private static final HashingStrategy<PyCallableParameter> PARAMETER_STRATEGY = new HashingStrategy<>() {
    @Override
    public int hashCode(@Nullable PyCallableParameter parameter) {
      if (parameter == null) return 0;
      final PyParameter psi = parameter.getParameter();
      return psi != null ? psi.hashCode() : System.identityHashCode(parameter);
    }

    @Override
    public boolean equals(@Nullable PyCallableParameter p1, @Nullable PyCallableParameter p2) {
      if (p1 == p2) return true;
      if (p1 == null || p2 == null) return false;
      final PyParameter psi = p1.getParameter();
      return psi != null && psi.equals(p2.getParameter());
    }
  };

  public static @Nullable PyArgumentList findArgumentList(PsiFile file, int contextOffset, int parameterListStart) {
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
        final PyResolveContext resolveContext = PyResolveContext.defaultContext(typeEvalContext);

        return StreamEx.of(call.multiResolveCallee(resolveContext))
          .append(getConsoleCallableTypes(call, resolveContext))
          .filter(callableType -> callableType.getParameters(typeEvalContext) != null)
          .map(callableType -> Pair.createNonNull(call, callableType))
          .collect(Collectors.toList());
      }
    }
    return null;
  }

  private static @NotNull List<PyCallableType> getConsoleCallableTypes(@NotNull PyCallExpression call,
                                                                       @NotNull PyResolveContext resolveContext) {
    if (!(call.getCallee() instanceof PyReferenceExpression callee)) return List.of();
    final PsiPolyVariantReference consoleReference =
      PythonRuntimeService.getInstance().getPydevConsoleReference(callee, resolveContext.withRemote());
    if (consoleReference == null) return List.of();

    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    return StreamEx.of(consoleReference.multiResolve(false))
      .map(ResolveResult::getElement)
      .select(PyTypedElement.class)
      .map(context::getType)
      .select(PyCallableType.class)
      .toList();
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
  public static List<ParameterDescription> buildParameterListHint(@NotNull List<PyCallableParameter> parameters,
                                                                  final @NotNull Map<Integer, PyCallableParameter> indexToNamedParameter,
                                                                  final @NotNull Map<PyCallableParameter, Integer> parameterToHintIndex,
                                                                  final @NotNull Map<Integer, EnumSet<ParameterFlag>> hintFlags,
                                                                  @NotNull TypeEvalContext context) {
    final int[] currentParameterIndex = new int[]{0};
    final int[] parameterPosition = new int[]{0};
    final Set<String> usedNames = StreamEx.of(parameters)
      .remove(PyParameterInfoUtils::hasSyntheticName)
      .map(PyCallableParameter::getName)
      .nonNull()
      .toCollection(HashSet::new);
    final List<ParameterDescription> parameterDescriptions = new ArrayList<>();
    final Map<PyParameter, PyCallableParameter> parameterMap = StreamEx.of(parameters)
      .filter(p -> p.getParameter() != null)
      .toMap(PyCallableParameter::getParameter, Function.identity(), (a, _) -> a);
    ParamHelper.walkDownParameters(
      parameters,
      new ParamHelper.ParamWalker() {
        @Override
        public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          hintFlags.put(parameterDescriptions.size(), EnumSet.noneOf(ParameterFlag.class));
          ParameterDescription parameterDescription = new ParameterDescription();
          parameterDescription.setFullRepresentation("(");
          parameterDescriptions.add(parameterDescription);
        }

        @Override
        public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          hintFlags.put(parameterDescriptions.size(), EnumSet.noneOf(ParameterFlag.class));
          ParameterDescription parameterDescription = new ParameterDescription();
          parameterDescription.setFullRepresentation(last ? ")" : "), ");
          parameterDescriptions.add(parameterDescription);
        }

        @Override
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          PyCallableParameter parameter = parameterMap.get(param);
          if (parameter == null) {
            parameter = PyCallableParameterImpl.psi(param);
          }
          visitNonPsiParameter(parameter, first, last);
        }

        @Override
        public void visitSlashParameter(@Nullable PySlashParameter param, boolean first, boolean last) {
          hintFlags.put(parameterDescriptions.size(), EnumSet.noneOf(ParameterFlag.class));
          currentParameterIndex[0]++;
          ParameterDescription parameterDescription = new ParameterDescription(PyAstSlashParameter.TEXT, "", last);
          parameterDescriptions.add(parameterDescription);
        }

        @Override
        public void visitSingleStarParameter(@Nullable PySingleStarParameter param, boolean first, boolean last) {
          hintFlags.put(parameterDescriptions.size(), EnumSet.noneOf(ParameterFlag.class));
          currentParameterIndex[0]++;
          ParameterDescription parameterDescription = new ParameterDescription(PyAstSingleStarParameter.TEXT, "", last);
          parameterDescriptions.add(parameterDescription);
        }

        @Override
        public void visitNonPsiParameter(@NotNull PyCallableParameter parameter, boolean first, boolean last) {
          indexToNamedParameter.put(currentParameterIndex[0], parameter);
          final StringBuilder stringBuilder = new StringBuilder();
          final String name = parameter.getName();
          final String syntheticName = hasSyntheticName(parameter) ? getSyntheticName(parameter, parameterPosition[0], usedNames) : null;
          parameterPosition[0]++;
          ParameterDescription parameterDescription = new ParameterDescription(syntheticName != null ? syntheticName : name, "", last);
          if (parameter.getParameter() instanceof PyNamedParameter) {
            final String annotation = ((PyNamedParameter)parameter.getParameter()).getAnnotationValue();
            if (annotation != null) {
              String annotationText = ParamHelper.getNameInSignature(parameter) + ": " +
                                      annotation.replaceAll("\n", "").replaceAll("\\s+", " ");
              parameterDescription.setAnnotation(last ? annotationText : (annotationText + ", "));
            }
          }
          String presentableText = parameter.getPresentableText(true, context, EnumSet.of(PyTypeRendererFeature.TYPE_VAR_BOUNDS),
                                                                type -> isAnyOrUnknown(type) || type instanceof PyStructuralType);
          if (syntheticName != null) {
            presentableText = replaceNameInSignature(presentableText, parameter, syntheticName);
          }
          stringBuilder.append(presentableText);
          if (!last) stringBuilder.append(", ");
          final int hintIndex = parameterDescriptions.size();
          parameterToHintIndex.put(parameter, hintIndex);
          hintFlags.put(hintIndex, EnumSet.noneOf(ParameterFlag.class));
          currentParameterIndex[0]++;
          parameterDescription.setFullRepresentation(stringBuilder.toString());
          parameterDescriptions.add(parameterDescription);
        }
      }
    );
    return parameterDescriptions;
  }

  /**
   * Tells if parameter info shows a synthetic name for the parameter.
   * This is true for a parameter without a name, for example a parameter of {@code Callable[[int, str], None]}.
   * It is also true for an expanded tuple element, which has no PSI and a {@code __p<i>} name
   * from {@link com.jetbrains.python.psi.types.PyUnpackedTupleType#asCallableParameters()}.
   */
  private static boolean hasSyntheticName(@NotNull PyCallableParameter parameter) {
    final String name = parameter.getName();
    if (name == null) return true;
    return parameter.getParameter() == null &&
           !parameter.isPositionalContainer() &&
           !parameter.isKeywordContainer() &&
           SYNTHETIC_POSITIONAL_NAME.matcher(name).matches();
  }

  /**
   * Returns a synthetic name for the parameter.
   * <p>
   * A positional parameter gets {@code __p<i>}, where {@code i} is its 0-based position among the shown parameters.
   * If a parameter with a real name already has this name, {@code i} goes up to the next free number.
   * A positional container gets {@code __args}, and a keyword container gets {@code __kwargs}.
   * The new name goes into {@code usedNames}.
   */
  private static @NotNull String getSyntheticName(@NotNull PyCallableParameter parameter, int position, @NotNull Set<String> usedNames) {
    String name;
    if (parameter.isPositionalContainer()) {
      name = "__args";
    }
    else if (parameter.isKeywordContainer()) {
      name = "__kwargs";
    }
    else {
      int index = position;
      while (usedNames.contains("__p" + index)) {
        index++;
      }
      name = "__p" + index;
    }
    usedNames.add(name);
    return name;
  }

  private static @NotNull String replaceNameInSignature(@NotNull String presentableText,
                                                        @NotNull PyCallableParameter parameter,
                                                        @NotNull String name) {
    final String nameInSignature = ParamHelper.getNameInSignature(parameter);
    if (!presentableText.startsWith(nameInSignature)) return presentableText;
    final String prefix = parameter.isPositionalContainer() ? "*" : parameter.isKeywordContainer() ? "**" : "";
    return prefix + name + presentableText.substring(nameInSignature.length());
  }

  public static void highlightParameter(final @NotNull PyCallableParameter parameter,
                                        final @NotNull Map<PyCallableParameter, Integer> parameterToHintIndex,
                                        final @NotNull Map<Integer, EnumSet<ParameterFlag>> hintFlags,
                                        boolean mustHighlight) {
    final Integer hintIndex = parameterToHintIndex.get(parameter);
    if (mustHighlight && hintIndex != null && hintFlags.containsKey(hintIndex)) {
      hintFlags.get(hintIndex).add(ParameterFlag.HIGHLIGHT);
    }
  }

  public static @NotNull List<PyCallableParameter> getFlattenedTupleParameterComponents(@NotNull PyTupleParameter parameter) {
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
  public static int collectHighlights(final @NotNull PyCallExpression.PyArgumentsMapping mapping,
                                      final @NotNull List<PyCallableParameter> parameterList,
                                      final @NotNull Map<PyCallableParameter, Integer> parameterHintToIndex,
                                      final @NotNull Map<Integer, EnumSet<ParameterFlag>> hintFlags,
                                      final @NotNull List<PyExpression> flatArgs,
                                      int currentParamOffset) {
    final PyCallableType callableType = mapping.getCallableType();
    assert callableType != null;
    int lastParamIndex = 0;
    final Map<PyCallableParameter, Integer> parameterPositions = CollectionFactory.createCustomHashingStrategyMap(PARAMETER_STRATEGY);
    for (int i = 0; i < parameterList.size(); i++) {
      parameterPositions.putIfAbsent(parameterList.get(i), i);
    }
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
        lastParamIndex = Math.max(lastParamIndex, parameterPositions.getOrDefault(parameter, -1));
        if (parameter != null) {
          highlightParameter(parameter, parameterHintToIndex, hintFlags, mustHighlight);
        }
      }
      else if (PyCallExpressionHelper.isVariadicPositionalArgument(arg)) {
        for (PyCallableParameter parameter : mapping.getParametersMappedToVariadicPositionalArguments()) {
          lastParamIndex = Math.max(lastParamIndex, parameterPositions.getOrDefault(parameter, -1));
          highlightParameter(parameter, parameterHintToIndex, hintFlags, mustHighlight);
        }
      }
      else if (PyCallExpressionHelper.isVariadicKeywordArgument(arg)) {
        for (PyCallableParameter parameter : mapping.getParametersMappedToVariadicKeywordArguments()) {
          lastParamIndex = Math.max(lastParamIndex, parameterPositions.getOrDefault(parameter, -1));
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
            lastParamIndex = Math.max(lastParamIndex, parameterPositions.getOrDefault(parameter, -1));
            highlightParameter(parameter, parameterHintToIndex, hintFlags, mustHighlight);
          }
        }
      }
    }
    return lastParamIndex;
  }

  public static void highlightNext(final @NotNull List<PyCallableParameter> parameterList,
                                   final @NotNull Map<Integer, PyCallableParameter> indexToNamedParameter,
                                   final @NotNull Map<PyCallableParameter, Integer> parameterToHintIndex,
                                   final @NotNull Map<Integer, EnumSet<ParameterFlag>> hintFlags,
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
        highlightIndex = 0; // no args, highlight first (PY-3690)
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
                                         List<PyCallableParameter> parameters,
                                         PyCallExpression.PyArgumentsMapping mapping,
                                         Map<Integer, PyCallableParameter> indexToNamedParameter,
                                         Map<PyCallableParameter, Integer> parameterToHintIndex,
                                         Map<Integer, EnumSet<ParameterFlag>> hintFlags,
                                         int currentParamOffset) {
    final List<PyExpression> flattenedArguments = PyUtil.flattenedParensAndLists(callExpression.getArguments());
    final int lastParamIndex =
      collectHighlights(mapping, parameters, parameterToHintIndex, hintFlags, flattenedArguments, currentParamOffset);

    highlightNext(parameters, indexToNamedParameter, parameterToHintIndex, hintFlags, flattenedArguments.isEmpty(),
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

  public static @Nullable ParameterHints buildParameterHints(@NotNull Pair<PyCallExpression, PyCallableType> callAndCallee,
                                                             int currentParamOffset) {
    final PyCallExpression callExpression = callAndCallee.getFirst();
    PyPsiUtils.assertValid(callExpression);

    final TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(callExpression.getProject(), callExpression.getContainingFile());
    final PyCallableType callableType =
      PyCallExpressionHelper.expandParamSpecArgsKwargs(callExpression, callAndCallee.getSecond(), typeEvalContext);

    List<PyCallableParameter> parameters = callableType.getUnpackedParameters(typeEvalContext);
    if (parameters == null) return null;

    final PyCallExpression.PyArgumentsMapping mapping =
      PyCallExpressionHelper.mapArguments(callExpression, List.of(callExpression.getArguments()), callableType, parameters, typeEvalContext);
    if (mapping.getCallableType() == null) return null;

    final Map<Integer, PyCallableParameter> indexToNamedParameter = new HashMap<>(parameters.size());

    // param -> hint index. indexes are not contiguous, because some hints are parentheses.
    final Map<PyCallableParameter, Integer> parameterToHintIndex = CollectionFactory.createCustomHashingStrategyMap(PARAMETER_STRATEGY);
    // formatting of hints: hint index -> flags. this includes flags for parens.
    final Map<Integer, EnumSet<ParameterFlag>> hintFlags = new HashMap<>();

    final List<ParameterDescription> hintsAndAnnotations =
      buildParameterListHint(parameters, indexToNamedParameter, parameterToHintIndex, hintFlags, typeEvalContext);

    highlightParameters(callExpression, parameters, mapping, indexToNamedParameter, parameterToHintIndex, hintFlags,
                        currentParamOffset);

    return new ParameterHints(hintsAndAnnotations, hintFlags);
  }

  public static class ParameterDescription {

    private String name = "";
    private String annotation = "";
    private boolean isLast = false;
    private String fullRepresentation = "";

    public ParameterDescription() { }

    public ParameterDescription(String name, String annotation, boolean isLast) {
      this.name = name;
      this.annotation = annotation;
      this.isLast = isLast;
    }

    public void setFullRepresentation(String fullRepresentation) {
      this.fullRepresentation = fullRepresentation;
    }

    public String getAnnotation() {
      return annotation;
    }

    public void setAnnotation(String annotation) {
      this.annotation = annotation;
    }

    public String getFullRepresentation(boolean showHints) {
      StringBuilder stringBuilder = new StringBuilder();
      if (showHints) {
        if (fullRepresentation.isEmpty()) {
          stringBuilder.append(name);
          if (!annotation.isEmpty()) {
            stringBuilder.append(": ").append(annotation);
          }
        }
        else {
          stringBuilder.append(fullRepresentation);
          return stringBuilder.toString();
        }
      }
      else {
        stringBuilder.append(name);
      }
      if (!isLast) {
        stringBuilder.append(", ");
      }
      return stringBuilder.toString();
    }
  }

  public static class CallInfo {
    private final PyCallExpression call;
    private final PyCallableType callee;
    private boolean isVisible;

    public CallInfo(PyCallExpression call, PyCallableType callee, boolean isVisible) {
      this.call = call;
      this.callee = callee;
      this.isVisible = isVisible;
    }

    public void setVisible(boolean visible) {
      this.isVisible = visible;
    }

    public boolean isVisible() {
      return this.isVisible;
    }

    public Pair<PyCallExpression, PyCallableType> getCallandCalleePair() {
      return new Pair<>(call, callee);
    }
  }
}
