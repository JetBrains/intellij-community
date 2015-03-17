package com.jetbrains.python.refactoring.move;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;

/**
 * Collects dependencies of the top-level symbols in the given module. This information is used then to highlight them
 * in "Move" dialog the same way as it's done for members of classes in various class-related refactorings.
 *
 * @see PyModuleMemberInfoModel
 *
 * @author Mikhail Golubev
 */
public class PyDependentModuleMembersCollector extends DependentMembersCollectorBase<PsiNamedElement, PyFile> {
  private final PyFile myModule;

  public PyDependentModuleMembersCollector(@NotNull PyFile module) {
    super(module, null);
    myModule = module;
  }

  @Override
  public void collect(final PsiNamedElement member) {
    if (member.getContainingFile() == myModule) {
      final PyResolveContext resolveContext = PyResolveContext.defaultContext();
      final PsiElement memberBody = PyMoveModuleMembersHelper.expandNamedElementBody(member);
      assert memberBody != null;
      memberBody.accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          for (PsiElement result : PyUtil.multiResolveTopPriority(element, resolveContext)) {
            if (result != null && isValidSameModuleDependency(result) && result != member) {
              myCollection.add((PsiNamedElement)result);
            }
          }
          super.visitElement(element);
        }
      });
    }
  }

  private boolean isValidSameModuleDependency(@NotNull PsiElement element) {
    return PyMoveModuleMembersHelper.isMovableModuleMember(element) && element.getContainingFile() == myModule;
  }
}
