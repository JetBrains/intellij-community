package org.jetbrains.idea.maven.utils;

import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;

public abstract class MavenUpdate extends Update {
  public MavenUpdate(@NonNls Object identity) {
    super(identity);
  }

  public final void run() {
    try {
      doRun();
    }
    catch (Throwable e) {
      e.printStackTrace();
      MavenLog.LOG.error(e);
    }
  }

  public abstract void doRun();
}
