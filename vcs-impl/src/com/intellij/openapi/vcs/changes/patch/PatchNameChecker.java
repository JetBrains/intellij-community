package com.intellij.openapi.vcs.changes.patch;

import java.io.File;

public class PatchNameChecker {
  public final static int MAX = 100;
  private final String myName;
  private String myError;

  public PatchNameChecker(final String name) {
    myName = new File(name).getName();
  }

  public boolean nameOk() {
    if (myName == null || myName.length() == 0) {
      myError = "File name cannot be empty";
      return false;
    } else if (myName.length() > MAX) {
      myError = "File name length cannot exceed " + MAX + " characters";
      return false;
    }
    return true;
  }

  public String getError() {
    return myError;
  }
}
