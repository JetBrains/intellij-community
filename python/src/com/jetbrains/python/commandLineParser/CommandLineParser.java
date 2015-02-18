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
package com.jetbrains.python.commandLineParser;

import org.jetbrains.annotations.NotNull;

/**
 * Engine to {@link CommandLine} structure from text.
 *
 * @author Ilya.Kazakevich
 */
public interface CommandLineParser {
  /**
   *
   * @param commandLineText command line to parse
   * @return command line information
   * @throws MalformedCommandLineException in case of bad commandline
   */
  @NotNull
  CommandLine parse(@NotNull String commandLineText) throws MalformedCommandLineException;
}
