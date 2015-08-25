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
package com.jetbrains.python.traceBackParsers;

import com.jetbrains.python.run.PyTracebackParser;
import com.jetbrains.python.testing.pytest.PyTestTracebackParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Searches for line in stacktrace
 *
 * @author Ilya.Kazakevich
 */
public interface TraceBackParser {
  /**
   * Searches for link in line
   *
   * @param line line to search link in
   * @return line info (if found)
   */
  @Nullable
  LinkInTrace findLinkInTrace(@NotNull String line);

  @NotNull // TODO: use EP instead?
  @SuppressWarnings("PublicStaticArrayField") // Noone will change it, anyway.
  TraceBackParser[] PARSERS = {new PyTestTracebackParser(), new PyTracebackParser()};
}
