package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;

public abstract class BaseLombokAction extends BaseGenerateAction {

  protected BaseLombokAction(CodeInsightActionHandler handler) {
    super(handler);
  }


}
