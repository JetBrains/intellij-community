package com.intellij.pom.tree.events;

public interface ChangeInfo {
  short ADD = 0;
  short REMOVED = 1;
  short REPLACE = 2;
  short CONTENTS_CHANGED = 3;

  int getChangeType();
  int getOldLength();
}
