package org.jetbrains.idea.svn.config;

public interface TestConnectionPerformer {
  void execute(final String url);
  boolean enabled();
}
