package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

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
      return new PyFunctionFindUsagesHandler(element);
    }
    return new PyClassFindUsagesHandler((PyClass)element);
  }
}
