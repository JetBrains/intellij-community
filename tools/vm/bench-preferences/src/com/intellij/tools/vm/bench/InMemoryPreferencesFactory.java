// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.vm.bench;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * The Java preferences of an IDE that a bench run starts: an empty user root and an empty system root in the memory of
 * the JVM. The IDE then reads no preference of the host, such as the JetBrains Account login of {@code JBAManager}, and its
 * writes end with the JVM.
 * <p>
 * A run selects it with {@code -Djava.util.prefs.PreferencesFactory} and finds it on the boot class path.
 */
public final class InMemoryPreferencesFactory implements PreferencesFactory {
  private static final Preferences USER_ROOT = new Node(null, "");
  private static final Preferences SYSTEM_ROOT = new Node(null, "");

  @Override
  public Preferences userRoot() {
    return USER_ROOT;
  }

  @Override
  public Preferences systemRoot() {
    return SYSTEM_ROOT;
  }

  /**
   * One node. {@link AbstractPreferences} keeps the children that a caller made, and it calls each method here under the
   * lock of the node.
   */
  private static final class Node extends AbstractPreferences {
    private final Map<String, String> values = new HashMap<>();

    Node(Node parent, String name) {
      super(parent, name);
    }

    @Override
    protected void putSpi(String key, String value) {
      values.put(key, value);
    }

    @Override
    protected String getSpi(String key) {
      return values.get(key);
    }

    @Override
    protected void removeSpi(String key) {
      values.remove(key);
    }

    @Override
    protected void removeNodeSpi() {
      values.clear();
    }

    @Override
    protected String[] keysSpi() {
      return values.keySet().toArray(new String[0]);
    }

    @Override
    protected String[] childrenNamesSpi() {
      return new String[0];
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
      return new Node(this, name);
    }

    @Override
    protected void syncSpi() {
    }

    @Override
    protected void flushSpi() {
    }
  }
}
