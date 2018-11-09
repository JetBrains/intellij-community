// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RunnerTest {
  @Test
  public void testExtractingFiles() {
    String[] args = {"bar", "ignored=xxx;yyy;zzz/zzz", "critical=", "ignored=aaa", "baz", "critical=ccc"};
    assertThat(Runner.extractArguments(args, "ignored")).containsExactly("xxx", "yyy", "zzz/zzz", "aaa");
    assertThat(Runner.extractArguments(args, "critical")).containsExactly("ccc");
    assertThat(Runner.extractArguments(args, "unknown")).isEmpty();
  }
}