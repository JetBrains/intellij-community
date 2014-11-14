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

import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * View to display dialog with members.
 * First, configure it with {@link #configure(MembersViewInitializationInfo)}.
 * Then, display with {@link #initAndShow()}
 *
 * @param <C> initialization info for this view. See {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo}
 *            for more info
 * @author Ilya.Kazakevich
 */
public interface MembersBasedView<C extends MembersViewInitializationInfo> {
  /**
   * Display conflict dialogs.
   *
   * @param duplicatesConflict    duplicates conflicts : that means destination class has the same member.
   *                              If member "foo" already exists in class "bar": pass [bar] -] [foo].
   * @param dependenciesConflicts dependency conflict: list of elements used by member under refactoring and would not be available
   *                              at new destination. If user wants to move method, that uses field "bar" which would not be available at new class,
   *                              pass [bar] field
   * @return true if user's choice is "continue". False if "cancel"
   */
  boolean showConflictsDialog(
    @NotNull MultiMap<PyClass, PyMemberInfo<?>> duplicatesConflict,
    @NotNull Collection<PyMemberInfo<?>> dependenciesConflicts);

  /**
   * Displays error message
   *
   * @param message message to display
   */
  void showError(@NotNull String message);

  /**
   * Configures view and <strong>must</strong> be called once, before {@link #initAndShow()}
   * It accepts configuration info class
   * Children may rewrite method to do additional configuration, but they should <strong>always</strong> call "super" first!
   *
   * @param configInfo configuration info
   */
  void configure(@NotNull C configInfo);

  /**
   * @return collection of member infos user selected
   */
  @NotNull
  Collection<PyMemberInfo<PyElement>> getSelectedMemberInfos();

  /**
   * Runs refactoring based on {@link com.intellij.refactoring.BaseRefactoringProcessor}.
   * It may display "preview" first.
   *
   * @param processor refactoring processor
   */
  void invokeRefactoring(@NotNull BaseRefactoringProcessor processor);

  /**
   * Displays dialog. Be sure to run {@link #configure(MembersViewInitializationInfo)} first
   */
  void initAndShow();

  /**
   * Closes dialog
   */
  void close();
}
