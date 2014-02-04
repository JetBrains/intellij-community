/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.MembersManager;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public final class PyPullUpHelper {

  private PyPullUpHelper() {
  }

  public static PyElement pullUp(final PyClass clazz, final Collection<PyMemberInfo> selectedMemberInfos, final PyClass superClass) {

    CommandProcessor.getInstance().executeCommand(clazz.getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            MembersManager.moveAllMembers(clazz, superClass, selectedMemberInfos);
          }
        });
      }
    }, RefactoringBundle.message("pull.members.up.title"), null);

    return superClass;
  }

}
