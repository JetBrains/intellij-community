package com.jetbrains.python.actions;

import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Adds a method foo to class X if X.foo() is unresolved. 
 * User: dcheryasov
 * Date: Apr 5, 2009 6:51:26 PM
 */
public class AddMethodQuickFix implements LocalQuickFix {

  private PyClass myQualifierClass;
  private String myIdentifier;

  public AddMethodQuickFix(String identifier, PyClass qualifierClass) {
    myIdentifier = identifier;
    myQualifierClass = qualifierClass;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", myIdentifier, myQualifierClass.getName());
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    // descriptor points to the unresolved ident
    // there can be no name clash, else the name would have resloved, and it hasn't.
    PsiElement problem_elt = descriptor.getPsiElement().getParent(); // id -> ref expr
    PyClass cls = myQualifierClass;
    String item_name = myIdentifier;
    if (cls != null && item_name != null) {
      PyStatementList cls_stmt_list = cls.getStatementList();
      PyUtil.ensureWritable(cls_stmt_list);
      Language language = cls.getLanguage();
      if (language instanceof PythonLanguage) {
        PythonLanguage pythonLanguage = (PythonLanguage)language;
        PyElementGenerator generator = pythonLanguage.getElementGenerator();
        // try to at least match parameter count
        // TODO: get parameter style from code style
        StringBuffer param_buf = new StringBuffer("(self"); // NOTE: might use a name other than 'self', according to code style.
        PsiElement pe = problem_elt.getParent();
        if (pe instanceof PyCallExpression) { // must always be the case
          PyArgumentList arglist = ((PyCallExpression)pe).getArgumentList();
          if (arglist != null) {
            int cnt = 1; // number parameters so we don't need to care about their names being unique.
            for (PyExpression arg : arglist.getArguments()) {
              if (arg instanceof PyReferenceExpression) { // foo(bar) -> def foo(self, bar_1)
                param_buf.append(", ").append(((PyReferenceExpression)arg).getReferencedName()).append("_").append(cnt);
              }
              else { // use a boring name
                param_buf.append(", param_").append(cnt);
              }
              cnt += 1;
            }
          }
        }
        param_buf.append("):\n");
        PyFunction meth = generator.createFromText(project, PyFunction.class, "def " + item_name + param_buf);
        // NOTE: this results in a parsing error, but the StatementList gets created ok
        PyStatement new_stmt = generator.createFromText(project, PyStatement.class, "pass"); // TODO: use a predefined template
        meth.getStatementList().add(new_stmt);
        meth.add(generator.createFromText(project, PsiWhiteSpace.class, "\n\n")); // after the last line of method
        cls_stmt_list.add(generator.createFromText(project, PsiWhiteSpace.class, "\n\n")); // after the last method, before ours
        meth = (PyFunction) cls_stmt_list.add(meth);
        showTemplateBuilder(meth);
        return;
      }
    }
    // we failed. tell about this
    PyUtil.showBalloon(project, PyBundle.message("QFIX.failed.to.add.method"), MessageType.ERROR);
  }

  private static void showTemplateBuilder(PyFunction method) {
    TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(method);
    PyParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 1; i < parameters.length; i++) {
      builder.replaceElement(parameters [i], parameters [i].getName());
    }
    builder.replaceElement(method.getStatementList(), "pass");

    builder.run();
  }
}
