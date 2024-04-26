// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunnerTest {
  @Test void extractingArgs() {
    var args = new String[]{"bar", "ignored=xxx;yyy;zzz/zzz", "critical=", "ignored=aaa", "baz", "critical=ccc"};
    assertThat(Runner.extractArguments(args, "ignored")).containsExactly("xxx", "yyy", "zzz/zzz", "aaa");
    assertThat(Runner.extractArguments(args, "critical")).containsExactly("ccc");
    assertThat(Runner.extractArguments(args, "unknown")).isEmpty();
  }
}
