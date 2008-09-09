package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class RelativePathCalculator {
  private final String myShifted;
  private final String myBase;

  private String myResult;
  private boolean myRename;

  public RelativePathCalculator(final String base, final String shifted) {
    myShifted = shifted;
    myBase = base;
  }

  public void execute() {
    if (myShifted == null || myBase == null) {
      myResult = null;
      return;
    }
    if (myShifted.equals(myBase)) {
      myResult = ".";
      myRename = false;
      return;
    }
    final String[] baseParts = split(myBase);
    final String[] shiftedParts = split(myShifted);

    myRename = checkRename(baseParts, shiftedParts);

    int cnt = 0;
    while (true) {
      if ((baseParts.length <= cnt) || (shiftedParts.length <= cnt)) {
        // means that directory moved to a file or vise versa -> error
        return;
      }
      if (! baseParts[cnt].equals(shiftedParts[cnt])) {
        break;
      }
      ++ cnt;
    }

    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < (baseParts.length - cnt - 1); i++) {
      sb.append("../");
    }

    for (int i = cnt; i < shiftedParts.length; i++) {
      final String shiftedPart = shiftedParts[i];
      sb.append(shiftedPart);
      if (i < (shiftedParts.length - 1)) {
        sb.append('/');
      }
    }

    myResult = sb.toString();
  }

  public boolean isRename() {
    return myRename;
  }

  private boolean checkRename(final String[] baseParts, final String[] shiftedParts) {
    if (baseParts.length == shiftedParts.length) {
      for (int i = 0; i < baseParts.length; i++) {
        if (! baseParts[i].equals(shiftedParts[i])) {
          return i == (baseParts.length - 1);
        }
      }
    }
    return false;
  }

  public String getResult() {
    return myResult;
  }

  @Nullable
  public static String getMovedString(final String beforeName, final String afterName) {
    if ((beforeName != null) && (afterName != null) && (! beforeName.equals(afterName))) {
      final RelativePathCalculator calculator = new RelativePathCalculator(beforeName, afterName);
      calculator.execute();
      final String key = (calculator.isRename()) ? "change.file.renamed.to.text" : "change.file.moved.to.text";
      return VcsBundle.message(key, calculator.getResult());
    }
    return null;
  }

  public static String[] split(final String s) {
    return s.replace(File.separatorChar, '/').split("/");
  }
}
