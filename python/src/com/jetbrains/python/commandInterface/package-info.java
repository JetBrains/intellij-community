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
 * View/Presenter pair that implements so-called "command line interface".
 * It has several abilities, including (but not limited):
 * <ol>
 *   <li>Suggestion box</li>
 *   <li>Error marking</li>
 *   <li>Popups</li>
 *   <li>AutoCompletion</li>
 * </ol>
 * <p>
 *   System consists of {@link com.jetbrains.python.commandInterface.CommandInterfacePresenter}
 *   and appropriate {@link com.jetbrains.python.commandInterface.CommandInterfaceView}.
 *   See {@link com.jetbrains.python.vp} for more info.
 * </p>
 *
 * <p>
 *   There is also swing-based view implementation in {@link com.jetbrains.python.commandInterface.swingView}
 *   and presenter implementation based on idea of commands with arguments. See {@link com.jetbrains.python.commandInterface.commandsWithArgs}
 * </p>
 *
 *
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.commandInterface;