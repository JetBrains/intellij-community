// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

public interface PatternsListener {
  void onChange(String patterns, String exceptions);

  class Empty implements PatternsListener {
    public static final Empty instance = new Empty();
    @Override
    public void onChange(final String patterns, final String exceptions) {
    }
  }
}
