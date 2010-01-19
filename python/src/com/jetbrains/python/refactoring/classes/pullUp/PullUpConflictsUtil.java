package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public class PullUpConflictsUtil {
  private PullUpConflictsUtil() {
  }

  public static MultiMap<PsiElement, String> checkConflicts(final Collection<PyMemberInfo> infos, final PyClass superClass) {
    final MultiMap<PsiElement, String> conflictsList = new MultiMap<PsiElement, String>();
    for (PyMemberInfo info : infos) {
      PsiElement member = info.getMember();
      boolean isConflict = false;
      if (member instanceof PyFunction) {
        final String name = ((PyFunction)member).getName();
        if (name == null) continue;
        final PyFunction superClassMethod = superClass.findMethodByName(name, false);
        isConflict = superClassMethod != null;
      } else if (member instanceof PyClass) {
        final PyClass clazz = (PyClass)member;
        for (PyClass aClass : superClass.getSuperClasses()) {
          if (aClass == clazz) {
            conflictsList.putValue(superClass,
                                   RefactoringUIUtil.getDescription(superClass, false) + " already extends " + RefactoringUIUtil.getDescription(clazz, false));
          }
        }
      }

      if (isConflict) {
        final String message = RefactoringBundle.message("0.already.contains.a.1",
                                                         RefactoringUIUtil.getDescription(superClass, false),
                                                         RefactoringUIUtil.getDescription(member, false));
        conflictsList.putValue(superClass, message);
      }
    }

    return conflictsList;
  }
}
