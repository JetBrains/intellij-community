// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.signatureHelp;

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
import com.jetbrains.python.psi.PyExpression;

import java.util.ArrayList;
import java.util.List;

public final class PySignatureHelpProvider implements SignatureHelpProvider {

  public static final SignatureInfo[] EMPTY_SIGNATURE_INFOS = new SignatureInfo[0];

  @Override
  public SignatureHelpResult getSignatureHelp(PsiFile file, int offset) {
    PyArgumentList argumentList = PyParameterInfoUtils.findArgumentList(file, offset, -1);
    if (argumentList == null) {
      return null;
    }

    List<Pair<PyCallExpression, PyCallExpression.PyMarkedCallee>> signatures = PyParameterInfoUtils.findCallCandidates(argumentList);

    int currentParamOffset = PyParameterInfoUtils.findCurrentParameter(argumentList, offset, file);
    int activeParameter = findActiveParameter(argumentList, offset);

    List<SignatureInfo> signatureInfos = new ArrayList<>();
    for (Pair<PyCallExpression, PyCallExpression.PyMarkedCallee> signature : signatures) {
      ParameterHints parameterHints = PyParameterInfoUtils.buildParameterHints(signature, currentParamOffset);
      if (parameterHints == null) {
        continue;
      }

      List<String> hints = parameterHints.getHints();
      ParameterInfo[] parameterInfos = new ParameterInfo[hints.size()];
      int i = 0;
      for (String hint : hints) {
        parameterInfos[i++] = new ParameterInfo(null, hint);
      }
      String fullSignature = String.join("", hints);
      signatureInfos.add(new SignatureInfo(null, fullSignature, parameterInfos));
    }

    return new SignatureHelpResult(activeParameter, signatureInfos.toArray(EMPTY_SIGNATURE_INFOS));
  }

  private static int findActiveParameter(PyArgumentList list, int caretOffset) {
    if (list.getArguments().length == 0) {
      return 0;
    }
    int activeParameter = 0;
    for (PyExpression arg : list.getArguments()) {
      if (caretOffset <= arg.getTextRange().getEndOffset()) {
        break;
      }
      activeParameter++;
    }
    return activeParameter;
  }
}
