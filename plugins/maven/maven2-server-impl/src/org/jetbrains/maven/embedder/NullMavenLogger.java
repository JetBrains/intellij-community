package org.jetbrains.maven.embedder;

/**
 * @author Sergey.Anchipolevsky
 *         Date: 28.01.2010
 */
class NullMavenLogger extends AbstractMavenLogger {
  @Override
  protected void printMessage(final int level, final String message, final Throwable throwable) {
    // do nothing
  }
}
