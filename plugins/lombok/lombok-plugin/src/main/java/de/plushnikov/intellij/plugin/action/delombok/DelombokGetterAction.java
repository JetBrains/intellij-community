package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import org.jetbrains.annotations.NotNull;

public class DelombokGetterAction extends BaseCodeInsightAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new DelombokGetterHandler();
  }
}