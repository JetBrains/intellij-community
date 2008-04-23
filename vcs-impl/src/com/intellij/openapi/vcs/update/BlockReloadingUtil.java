package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.ex.ProjectManagerEx;

public class BlockReloadingUtil {
  private BlockReloadingUtil() {
  }

  public static void block() {
    ProjectManagerEx.getInstanceEx().blockReloadingProjectOnExternalChanges();
  }

  public static void unblock() {
    ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();
  }
}
