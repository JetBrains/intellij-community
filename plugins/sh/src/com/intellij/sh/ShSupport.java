// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.sh.psi.ShLiteral;
import com.intellij.sh.psi.ShVariable;
import com.intellij.sh.run.ShRunConfiguration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated From now there are no plugins which use this API
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
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
}