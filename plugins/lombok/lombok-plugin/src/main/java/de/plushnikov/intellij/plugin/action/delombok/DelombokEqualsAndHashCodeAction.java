package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import de.plushnikov.intellij.plugin.action.BaseLombokAction;
import de.plushnikov.intellij.plugin.processor.clazz.EqualsAndHashCodeProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokEqualsAndHashCodeAction extends BaseLombokAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new BaseDelombokHandler(new EqualsAndHashCodeProcessor());
  }
}