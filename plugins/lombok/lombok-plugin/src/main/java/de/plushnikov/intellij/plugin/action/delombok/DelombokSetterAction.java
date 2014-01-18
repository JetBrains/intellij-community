package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import org.jetbrains.annotations.NotNull;

public class DelombokSetterAction extends BaseCodeInsightAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new DelombokSetterHandler();
  }
}