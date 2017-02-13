/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.wrapper;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class StreamCallImpl implements MethodCall {
  private PsiMethodCallExpression myMethodCall;

  public StreamCallImpl(@NotNull PsiMethodCallExpression methodCallExpression) {
    myMethodCall = methodCallExpression;
  }

  @NotNull
  @Override
  public String getName() {
    return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
      final PsiMethod method = myMethodCall.resolveMethod();
      return method != null ? method.getName() : "";
    });
  }

  @NotNull
  @Override
  public String getArguments() {
    return myMethodCall.getArgumentList().getText();
  }
}
