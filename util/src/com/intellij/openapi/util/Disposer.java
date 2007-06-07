/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.objectTree.ObjectNode;
import com.intellij.openapi.util.objectTree.ObjectTree;
import com.intellij.openapi.util.objectTree.ObjectTreeAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class Disposer {
  private static final ObjectTree ourTree = new ObjectTree();

  private static final ObjectTreeAction ourDisposeAction = new ObjectTreeAction() {
    public void execute(final Object each) {
      ((Disposable)each).dispose();
    }
  };
  private static boolean ourDebugMode;

  private Disposer() {
  }

  private static Map<String, Disposable> ourKeyDisposables = new WeakHashMap<String, Disposable>();

  public static void register(@NotNull Disposable parent, @NotNull Disposable child) {
    register(parent, child, null);
  }

  public static void register(@NotNull Disposable parent, @NotNull Disposable child, @Nullable final String key) {
    assert parent != child : " Cannot register to itself";

    synchronized (ourTree) {
      ourTree.register(parent, child);

      if (key == null) return;

      assert get(key) == null;
      ourKeyDisposables.put(key, child);
      register(child, new Disposable() {
        public void dispose() {
          ourKeyDisposables.remove(key);
        }
      });
    }
  }

  public static Disposable get(String key) {
    return ourKeyDisposables.get(key);
  }

  public static void dispose(Disposable disposable) {
    synchronized (ourTree) {
      ourTree.executeAll(disposable, true, ourDisposeAction);
    }
  }

  public static void disposeChildAndReplace(Disposable toDipose, Disposable toReplace) {
    synchronized (ourTree) {
      ourTree.executeChildAndReplace(toDipose, toReplace, true, ourDisposeAction);
    }
  }

  static ObjectTree getTree() {
    return ourTree;
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "HardCodedStringLiteral"})
  public static void assertIsEmpty() {
    boolean firstObject = true;

    final Set<Object> objects = ourTree.getRootObjects();
    for (Object object : objects) {
      if (object == null) continue;
      final ObjectNode objectNode = ourTree.getObject2NodeMap().get(object);
      if (objectNode == null) continue;

      if (firstObject) {
        firstObject = false;
        System.err.println("***********************************************************************************************");
        System.err.println("***                        M E M O R Y    L E A K S   D E T E C T E D                       ***");
        System.err.println("***********************************************************************************************");
        System.err.println("***                                                                                         ***");
        System.err.println("***   The following objects were not disposed: ");
      }

      System.err.println("***   " + object + " of class " + object.getClass());
      final Throwable trace = objectNode.getTrace();
      if (trace != null) {
        System.err.println("***         First seen at: ");
        trace.printStackTrace();
      }
    }

    if (!firstObject) {
      System.err.println("***                                                                                         ***");
      System.err.println("***********************************************************************************************");
    }
  }

  public static void setDebugMode(final boolean b) {
    ourDebugMode = b;
  }


  public static boolean isDebugMode() {
    return ourDebugMode;
  }
}
