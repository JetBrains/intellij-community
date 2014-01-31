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
package com.jetbrains.python.refactoring.classes.pullUp;


import com.intellij.psi.PsiElement;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Ilya.Kazakevich
 * View for pull-up refactoring
 */
public interface PyPullUpView {
  /**
   * Launches view.
   *
   * @param parents         collection of class parents to display (first one would be displayed)
   * @param memberInfoModel Member info model for members panel
   * @param memberInfos     Member infos: list of class members
   */
  void init(@NotNull Collection<PyClass> parents,
            @NotNull MemberInfoModel<PyElement, PyMemberInfo> memberInfoModel,
            @NotNull List<PyMemberInfo> memberInfos);

  /**
   * @return Parent that user selected
   */
  @NotNull
  PyClass getSelectedParent();

  /**
   * Closes view
   */
  void closeDialog();

  /**
   * @return List of members selected by user
   */
  @NotNull
  Collection<PyMemberInfo> getSelectedMemberInfos();

  /**
   * Displays conflicts dialog
   *
   * @param conflicts map of conflicts
   * @return true when user clicked "yes". False otherwise
   */
  boolean showConflictsDialog(@NotNull MultiMap<PsiElement, String> conflicts);
}
