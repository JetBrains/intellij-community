package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import de.plushnikov.intellij.plugin.action.BaseLombokAction;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokConstructorAction extends BaseLombokAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new BaseDelombokHandler(new AllArgsConstructorProcessor(), new NoArgsConstructorProcessor(), new RequiredArgsConstructorProcessor());
  }
}