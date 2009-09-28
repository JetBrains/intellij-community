package org.jetbrains.idea.svn.config;

public interface PatternsListener {
  void onChange(String patterns, String exceptions);

  class Empty implements PatternsListener {
    public static final Empty instance = new Empty();
    public void onChange(final String patterns, final String exceptions) {
    }
  }
}
