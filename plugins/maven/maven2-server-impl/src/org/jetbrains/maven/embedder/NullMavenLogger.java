package org.jetbrains.maven.embedder;

/**
 * @author Sergey.Anchipolevsky
 */
class NullMavenLogger extends AbstractMavenLogger {
  @Override
  protected void printMessage(final int level, final String message, final Throwable throwable) {
    // do nothing
  }
}
