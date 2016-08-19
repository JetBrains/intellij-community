package org.jetbrains.idea.maven.aether;

/**
 * @author Eugene Zhuravlev
 *         Date: 12-Aug-16
 */
public interface ProgressConsumer {
  ProgressConsumer DEAF = new ProgressConsumer() {
    public void consume(String message) {
    }
  };

  void consume(String message);
}
