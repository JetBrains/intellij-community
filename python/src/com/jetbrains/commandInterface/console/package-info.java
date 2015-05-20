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

/**
 * <h1>Command line console</h1>
 * <p>
 * Package to display command line console on the bottom of the screen. It supports history, execution, syntax highlighting and so on.
 * Entry point is {@link com.jetbrains.commandInterface.console.CommandLineConsoleApi}.
 * </p>
 * <p/>
 * <h2>Technical details</h2>
 * <p>
 * This package uses {@link com.jetbrains.commandInterface.console.CommandConsole} and it can work in 2 modes: "command-mode"
 *  to accept, highlight and execute commands, and in "process-mode" to pass stdin/stdout to process it execute.
 * See class documentation for details.
 * </p>
 *
 * @author Ilya.Kazakevich
 * @see com.jetbrains.commandInterface.console.CommandLineConsoleApi
 */
package com.jetbrains.commandInterface.console;