package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateSetterHandler;

/**
 * Action group which contains Generate... actions
 * Available in the Java code editor context only
 * @author Alexey Kudravtsev
 */ 
public class GenerateSetterAction extends BaseGenerateAction {
  public GenerateSetterAction() {
    super(new GenerateSetterHandler());
  }

}