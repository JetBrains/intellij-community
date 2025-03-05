package de.plushnikov.intellij.plugin.action.lombok;

import de.plushnikov.intellij.plugin.handler.LombokLoggerHandler;

public class LombokLoggerAction extends BaseLombokAction {

  public LombokLoggerAction() {
    super(new LombokLoggerHandler());
  }

}
