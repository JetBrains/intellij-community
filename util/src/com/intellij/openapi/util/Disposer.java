/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.objectTree.ObjectTree;
import com.intellij.openapi.util.objectTree.ObjectTreeAction;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class Disposer {

  private static final ObjectTree ourTree = new ObjectTree();

  private static final ObjectTreeAction ourDisposeAction = new ObjectTreeAction() {
    public void execute(final Object each) {
      ((Disposable)each).dispose();
    }
  };

  private Disposer() {
  }

  public static void register(@NotNull Disposable parent, @NotNull Disposable child) {
    assert parent != child : " Cannot register to intself";

    ourTree.register(parent, child);
  }

  public static void dispose(Disposable disposable) {
    ourTree.executeAll(disposable, true, ourDisposeAction);
  }

  public static void disposeChildAndReplace(Disposable toDipose, Disposable toReplace) {
    ourTree.executeChildAndReplace(toDipose, toReplace, true, ourDisposeAction);
  }

  public static boolean isRegistered(Disposable aDisposable) {
    return ourTree.isRegistered(aDisposable);
  }

  public static boolean isRoot(Disposable disposable) {
    return ourTree.isRoot(disposable);
  }

  static ObjectTree getTree() {
    return ourTree;
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "HardCodedStringLiteral"})
  public static void assertIsEmpty() {
    final Set objects = ourTree.getRootObjects();
    if (!objects.isEmpty()) {
      System.err.println("***********************************************************************************************");
      System.err.println("***                        M E M O R Y    L E A K S   D E T E C T E D                       ***");
      System.err.println("***********************************************************************************************");
      System.err.println("***                                                                                         ***");
      System.err.println("***   The following objects were not disposed: ");

      for (Object object : objects) {
        System.err.println("***   " + object + " of class " + object.getClass());
      }

      System.err.println("***                                                                                         ***");
      System.err.println("***********************************************************************************************");
    }
  }
}
