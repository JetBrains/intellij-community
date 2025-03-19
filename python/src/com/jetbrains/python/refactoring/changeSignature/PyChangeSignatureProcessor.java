// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */

public class PyChangeSignatureProcessor extends ChangeSignatureProcessorBase {

  public PyChangeSignatureProcessor(Project project,
                                    PyFunction method,
                                    String newName,
                                    PyParameterInfo[] parameterInfo) {
    super(project, generateChangeInfo(method, newName, parameterInfo));
  }

  private static PyChangeInfo generateChangeInfo(PyFunction method,
                                                  String newName,
                                                  PyParameterInfo[] parameterInfo) {
    return new PyChangeInfo(method, parameterInfo, newName);
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new PyChangeSignatureUsageViewDescriptor(usages);
  }
}
