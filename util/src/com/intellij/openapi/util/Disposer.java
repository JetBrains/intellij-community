/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.objectTree.ObjectNode;
import com.intellij.openapi.util.objectTree.ObjectTree;
import com.intellij.openapi.util.objectTree.ObjectTreeAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@SuppressWarnings({"SSBasedInspection"})
public class Disposer {
  private static final ObjectTree<Disposable> ourTree = new ObjectTree<Disposable>();

  private static final ObjectTreeAction<Disposable> ourDisposeAction = new ObjectTreeAction<Disposable>() {
    public void execute(final Disposable each) {
      each.dispose();
    }

    public void beforeTreeExecution(final Disposable parent) {
      if (parent instanceof Disposable.Parent) {
        ((Disposable.Parent)parent).beforeTreeDispose();
      }
    }
  };

  private static boolean ourDebugMode;

  private Disposer() {
  }

  private static final Map<String, Disposable> ourKeyDisposables = new WeakHashMap<String, Disposable>();

  public static void register(@NotNull Disposable parent, @NotNull Disposable child) {
    register(parent, child, null);
  }

  public static void register(@NotNull Disposable parent, @NotNull Disposable child, @NonNls @Nullable final String key) {
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

  public static boolean isDisposed(Disposable disposable) {
    return !getTree().getObject2NodeMap().containsKey(disposable);
  }

  public static Disposable get(String key) {
    return ourKeyDisposables.get(key);
  }

  public static void dispose(Disposable disposable) {
    dispose(disposable, true);
  }

  public static void dispose(Disposable disposable, boolean processUnregistered) {
    synchronized (ourTree) {
      ourTree.executeAll(disposable, true, ourDisposeAction, processUnregistered);
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

    final Set<Disposable> objects = ourTree.getRootObjects();
    for (Disposable object : objects) {
      if (object == null) continue;
      final ObjectNode<Disposable> objectNode = ourTree.getObject2NodeMap().get(object);
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
