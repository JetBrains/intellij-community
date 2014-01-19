package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import de.plushnikov.intellij.plugin.action.BaseLombokAction;
import org.jetbrains.annotations.NotNull;

public class LombokLoggerAction extends BaseLombokAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new LombokLoggerHandler();
  }

}