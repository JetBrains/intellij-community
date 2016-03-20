package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.clazz.WitherProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.field.WitherFieldProcessor;

public class DelombokWitherAction extends BaseDelombokAction {
  public DelombokWitherAction() {
    super(new BaseDelombokHandler(new WitherProcessor(new WitherFieldProcessor(new RequiredArgsConstructorProcessor())),
        new WitherFieldProcessor(new RequiredArgsConstructorProcessor())));
  }
}