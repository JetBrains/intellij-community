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
package com.jetbrains.python.refactoring.classes.membersManager.vp;

import org.jetbrains.annotations.NotNull;

/**
 * To be thrown when {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedViewSwingImpl} or its children
 * assumes that data entered by user in {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedView} is invalid.
 * See {@link MembersBasedPresenterImpl#validateView()} for info why exception user
 * @author Ilya.Kazakevich
 */
public class BadDataException extends Exception {
  /**
   * @param message what exactly is wrong with data
   */
  public BadDataException(@NotNull final String message) {
    super(message);
  }
}
