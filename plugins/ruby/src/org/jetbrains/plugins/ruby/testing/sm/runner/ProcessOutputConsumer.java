package org.jetbrains.plugins.ruby.testing.sm.runner;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;

/**
 * @author Roman Chernyatchik
 */
public interface ProcessOutputConsumer extends Disposable {
  void addProcessor(final GeneralTestEventsProcessor processor);
  void process(final String text, final Key outputType);
}
 