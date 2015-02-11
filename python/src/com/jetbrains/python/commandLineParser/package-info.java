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
 * Engine to parse command-line.
 * Command line consists of command itself,  {@link com.jetbrains.python.commandLineParser.CommandLinePartType#ARGUMENT arguments}
 * and {@link com.jetbrains.python.commandLineParser.CommandLinePartType#OPTION options}.
 * Use need to split command line into {@link com.jetbrains.python.WordWithPosition chunks}, pass them to
 * {@link com.jetbrains.python.commandLineParser.CommandLineParser parser} and obtain {@link com.jetbrains.python.commandLineParser.CommandLineParseResult}.
 *
 * Not like any other parsers, this package supports {@link com.jetbrains.python.WordWithPosition} telling you exactly with part of
 * command line is command or argument. That helps you to underline or emphisize some parts.
 *
 *
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.commandLineParser;