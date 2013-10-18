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

  protected PyChangeSignatureProcessor(Project project,
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

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new PyChangeSignatureUsageViewDescriptor(usages);
  }
}
