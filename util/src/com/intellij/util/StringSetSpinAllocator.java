package com.intellij.util;

import java.util.HashSet;
import java.util.Set;

public class StringSetSpinAllocator {

  private StringSetSpinAllocator() {
  }

  private static class Creator implements SpinAllocator.ICreator<Set<String>> {
    public Set<String> createInstance() {
      return new HashSet<String>();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<Set<String>> {
    public void disposeInstance(final Set<String> instance) {
      instance.clear();
    }
  }

  private static SpinAllocator<Set<String>> myAllocator =
    new SpinAllocator<Set<String>>(new Creator(), new Disposer());

  public static Set<String> alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(Set<String> instance) {
    myAllocator.dispose(instance);
  }
}
