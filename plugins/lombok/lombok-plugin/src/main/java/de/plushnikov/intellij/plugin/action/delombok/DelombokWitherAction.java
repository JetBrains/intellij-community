package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import de.plushnikov.intellij.plugin.action.BaseLombokAction;
import de.plushnikov.intellij.plugin.processor.clazz.WitherProcessor;
import de.plushnikov.intellij.plugin.processor.field.WitherFieldProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokWitherAction extends BaseLombokAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new BaseDelombokHandler(new WitherProcessor(), new WitherFieldProcessor());
  }
}