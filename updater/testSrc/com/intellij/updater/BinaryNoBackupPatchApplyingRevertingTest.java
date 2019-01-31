// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

public class BinaryNoBackupPatchApplyingRevertingTest extends PatchApplyingRevertingTest {
  @Override
  protected boolean isBinary() {
    return true;
  }

  @Override
  protected boolean isBackup() {
    return false;
  }
}