// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.signatureHelp;

import com.intellij.codeInsight.parameterInfo.ParameterFlag;
import com.intellij.codeInsight.signatureHelp.ParameterInfo;
import com.intellij.codeInsight.signatureHelp.SignatureHelpProvider;
import com.intellij.codeInsight.signatureHelp.SignatureHelpResult;
import com.intellij.codeInsight.signatureHelp.SignatureInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.codeInsight.parameterInfo.ParameterHints;
import com.jetbrains.python.codeInsight.parameterInfo.PyParameterInfoUtils;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.types.PyCallableType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public final class PySignatureHelpProvider implements SignatureHelpProvider {

  @Override
  public SignatureHelpResult getSignatureHelp(PsiFile file, int offset) {
    PyArgumentList argumentList = PyParameterInfoUtils.findArgumentList(file, offset, -1);
    if (argumentList == null || !argumentList.getTextRange().contains(offset)) {
      return null;
    }

    List<Pair<PyCallExpression, PyCallableType>> signatures = PyParameterInfoUtils.findCallCandidates(argumentList);

    int currentParamOffset = PyParameterInfoUtils.findCurrentParameter(argumentList, offset, file);

    List<SignatureInfo> signatureInfos = new ArrayList<>();
    for (Pair<PyCallExpression, PyCallableType> signature : signatures) {
      ParameterHints parameterHints = PyParameterInfoUtils.buildParameterHints(signature, currentParamOffset);
      if (parameterHints == null) {
        continue;
      }

      List<String> hints = parameterHints.getHints();

      List<ParameterInfo> parameterInfos = new ArrayList<>();
      for (String hint : hints) {
        parameterInfos.add(new ParameterInfo(null, hint));
      }
      String fullSignature = String.join("", hints);
      int highlightedParamIndex = findHighlightedParamIndex(parameterHints.getFlags());
      signatureInfos.add(new SignatureInfo(null, fullSignature, parameterInfos, highlightedParamIndex));
    }

    return new SignatureHelpResult(signatureInfos);
  }

  private static int findHighlightedParamIndex(Map<Integer, EnumSet<ParameterFlag>> flags) {
    for (int i = 0; i < flags.size(); i++) {
      if (flags.containsKey(i)) {
        EnumSet<ParameterFlag> parameterFlags = flags.get(i);
        if (parameterFlags.contains(ParameterFlag.HIGHLIGHT)) {
          return i;
        }
      }
    }
    return -1;
  }

}
