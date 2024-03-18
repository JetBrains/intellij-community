// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.parameterInfo.ParameterFlag;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtilRt;
import com.jetbrains.python.codeInsight.parameterInfo.ParameterHints;
import com.jetbrains.python.codeInsight.parameterInfo.PyParameterInfoUtils;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.types.PyCallableType;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public final class PyParameterInfoHandler implements ParameterInfoHandler<PyArgumentList, Pair<PyCallExpression, PyCallableType>> {
  private static final int MY_PARAM_LENGTH_LIMIT = 50;
  private static final int MAX_PARAMETER_INFO_TO_SHOW = 20;

  private static final EnumMap<ParameterFlag, ParameterInfoUIContextEx.Flag> PARAM_FLAG_TO_UI_FLAG = new EnumMap<>(Map.of(
    ParameterFlag.HIGHLIGHT, ParameterInfoUIContextEx.Flag.HIGHLIGHT,
    ParameterFlag.DISABLE, ParameterInfoUIContextEx.Flag.DISABLE,
    ParameterFlag.STRIKEOUT, ParameterInfoUIContextEx.Flag.STRIKEOUT
  ));

  @Override
  @Nullable
  public PyArgumentList findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
    PsiFile file = context.getFile();
    int offset = context.getOffset();
    final PyArgumentList argumentList = PyParameterInfoUtils.findArgumentList(file, offset, -1);

    List<Pair<PyCallExpression, PyCallableType>> parameterInfos = PyParameterInfoUtils.findCallCandidates(argumentList);
    if (parameterInfos != null) {
      if (parameterInfos.size() > MAX_PARAMETER_INFO_TO_SHOW) {
        parameterInfos = parameterInfos.subList(0, MAX_PARAMETER_INFO_TO_SHOW);
      }
      Object[] infoArr = parameterInfos.toArray();
      context.setItemsToShow(infoArr);
      return argumentList;
    }

    return null;
  }

  @Override
  public void showParameterInfo(@NotNull PyArgumentList element, @NotNull CreateParameterInfoContext context) {
    context.showHint(element, element.getTextOffset(), this);
  }

  @Override
  @Nullable
  public PyArgumentList findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
    return PyParameterInfoUtils.findArgumentList(context.getFile(), context.getOffset(), context.getParameterListStart());
  }

  /*
   <b>Note: instead of parameter index, we directly store parameter's offset for later use.</b><br/>
   We cannot store an index since we cannot determine what is an argument until we actually map arguments to parameters.
   This is because a tuple in arguments may be a whole argument or map to a tuple parameter.
   */
  @Override
  public void updateParameterInfo(@NotNull PyArgumentList argumentList, @NotNull UpdateParameterInfoContext context) {
    final int allegedCursorOffset = context.getOffset(); // this is already shifted backwards to skip spaces

    if (!argumentList.getTextRange().contains(allegedCursorOffset) && argumentList.getText().endsWith(")")) {
      context.removeHint();
      return;
    }

    final PsiFile file = context.getFile();
    int offset = PyParameterInfoUtils.findCurrentParameter(argumentList, allegedCursorOffset, file);

    context.setCurrentParameter(offset);
  }

  @Override
  public void updateUI(@NotNull Pair<PyCallExpression, PyCallableType> callAndCallee, @NotNull ParameterInfoUIContext context) {
    final int currentParamOffset = context.getCurrentParameterIndex(); // in Python mode, we get an offset here, not an index!
    ParameterHints parameterHints = PyParameterInfoUtils.buildParameterHints(callAndCallee, currentParamOffset);
    if (parameterHints == null) return;

    String[] hints = ArrayUtilRt.toStringArray(parameterHints.getHints());
    String[] annotations = ArrayUtilRt.toStringArray(parameterHints.getAnnotations());
    if (context instanceof ParameterInfoUIContextEx) {
      //noinspection unchecked
      EnumSet<ParameterInfoUIContextEx.Flag>[] flags = new EnumSet[parameterHints.getFlags().size()];
      for (int i = 0; i < flags.length; i++) {
        EnumSet<ParameterFlag> curFlags = parameterHints.getFlags().get(i);
        if (!curFlags.contains(ParameterFlag.HIGHLIGHT) && i < hints.length && hints[i].length() > MY_PARAM_LENGTH_LIMIT &&
            i < annotations.length) {
          String annotation = annotations[i];
          if (!annotation.isEmpty() && annotation.length() < hints[i].length()) {
            hints[i] = annotation;
          }
        }
        flags[i] = StreamEx.of(parameterHints.getFlags().get(i))
          .map(PARAM_FLAG_TO_UI_FLAG::get)
          .collect(MoreCollectors.toEnumSet(ParameterInfoUIContextEx.Flag.class));
      }
      if (hints.length == 0) {
        hints = new String[]{getNoParamsMsg()};
        //noinspection unchecked
        flags = new EnumSet[]{EnumSet.of(ParameterInfoUIContextEx.Flag.DISABLE)};
      }

      ((ParameterInfoUIContextEx)context).setupUIComponentPresentation(hints, flags, context.getDefaultParameterColor());
    }
    else { // fallback, no highlight
      final StringBuilder signatureBuilder = new StringBuilder();
      if (hints.length == 0) {
        signatureBuilder.append(getNoParamsMsg());
      }
      else {
        for (String s : hints) signatureBuilder.append(s);
      }
      context.setupUIComponentPresentation(
        signatureBuilder.toString(), -1, 0, false, false, false, context.getDefaultParameterColor()
      );
    }
  }

  private static String getNoParamsMsg() {
    return CodeInsightBundle.message("parameter.info.no.parameters");
  }
}
