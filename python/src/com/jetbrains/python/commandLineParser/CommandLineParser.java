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

import com.jetbrains.python.WordWithPosition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Engine to parse command line. It understands how options and arguments are coded in certain commandline.
 * It supportd {@link com.jetbrains.python.WordWithPosition} telling you exactly with part of
 * command line is command or argument. That helps you to underline or emphisize some parts.
 *
 * @author Ilya.Kazakevich
 */
public interface CommandLineParser {
  /**
   * @param commandLineParts command line splitted into words.
   * @return command line information
   * @throws MalformedCommandLineException in case of bad commandline
   */
  @NotNull
  CommandLineParseResult parse(@NotNull List<WordWithPosition> commandLineParts) throws MalformedCommandLineException;
}
