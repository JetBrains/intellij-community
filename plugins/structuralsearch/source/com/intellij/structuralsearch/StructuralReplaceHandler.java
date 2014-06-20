package com.intellij.structuralsearch;

import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class StructuralReplaceHandler {
  public abstract void replace(final ReplacementInfo info);

  public void prepare
    (ReplacementInfo info) {
  }
}
