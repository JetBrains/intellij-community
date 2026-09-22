// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;

import static com.intellij.updater.WindowsNative.HKEY_CURRENT_USER;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledOnOs(OS.WINDOWS)
@SuppressWarnings("SuspiciousPackagePrivateAccess")
class WindowsNativeTest {
  private static final int CSIDL_WINDOWS = 0x0024;

  private final String baseKey = "Software\\JetBrains\\" + WindowsNativeTest.class.getSimpleName() + '-' + ProcessHandle.current().pid();
  private WindowsNative nativeApi;

  @BeforeEach
  void setUp() {
    nativeApi = WindowsNative.supplier().get();
    assertThat(nativeApi).isNotNull();
  }

  @AfterEach
  void tearDown() {
    if (nativeApi == null) return;
    for (var key : new String[]{baseKey + "\\from", baseKey + "\\to", baseKey}) {
      try {
        nativeApi.registryDeleteKey(HKEY_CURRENT_USER, key);
      }
      catch (WindowsNative.NativeException ignored) { }
    }
  }

  @Test
  void registryRoundTrip() throws WindowsNative.NativeException {
    var from = baseKey + "\\from";
    var to = baseKey + "\\to";

    nativeApi.registryCreateKey(HKEY_CURRENT_USER, from);
    nativeApi.registrySetString(HKEY_CURRENT_USER, from, "", "C:\\Program Files\\JetBrains\\IDE");
    nativeApi.registrySetString(HKEY_CURRENT_USER, from, "MenuFolder", "JetBrains");
    assertThat(nativeApi.registryGetString(HKEY_CURRENT_USER, from, "")).isEqualTo("C:\\Program Files\\JetBrains\\IDE");
    assertThat(nativeApi.registryGetString(HKEY_CURRENT_USER, from, "missing")).isNull();
    assertThat(nativeApi.registrySubKeys(HKEY_CURRENT_USER, baseKey)).containsExactly("from");

    nativeApi.registryCreateKey(HKEY_CURRENT_USER, to);
    nativeApi.registryCopyValues(HKEY_CURRENT_USER, from, to);
    nativeApi.registryDeleteKey(HKEY_CURRENT_USER, from);

    assertThat(nativeApi.registrySubKeys(HKEY_CURRENT_USER, baseKey)).containsExactly("to");
    assertThat(nativeApi.registryGetString(HKEY_CURRENT_USER, to, "")).isEqualTo("C:\\Program Files\\JetBrains\\IDE");
    assertThat(nativeApi.registryGetString(HKEY_CURRENT_USER, to, "MenuFolder")).isEqualTo("JetBrains");
  }

  @Test
  void missingKey() throws WindowsNative.NativeException {
    assertThat(nativeApi.registrySubKeys(HKEY_CURRENT_USER, baseKey + "\\missing")).isEmpty();
    assertThat(nativeApi.registryGetString(HKEY_CURRENT_USER, baseKey + "\\missing", "")).isNull();
  }

  @Test
  void windowsFolder() {
    assertThat(nativeApi.folderPath(CSIDL_WINDOWS)).isEqualTo(Path.of(System.getenv("SystemRoot")));
  }
}
