// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi;

import org.jetbrains.annotations.Nullable;

/** See <a href="http://www.yaml.org/spec/1.2/spec.html#id2785586">6.9.2. Node Anchors</a> */
public interface YAMLAnchor extends YAMLPsiElement {
  /** @return sub-tree YAML value marked by this anchor */
  @Nullable
  YAMLValue getMarkedValue();
}
