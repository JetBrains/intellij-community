// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLAlias;

/** Current implementation consists of 2 nodes: star symbol and name identifier */
public class YAMLAliasImpl extends YAMLValueImpl implements YAMLAlias {
  public YAMLAliasImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML alias";
  }
}
