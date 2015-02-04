/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
 * Command-line like interface presenter that uses conception of command and its arguments.
 * See {@link com.jetbrains.python.commandInterface.commandsWithArgs.CommandInterfacePresenterCommandBased}
 * and its arguments: {@link com.jetbrains.python.commandInterface.commandsWithArgs.Argument}.
 *
 * It supports <a href="https://docs.python.org/2/library/optparse.html">optparse</a> terminology, so
 * read it first and use {@link com.jetbrains.python.optParse} package
 * @see com.jetbrains.python.optParse
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.commandInterface.commandsWithArgs;