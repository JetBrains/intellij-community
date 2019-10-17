// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiReference;
import com.intellij.sh.psi.ShVariable;
import com.intellij.sh.run.ShRunConfiguration;
import com.intellij.sh.run.ShRunConfigurationProfileState;
import org.jetbrains.annotations.NotNull;

public interface ShSupport {
  static ShSupport getInstance() { return ServiceManager.getService(ShSupport.class); }

  boolean isExternalFormatterEnabled();

  boolean isRenameEnabled();

  @NotNull
  RunProfileState createRunProfileState(@NotNull Executor executor,
                                        @NotNull ExecutionEnvironment environment,
                                        @NotNull ShRunConfiguration configuration);

  @NotNull
  PsiReference[] getVariableReferences(@NotNull ShVariable v);
  
  
  class Impl implements ShSupport {
    @Override
    public boolean isExternalFormatterEnabled() { return true; }

    @Override
    public boolean isRenameEnabled() { return true; }

    @NotNull
    @Override
    public RunProfileState createRunProfileState(@NotNull Executor executor,
                                                 @NotNull ExecutionEnvironment environment,
                                                 @NotNull ShRunConfiguration configuration) {
      return new ShRunConfigurationProfileState(environment.getProject(), configuration);
    }

    @NotNull
    @Override
    public PsiReference[] getVariableReferences(@NotNull ShVariable v) {
      return PsiReference.EMPTY_ARRAY;
    }
  }
}