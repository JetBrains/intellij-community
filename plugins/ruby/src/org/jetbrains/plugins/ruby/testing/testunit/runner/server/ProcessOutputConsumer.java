package org.jetbrains.plugins.ruby.testing.testunit.runner.server;

import org.jetbrains.plugins.ruby.testing.testunit.runner.GeneralTestEventsProcessor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.Disposable;

/**
 * @author Roman Chernyatchik
 */
public interface ProcessOutputConsumer extends Disposable {
  void addProcessor(final GeneralTestEventsProcessor processor);
  void process(final String text, final Key outputType);
}
 