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

/**
 * Types of command line parts.
 *
 * @author Ilya.Kazakevich
 */
public enum CommandLinePartType {
  /**
   * Argument (or positional, or unnamed argument) something that has only value. Like "my_folder" in "rm my_folder"
   */
  ARGUMENT,
  /**
   * Option is named but optional parameter. Like "-l" in "ls -l".
   */
  OPTION,
  /**
   * Option argument like --folder-to-delete=/
   * Here root is option argument
   */
  OPTION_ARGUMENT,
  /**
   * Some part of command line that {@link com.jetbrains.python.commandLineParser.CommandLineParser} does not understand
   */
  UNKNOWN
}
