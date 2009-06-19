package com.intellij.execution.testframework.sm.runner;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;

/**
 * @author Roman Chernyatchik
 */
public interface ProcessOutputConsumer extends Disposable {
  void setProcessor(GeneralTestEventsProcessor processor);
  void process(String text, Key outputType);
}
 