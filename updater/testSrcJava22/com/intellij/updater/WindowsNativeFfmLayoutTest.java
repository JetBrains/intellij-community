// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsNativeFfmLayoutTest {
  /** {@code RM_PROCESS_INFO} is 668 bytes on Windows x64 and ARM64. */
  private static final long RM_PROCESS_INFO_SIZE = 668L;

  @Test
  void processInfoLayoutSize() {
    assertThat(WindowsNative.RM_PROCESS_INFO.byteSize()).isEqualTo(RM_PROCESS_INFO_SIZE);
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void nativeHelpersLoadOnWindows() {
    assertThat(WindowsNative.supplier().get()).isNotNull();
  }

  @Test
  void minFeatureIsTwentyTwo() {
    assertThat(WindowsNative.MIN_FEATURE).isEqualTo(22);
  }
}