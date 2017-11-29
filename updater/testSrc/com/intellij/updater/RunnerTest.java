/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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