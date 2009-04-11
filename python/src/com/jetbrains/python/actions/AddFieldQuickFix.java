package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Available on self.my_something when my_something is unresolved.
 * User: dcheryasov
 * Date: Apr 4, 2009 1:53:46 PM
 */
public class AddFieldQuickFix implements LocalQuickFix {

  private PyClass myQualifierClass;
  private String myIdentifier;

  public AddFieldQuickFix(String identifier, PyClass qualifierClass) {
    myIdentifier = identifier;
    myQualifierClass = qualifierClass;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", myIdentifier, myQualifierClass.getName());
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  private static PsiElement appendToInit(PyFunction init, String item_name, PyElementGenerator generator, Project project) {
    // add this field as the last stmt of the constructor
    final PyStatementList stmt_list = init.getStatementList();
    PyStatement[] stmts = stmt_list.getStatements(); // NOTE: rather wasteful, consider iterable stmt list
    PyStatement last_stmt = null;
    if (stmts.length > 0) last_stmt = stmts[stmts.length-1];
    // name of 'self' may be different for fancier styles
    PyParameter[] params = init.getParameterList().getParameters();
    String self_name = PyNames.CANONICAL_SELF;
    if (params.length > 0) {
      self_name = params[0].getName();
    }
    PyStatement new_stmt = generator.createFromText(project, PyStatement.class, self_name + "." +item_name + " = None");
    PyUtil.ensureWritable(stmt_list);
    return stmt_list.addAfter(new_stmt, last_stmt);
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    // expect the descriptor to point to the unresolved identifier.
    PyClass cls = myQualifierClass;
    String item_name = myIdentifier;
    if (cls != null && item_name != null) {
      PyFunction init = cls.findMethodByName(PyNames.INIT);
      Language language = cls.getLanguage();
      if (language instanceof PythonLanguage) {
        PythonLanguage pythonLanguage = (PythonLanguage)language;
        PyElementGenerator generator = pythonLanguage.getElementGenerator();
        if (init != null) {
          appendToInit(init, item_name, generator, project);
          return;
        }
        else { // no init! boldly copy ancestor's.
          for (PyClass ancestor : cls.iterateAncestors()) {
            init = ancestor.findMethodByName(PyNames.INIT);
            if (init != null) break;
          }
          if (init != null) {
            // TODO: factor this out
            // found it; copy its param list and make a call to it.
            PyUtil.ensureWritable(cls);
            final PyParameterList paramlist = init.getParameterList();
            PyFunction new_init = generator.createFromText(
              project, PyFunction.class,
              "def "+PyNames.INIT + paramlist.getText() + ":\n",
              new int[]{0}
            ); // NOTE: this results in a parsing error, but the StatementList gets created ok
            if (cls.isNewStyleClass()) {
              // form the super() call
              StringBuffer sb = new StringBuffer("super(");
              sb.append(cls.getName());
              PyParameter[] params = paramlist.getParameters();
              // NOTE: assume that we have at least the first param
              String self_name = params[0].getName();
              sb.append(", ").append(self_name).append(").").append(PyNames.INIT).append("(");
              boolean seen = false;
              for (int i = 1; i < params.length; i += 1) {
                if (seen) sb.append(", ");
                else seen = true;
                sb.append(params[i].getText());
              }
              sb.append(")");
              PyStatement new_stmt = generator.createFromText(project, PyStatement.class, sb.toString());
              new_init.getStatementList().add(new_stmt);
            }
            appendToInit(new_init, item_name, generator, project);
            new_init.add(generator.createFromText(project, PsiWhiteSpace.class, "\n\n")); // after the last line

            PsiElement add_anchor = null;
            PyFunction[] meths = cls.getMethods();
            if (meths.length > 0) add_anchor = meths[0].getPrevSibling();
            PyStatementList cls_content = cls.getStatementList();
            cls_content.addAfter(new_init, add_anchor); 

            PyUtil.showBalloon(
              project,
              PyBundle.message("QFIX.added.constructor.$0.for.field.$1", cls.getName(), item_name), 
              MessageType.INFO
            );
            return;
          }
          //else  // well, that can't be
        }
      }
    }
    // somehow we failed. tell about this
    PyUtil.showBalloon(project, PyBundle.message("QFIX.failed.to.add.field"), MessageType.ERROR);
  }
}
