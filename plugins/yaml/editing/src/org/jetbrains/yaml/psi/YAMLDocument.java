// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi;

import org.jetbrains.annotations.Nullable;

public interface YAMLDocument extends YAMLPsiElement {
  @Nullable
  YAMLValue getTopLevelValue();
}
