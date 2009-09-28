package org.jetbrains.idea.maven.utils;

public interface MavenTask {
  void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException;
}
