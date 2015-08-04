/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new PyChangeSignatureUsageViewDescriptor(usages);
  }
}
