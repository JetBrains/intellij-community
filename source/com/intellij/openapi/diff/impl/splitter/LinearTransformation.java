package com.intellij.openapi.diff.impl.splitter;

class LinearTransformation implements Transformation {
  private final int myK;
  private final int myX0;

  public LinearTransformation(int offset, int lineHeight) {
    myK = lineHeight;
    myX0 = -offset;
  }

  public int transform(int line) {
    return linear(line, myX0, myK);
  }

  private static int linear(int x, int x0, int k) {
    return k*x + x0;
  }

  public static int oneToOne(int x, int x0, Interval range) {
    if (range.getLength() == 0) return range.getStart();
    return range.getStart() + Math.min(x - x0, range.getLength() - 1);
  }
}
