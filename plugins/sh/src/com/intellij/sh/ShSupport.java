// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.sh.codeInsight.ShFunctionReference;
import com.intellij.sh.psi.ShLiteral;
import com.intellij.sh.psi.ShString;
import com.intellij.sh.psi.ShVariable;
import com.intellij.sh.run.ShRunConfiguration;
import com.intellij.sh.run.ShRunConfigurationProfileState;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ShSupport {
  ExtensionPointName<ShSupport> EP_NAME = ExtensionPointName.create("com.intellij.sh.shSupport");

  @NotNull
  static ShSupport getInstance() {
    return EP_NAME.findExtensionOrFail(ShSupport.class);
  }

  boolean isExternalFormatterEnabled();

  boolean isRenameEnabled();

  @NotNull
  RunProfileState createRunProfileState(@NotNull Executor executor,
                                        @NotNull ExecutionEnvironment environment,
                                        @NotNull ShRunConfiguration configuration);

  PsiReference @NotNull [] getVariableReferences(@NotNull ShVariable v);

  PsiReference @NotNull [] getLiteralReferences(@NotNull ShLiteral o);

  /**
   * Retrieve the name of the literal, if it's used as a variable declaration.
   *
   * @param l the literal
   * @return The name of the literal, if it's used as a variable declaration. Otherwise {@code null} is returned.
   * @see com.intellij.psi.PsiNameIdentifierOwner
   */
  @Nullable
  String getName(@NotNull ShLiteral l);

  /**
   * Retrieve the name identifier of this literal, if it's used as a variable declaration.
   *
   * @param l The literal
   * @return A non-null element representing the name if the literal is used as a variable declaration. Otherwise {@code null} is returned.
   * @see com.intellij.psi.PsiNameIdentifierOwner
   */
  @Nullable
  PsiElement getNameIdentifier(@NotNull ShLiteral l);

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

    @Override
    public PsiReference @NotNull [] getVariableReferences(@NotNull ShVariable v) {
      return PsiReference.EMPTY_ARRAY;
    }

    @Override
    public PsiReference @NotNull [] getLiteralReferences(@NotNull ShLiteral o) {
      return o instanceof ShString || o.getWord() != null
             ? ArrayUtil.prepend(new ShFunctionReference(o), ReferenceProvidersRegistry.getReferencesFromProviders(o))
             : PsiReference.EMPTY_ARRAY;
    }

    @Override
    public @Nullable String getName(@NotNull ShLiteral l) {
      return null;
    }

    @Override
    public @Nullable PsiElement getNameIdentifier(@NotNull ShLiteral l) {
      return null;
    }
  }
}