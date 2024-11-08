package de.plushnikov.intellij.plugin.action.lombok;

import de.plushnikov.intellij.plugin.handler.LombokToStringHandler;

public class LombokToStringAction extends BaseLombokAction {

  public LombokToStringAction() {
    super(new LombokToStringHandler());
  }

}
