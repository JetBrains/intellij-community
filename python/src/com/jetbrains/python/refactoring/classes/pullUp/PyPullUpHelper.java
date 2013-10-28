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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;

import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public class PyPullUpHelper {
  private static final Logger LOG = Logger.getInstance(PyPullUpHelper.class.getName());
  private PyPullUpHelper() {}

  public static PyElement pullUp(final PyClass clazz, final Collection<PyMemberInfo> selectedMemberInfos, final PyClass superClass) {
    final Set<String> superClasses = new HashSet<String>();
    final Set<PsiNamedElement> extractedClasses = new HashSet<PsiNamedElement>();
    final List<PyFunction> methods = new ArrayList<PyFunction>();
    for (PyMemberInfo member : selectedMemberInfos) {
      final PyElement element = member.getMember();
      if (element instanceof PyFunction) methods.add((PyFunction)element);
      else if (element instanceof PyClass) {
        superClasses.add(element.getName());
        extractedClasses.add((PyClass)element);
      }
      else LOG.error("unmatched member class " + element.getClass());
    }
    
    CommandProcessor.getInstance().executeCommand(clazz.getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            // move methods
            PyClassRefactoringUtil.moveMethods(methods, superClass);

            // move superclasses declarations
            PyClassRefactoringUtil.moveSuperclasses(clazz, superClasses, superClass);
            PyClassRefactoringUtil.insertImport(superClass, extractedClasses);
            PyClassRefactoringUtil.insertPassIfNeeded(clazz);
          }
        });
      }
    }, RefactoringBundle.message("pull.members.up.title"), null);

    return superClass;
  }

}
