package de.plushnikov.intellij.plugin.action.lombok;

import de.plushnikov.intellij.plugin.handler.LombokEqualsAndHashcodeHandler;

public class LombokEqualsAndHashcodeAction extends BaseLombokAction {

  public LombokEqualsAndHashcodeAction() {
    super(new LombokEqualsAndHashcodeHandler());
  }

}
