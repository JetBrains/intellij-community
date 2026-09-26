package de.plushnikov.intellij.plugin.action.lombok;

import de.plushnikov.intellij.plugin.handler.LombokDataHandler;

public class LombokDataAction extends BaseLombokAction {

  public LombokDataAction() {
    super(new LombokDataHandler());
  }

}
