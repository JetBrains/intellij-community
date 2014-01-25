package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;

public class DelombokConstructorAction extends BaseDelombokAction {

  public DelombokConstructorAction() {
    super(new BaseDelombokHandler(new AllArgsConstructorProcessor(), new NoArgsConstructorProcessor(), new RequiredArgsConstructorProcessor()));
  }

}