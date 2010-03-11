package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyClassScopeProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.python.core.PyMethod;

/**
 * @author oleg
 */
public class AddGlobalQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.add.global");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement problemElt = descriptor.getPsiElement();
    if (problemElt instanceof PyReferenceExpression) {
      final PyReferenceExpression expression = (PyReferenceExpression)problemElt;
      final String name = expression.getReferencedName();
      final ScopeOwner owner = PsiTreeUtil.getParentOfType(problemElt, ScopeOwner.class);
      assert owner instanceof PyClass || owner instanceof PyFunction : "Add global quickfix is available only inside class or function, but applied for " + owner;
      final Ref<Boolean> added = new Ref<Boolean>(false);
      owner.accept(new PyRecursiveElementVisitor(){
        @Override
        public void visitElement(PsiElement element) {
          if (!added.get()){
            super.visitElement(element);
          }
        }
        @Override
        public void visitPyGlobalStatement(final PyGlobalStatement node) {
          if (!added.get()){
            node.addGlobal(name);
            added.set(true);
          }
        }
      });
      if (added.get()){
        return;
      }
      final PyGlobalStatement globalStatement =
        PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyGlobalStatement.class, "global " + name);
      if (owner instanceof PyClass){
        final PyStatementList statementList = ((PyClass)owner).getStatementList();
        statementList.addBefore(globalStatement, statementList.getStatements()[0]);
        return;
      }
      if (owner instanceof PyFunction){
        final PyStatementList statementList = ((PyFunction)owner).getStatementList();
        statementList.addBefore(globalStatement, statementList.getStatements()[0]);
        return;
      }
    }
  }
}
