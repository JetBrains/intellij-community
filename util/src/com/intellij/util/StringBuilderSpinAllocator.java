package com.intellij.util;

/**
 * StringBuilderSpinAllocator reuses StringBuilder instances performing non-blocking allocation and dispose.
 */
public class StringBuilderSpinAllocator {

  private static class Creator implements SpinAllocator.ICreator<StringBuilder> {
    public StringBuilder createInstance() {
      return new StringBuilder();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<StringBuilder> {
    public void disposeInstance(final StringBuilder instance) {
      instance.setLength(0);
      if( instance.capacity() > 1024 ) {
        instance.trimToSize();
      }
    }
  }

  private static SpinAllocator<StringBuilder> myAllocator =
    new SpinAllocator<StringBuilder>(new Creator(), new Disposer());

  public static StringBuilder alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(StringBuilder instance) {
    myAllocator.dispose(instance);
  }
}
