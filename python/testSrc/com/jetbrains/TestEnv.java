/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * @author traff
 */
public enum TestEnv {

  WINDOWS(() -> SystemInfo.isWindows), LINUX(() -> SystemInfo.isLinux), MAC(() -> SystemInfo.isMac);

  @NotNull
  private final Supplier<Boolean> myThisOs;

  TestEnv(@NotNull final Supplier<Boolean> isThisOs) {
    myThisOs = isThisOs;
  }

  public boolean isThisOs() {
    return myThisOs.get();
  }
}
