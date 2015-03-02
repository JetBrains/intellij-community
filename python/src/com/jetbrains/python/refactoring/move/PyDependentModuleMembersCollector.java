package com.jetbrains.python.refactoring.move;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;

/**
 * Collects dependencies of the top-level symbols in the given module. This information is used then to highlight them
 * in "Move ..." dialog the same way as it's done for members of classes in various class-related refactorings.
 *
 * @see PyModuleMemberInfoModel
 *
 * @author Mikhail Golubev
 */
public class PyDependentModuleMembersCollector extends DependentMembersCollectorBase<PyElement, PyFile> {
  private final PyFile myModule;

  public PyDependentModuleMembersCollector(@NotNull PyFile module) {
    super(module, null);
    myModule = module;
  }

  @Override
  public void collect(final PyElement member) {
    if (member.getContainingFile() == myModule) {
      final PyResolveContext resolveContext = PyResolveContext.defaultContext();
      member.accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          for (PsiElement result : PyUtil.multiResolveTopPriority(element, resolveContext)) {
            if (isValidSameModuleDependency(result) && result != member) {
              myCollection.add(((PyElement)result));
            }
          }
          super.visitElement(element);
        }
      });
    }
  }

  private boolean isValidSameModuleDependency(@NotNull PsiElement element) {
    return PyMoveClassOrFunctionDelegate.canMoveElement(element) && element.getContainingFile() == myModule;
  }
}
