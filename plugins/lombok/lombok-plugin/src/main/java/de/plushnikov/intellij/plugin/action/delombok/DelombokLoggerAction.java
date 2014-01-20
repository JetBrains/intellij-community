package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import de.plushnikov.intellij.plugin.action.BaseLombokAction;
import org.jetbrains.annotations.NotNull;

public class DelombokLoggerAction extends BaseLombokAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new DelombokLoggerHandler();
  }
}