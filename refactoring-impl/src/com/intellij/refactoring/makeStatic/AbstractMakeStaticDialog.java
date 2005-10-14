/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 04.07.2002
 * Time: 13:14:49
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.makeStatic;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.usageView.UsageViewUtil;

import javax.swing.*;

public abstract class AbstractMakeStaticDialog extends RefactoringDialog {
  protected PsiTypeParameterListOwner myMember;
  protected String myMemberName;

  public AbstractMakeStaticDialog(Project project, PsiTypeParameterListOwner member) {
    super(project, true);
    myMember = member;
    myMemberName = member.getName();
  }

  protected void doAction() {
    if (!validateData())
      return;

    final Settings settings = new Settings(
            isReplaceUsages(),
            isMakeClassParameter() ? getClassParameterName() : null,
            getVariableData()
    );
    if (myMember instanceof PsiMethod) {
      invokeRefactoring(new MakeMethodStaticProcessor(getProject(), (PsiMethod)myMember, settings));
    }
    else {
      invokeRefactoring(new MakeClassStaticProcessor(getProject(), (PsiClass)myMember, settings));
    }
  }

  protected abstract boolean validateData();

  public abstract boolean isMakeClassParameter();

  public abstract String getClassParameterName();

  public abstract ParameterTablePanel.VariableData[] getVariableData();

  public abstract boolean isReplaceUsages();

  protected JLabel createDescriptionLabel() {
    String type = UsageViewUtil.getType(myMember);
    return new JLabel(RefactoringBundle.message("make.static.description.label", type, myMemberName));
  }
}
