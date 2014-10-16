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
 * Incapsulates knowledge about class members that could be moved to some other class.
 * To use (get list of members to move or actually move them) use {@link com.jetbrains.python.refactoring.classes.membersManager.MembersManager#getAllMembersCouldBeMoved(com.jetbrains.python.psi.PyClass)}
 * and                                                            {@link com.jetbrains.python.refactoring.classes.membersManager.MembersManager#moveAllMembers(java.util.Collection, com.jetbrains.python.psi.PyClass, com.jetbrains.python.psi.PyClass)}
 *
 * This class delegates its behaviour to its managers (some kind of plugins). There is one for each member type (one for method, one for field etc).
 * You need to extend {@link com.jetbrains.python.refactoring.classes.membersManager.MembersManager} to add some. See its javadoc for more info.
 *
 *
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.refactoring.classes.membersManager;