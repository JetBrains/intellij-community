package de.plushnikov.intellij.plugin.action.lombok;

import de.plushnikov.intellij.plugin.handler.LombokGetterHandler;

public class LombokGetterAction extends BaseLombokAction {

  public LombokGetterAction() {
    super(new LombokGetterHandler());
  }

}
