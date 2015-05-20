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
 * <h1>GNU command line language PSI</h1>
 * <h2>Command line language</h2>
 * <p>
 * <a href="http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html#tag_12_02">GNU</a> command line language syntax
 * based on <a href="http://www.gnu.org/software/libc/manual/html_node/Argument-Syntax.html">POSIX</a> syntax.
 * <pre>
 *   my_command --option --option-2=argument positional_argument -s
 * </pre>
 * </p>
 * <h2>PSI</h2>
 * <p>Generation based on Grammar-Kit, see .bnf file, do not edit parser nor lexer manually.
 * When parsed, {@link com.jetbrains.commandInterface.commandLine.psi.CommandLineFile} is root element for
 * {@link com.jetbrains.commandInterface.commandLine.CommandLineLanguage}.
 * <strong>Warning</strong>: always fill {@link com.jetbrains.commandInterface.commandLine.psi.CommandLineFile#setCommandsAndDefaultExecutor(java.util.List, com.jetbrains.commandInterface.command.CommandExecutor)}
 * if possible.
 * </p>
 * <h2>Extension points</h2>
 * <p>This package has a a lot of extension points (language, inspection etc). Make sure all of them are registered</p>
 *
 * @author Ilya.Kazakevich
 */
package com.jetbrains.commandInterface.commandLine;