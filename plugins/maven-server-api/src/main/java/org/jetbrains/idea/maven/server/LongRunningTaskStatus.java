// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import java.io.Serializable;
import java.util.Objects;

public final class LongRunningTaskStatus implements Serializable {
  private final int total;
  private final int finished;

  public LongRunningTaskStatus(int total, int finished) {
    this.total = total;
    this.finished = finished;
  }

  public int total() { return total; }

  public int finished() { return finished; }

  public double fraction() {
    int t = total;
    int f = finished;
    if (t == 0) return 0;
    return ((double)f) / t;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    LongRunningTaskStatus that = (LongRunningTaskStatus)obj;
    return this.total == that.total &&
           this.finished == that.finished;
  }

  @Override
  public int hashCode() {
    return Objects.hash(total, finished);
  }

  @Override
  public String toString() {
    return "LongRunningTaskStatus[" +
           "total=" + total + ", " +
           "finished=" + finished + ']';
  }
}
