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
 * <p>
 * An <a href="https://docs.python.org/2/library/optparse.html">optparse</a> module twin, that parses command line.
 * Unlike any other GNU/Posix parsers, it knows how to:</p>
 * <ol>
 *   <li>Parse options and args with out of any knowledge about required args</li>
 *   <li>Provide actual places in command line where exactly such args or opts exist./li>
 * </ol>
 * <p>
 * Be sure to read optparse manual
 * (epecially <a href="https://docs.python.org/2/library/optparse.html#terminology">terminology</a>) part.
 * </p>
 * <p>Package entry point is {@link com.jetbrains.python.optParse.ParsedCommandLine}</p>
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.optParse;