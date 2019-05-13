package org.jetbrains.maven.embedder;

/**
 * @author Sergey.Anchipolevsky
 */
public interface MavenEmbedderLogger {
  void debug(CharSequence msg);

  void debug(CharSequence msg, Throwable e);

  void debug(Throwable e);

  void info(CharSequence msg);

  void info(CharSequence msg, Throwable e);

  void info(Throwable e);

  void warn(CharSequence msg);

  void warn(CharSequence msg, Throwable e);

  void warn(Throwable e);

  void error(CharSequence msg);

  void error(CharSequence msg, Throwable e);

  void error(Throwable e);
}
