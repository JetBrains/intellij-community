package de.plushnikov.intellij.plugin.action.lombok;

import de.plushnikov.intellij.plugin.handler.LombokSetterHandler;

public class LombokSetterAction extends BaseLombokAction {

  public LombokSetterAction() {
    super(new LombokSetterHandler());
  }

}
