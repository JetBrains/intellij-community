// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.PomTarget;
import com.intellij.psi.ContributedReferenceHost;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface YAMLKeyValue extends YAMLPsiElement, ContributedReferenceHost, PsiNamedElement, PomTarget {
  @Contract(pure = true)
  @Nullable
  PsiElement getKey();

  @Contract(pure = true)
  @NlsSafe
  @NotNull
  String getKeyText();

  @Contract(pure = true)
  @Nullable
  YAMLValue getValue();

  @Contract(pure = true)
  @NlsSafe
  @NotNull
  String getValueText();

  @Contract(pure = true)
  @Nullable
  YAMLMapping getParentMapping();

  /**
   * Checks whether this entry uses the explicit key notation, that is, the key is introduced by the
   * {@code ?} ("question mark") mapping-key indicator:
   * <pre>{@code
   * ? - complex
   *   - key
   * : value
   * }</pre>
   * Ordinary entries written as {@code key: value} (an implicit key) return {@code false}.
   *
   * @return {@code true} if this key-value pair begins with the {@code ?} explicit key indicator
   */
  @Contract(pure = true)
  boolean isExplicitKey();

  void setValue(@NotNull YAMLValue value);
}
