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
 * <h1>Command with arguments and options.</h1>
 * <p>
 * Each {@link com.jetbrains.commandInterface.command.Command} may have one or more positional {@link com.jetbrains.commandInterface.command.Argument arguments}
 * and several {@link com.jetbrains.commandInterface.command.Option options} (with arguments as well).
 * You need to implemenet {@link com.jetbrains.commandInterface.command.Command} first.
 * </p>
 *
 * @author Ilya.Kazakevich
 */
package com.jetbrains.commandInterface.command;