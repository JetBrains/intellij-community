package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PyFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element instanceof PyClass ||
           (element instanceof PyFile && PyNames.INIT_DOT_PY.equals(((PyFile) element).getName())) ||
           element instanceof PyFunction;
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    if (element instanceof PyFile) {
      return new PyModuleFindUsagesHandler((PyFile) element);
    }
    if (element instanceof PyFunction) {
      if (!forHighlightUsages) {
        final Collection<PsiElement> superMethods = PySuperMethodsSearch.search((PyFunction)element, true).findAll();
        if (superMethods.size() > 0) {
          final PsiElement next = superMethods.iterator().next();
          // TODO should do this for Jython functions overriding Java methods too
          if (next instanceof PyFunction && !isInClassobj((PyFunction)next)) {
            StringBuilder messageBuilder = new StringBuilder("Method ");
            messageBuilder.append(((PyFunction)element).getName());
            messageBuilder.append(" overrides method of class ");
            messageBuilder.append(((PyFunction)next).getContainingClass().getName());
            messageBuilder.append(".\nDo you want to find usages of the base method?");
            int rc = Messages.showYesNoCancelDialog(element.getProject(), messageBuilder.toString(),  "Find Usages", Messages.getQuestionIcon());
            if (rc == 0) {
              List<PsiElement> allMethods = new ArrayList<PsiElement>();
              allMethods.add(element);
              allMethods.addAll(superMethods);
              return new PyFunctionFindUsagesHandler(element, allMethods);
            }
            if (rc == 1) {
              return new PyFunctionFindUsagesHandler(element);
            }
            return FindUsagesHandler.NULL_HANDLER;
          }
        }

      }
      return new PyFunctionFindUsagesHandler(element);
    }
    return new PyClassFindUsagesHandler((PyClass)element);
  }

  private static boolean isInClassobj(PyFunction fun) {
    final PyClass containingClass = fun.getContainingClass();
    return containingClass != null && PyNames.FAKE_OLD_BASE.equals(containingClass.getName());
  }
}
