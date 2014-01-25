package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import de.plushnikov.intellij.plugin.action.BaseLombokAction;
import de.plushnikov.intellij.plugin.processor.clazz.ValueExperimentalProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.ValueProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokValueAction extends BaseLombokAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new BaseDelombokHandler(new ValueProcessor(), new ValueExperimentalProcessor());
  }
}