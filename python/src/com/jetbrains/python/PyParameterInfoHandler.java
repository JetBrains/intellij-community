// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.parameterInfo.ParameterFlag;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.codeInsight.parameterInfo.ParameterHints;
import com.jetbrains.python.codeInsight.parameterInfo.PyParameterInfoUtils;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.types.PyCallableType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public class PyParameterInfoHandler implements ParameterInfoHandler<PyArgumentList, Pair<PyCallExpression, PyCallableType>> {

  @Override
  @Nullable
  public PyArgumentList findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
    PsiFile file = context.getFile();
    int offset = context.getOffset();
    final PyArgumentList argumentList = PyParameterInfoUtils.findArgumentList(file, offset, -1);

    List<Pair<PyCallExpression, PyCallableType>> parameterInfos = PyParameterInfoUtils.findCallCandidates(argumentList);
    if (parameterInfos != null) {
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
    if (context instanceof ParameterInfoUIContextEx) {
      final ParameterInfoUIContextEx pic = (ParameterInfoUIContextEx)context;
      EnumSet[] flags = new EnumSet[parameterHints.getFlags().size()];
      for (int i = 0; i < flags.length; i++) {
        EnumSet<ParameterInfoUIContextEx.Flag> paramUIFlags = EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class);
        EnumSet<ParameterFlag> paramsFlags = parameterHints.getFlags().get(i);
        for (ParameterFlag flag : paramsFlags) {
          switch (flag) {
            case HIGHLIGHT:
              paramUIFlags.add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
              break;
            case STRIKEOUT:
              paramUIFlags.add(ParameterInfoUIContextEx.Flag.STRIKEOUT);
              break;
            case DISABLE:
              paramUIFlags.add(ParameterInfoUIContextEx.Flag.DISABLE);
          }
        }
        flags[i] = paramUIFlags;
      }
      if (hints.length < 1) {
        hints = new String[]{getNoParamsMsg()};
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
        signatureBuilder.append(XmlStringUtil.escapeString(getNoParamsMsg()));
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
