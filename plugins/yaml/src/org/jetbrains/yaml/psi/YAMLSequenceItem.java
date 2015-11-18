package org.jetbrains.yaml.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public interface YAMLSequenceItem extends YAMLPsiElement {
  @NotNull
  YAMLKeyValue[] getKeysValues();
}