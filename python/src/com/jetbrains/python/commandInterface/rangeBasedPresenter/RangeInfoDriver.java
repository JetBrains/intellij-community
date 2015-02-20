/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.commandInterface.rangeBasedPresenter;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Driver that knows how to getCommandLineInfo pack of chunks into chunk info.
 *
 * @author Ilya.Kazakevich
 */
public interface RangeInfoDriver {
  /**
   * Parses command line text into executor and pack of range infos.
   * There <strong>always</strong> should be at least one range info and the last one is almost always {@link RangeInfo#TERMINATION_RANGE).
   *
   * @param commandLineText command line text to parse.
   * @return pair or executor (the one that knows how to execute command line) and pack of range infos.
   * <strong>Warning! </strong> :Executor could be null if command can't be executed
   */
  @NotNull
  Pair<Executor, List<RangeInfo>> getCommandLineInfo(@NotNull String commandLineText);
}
