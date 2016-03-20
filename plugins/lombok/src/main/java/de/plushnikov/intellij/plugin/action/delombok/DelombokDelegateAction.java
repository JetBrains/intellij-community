package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.field.DelegateFieldProcessor;
import de.plushnikov.intellij.plugin.processor.handler.DelegateHandler;
import de.plushnikov.intellij.plugin.processor.method.DelegateMethodProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokDelegateAction extends BaseDelombokAction {
  public DelombokDelegateAction() {
    super(createHandler());
  }

  @NotNull
  private static BaseDelombokHandler createHandler() {
    final DelegateHandler delegateHandler = new DelegateHandler();
    return new BaseDelombokHandler(
        new DelegateFieldProcessor(delegateHandler),
        new DelegateMethodProcessor(delegateHandler));
  }
}