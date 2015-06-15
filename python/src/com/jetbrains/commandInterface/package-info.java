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
 * <h1>Command-line interface</h1>
 * <h2>What is the purpose of this package?</h2>
 * <p>
 *   This package is based on ideas of command-line with command, positional arguments, options and their arguments.
 *   Initial idea is taken from <a href="http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html#tag_12_02">POSIX</a>
 *   and enhanced by <a href="http://www.gnu.org/software/libc/manual/html_node/Argument-Syntax.html">GNU</a>.
 *   It also supported by Python in <a href="https://docs.python.org/2/library/optparse.html">optparse</a> package.
 *   Command-line is something like
 *   <pre>my_command positional_argument -s --long-option --another-long-option=arg1 arg2 some_other_arg</pre>
 *   and this package helps you to parse command lines, highlight errors, display a console-like interface and execute commands.
 * </p>
 * <h2>What this package consists of?</h2>
 * <p>
 *   This package has 3 subpackages:
 *   <ol>
 *     <li>{@link com.jetbrains.commandInterface.command}  contains classes to describe commands, arguments and options.
 *     It is up to you where to obtain list of available commands, but you should implement {@link com.jetbrains.commandInterface.command.Command}
 *     first, and create list of it with arguments and options. See package info for more details</li>
 *     <li>{@link com.jetbrains.commandInterface.commandLine} is language based on
 *     <a href="http://www.gnu.org/software/libc/manual/html_node/Argument-Syntax.html">GNU</a> command line representation.
 *     It has PSI, so it parses command lines into tree of PsiElements. But this package <strong>is not only for parsing</strong>:
 *     If you provide list of
 *     {@link com.jetbrains.commandInterface.command.Command commands} to {@link com.jetbrains.commandInterface.commandLine.psi.CommandLineFile}
 *      (see {@link com.jetbrains.commandInterface.commandLine.psi.CommandLineFile#setCommandsAndDefaultExecutor(java.util.List, com.jetbrains.commandInterface.command.CommandExecutor)}}), it will inject references
 *      (to provide autocompletion) and activate inspection to validate options and arguments. </li>
 *      <li>{@link com.jetbrains.commandInterface.console} displays console-like interface at the bottom of the screen to give user
 *      ability to wotk with your command-line.</li>
 *   </ol>
 * </p>
 * <h2>How to use this package?</h2>
 *   <ol>
 *     <li>Implement {@link com.jetbrains.commandInterface.command.Command}</li>
 *     <li>Create list of {@link com.jetbrains.commandInterface.command.Command commands}</li>
 *     <li>Provide it to {@link com.jetbrains.commandInterface.console.CommandLineConsoleApi#createConsole(com.intellij.openapi.module.Module, java.lang.String, java.util.List)}</li>
 *   </ol>
 * @author Ilya.Kazakevich
 */
package com.jetbrains.commandInterface;