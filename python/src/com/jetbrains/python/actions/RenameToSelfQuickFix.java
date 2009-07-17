package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyNamedParameter;
import org.jetbrains.annotations.NotNull;

/**
 * TODO: Add description
* User: dcheryasov
* Date: Nov 30, 2008 6:10:13 AM
*/
public class RenameToSelfQuickFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#" + RenameToSelfQuickFix.class.getName());

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement elt = descriptor.getPsiElement();
    if (elt != null && elt instanceof PyNamedParameter && elt.isWritable()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final PyNamedParameter the_self = PythonLanguage.getInstance().getElementGenerator().createParameter(project, "self");
          try {
            elt.replace(the_self);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      });
    }
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.NAME.parameters");
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.rename.parameter.to.self");
  }
}
