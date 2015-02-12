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
 * {@link com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ChunkDriver} implementation based on idea of
 * {@link com.jetbrains.python.commandInterface.command command, option and argument}.
 *
 *  See {@link com.jetbrains.python.commandInterface.commandBasedChunkDriver.CommandBasedChunkDriver} as entry point.
 *  It parses command line using {@link com.jetbrains.python.commandLineParser.CommandLineParser} and finds matching command and arguments
 *  provided by user
 *
 * @see com.jetbrains.python.commandInterface.command
 * @see com.jetbrains.python.commandInterface.command.Command
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.commandInterface.commandBasedChunkDriver;