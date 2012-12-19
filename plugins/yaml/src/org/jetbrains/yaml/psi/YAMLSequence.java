package org.jetbrains.yaml.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public interface YAMLSequence extends YAMLPsiElement {
  @NotNull
  YAMLKeyValue[] getKeysValues();
}