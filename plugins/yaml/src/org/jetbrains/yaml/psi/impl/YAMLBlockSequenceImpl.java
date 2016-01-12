package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class YAMLBlockSequenceImpl extends YAMLSequenceImpl {
  public YAMLBlockSequenceImpl(@NotNull ASTNode node) {
    super(node);
  }
}
