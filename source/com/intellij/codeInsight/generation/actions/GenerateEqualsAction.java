package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateEqualsHandler;

/**
 * @author dsl
 */
public class GenerateEqualsAction extends BaseGenerateAction {
  public GenerateEqualsAction() {
    super(new GenerateEqualsHandler());
  }
}
