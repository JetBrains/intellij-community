package de.plushnikov.intellij.plugin.extension.postfix;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;

public class IntroduceLombokVariableHandler extends IntroduceVariableHandler {
  private final String selectedTypeFQN;

  IntroduceLombokVariableHandler(String selectedTypeFQN) {
    this.selectedTypeFQN = selectedTypeFQN;
  }

  /*
   * This method with OccurrencesChooser.ReplaceChoice parameter exists up to 2017.2
   * Started from 2017.2 it use JavaReplaceChoice parameter
   */
  @Override
  public IntroduceVariableSettings getSettings(Project project, Editor editor, PsiExpression expr,
                                               PsiExpression[] occurrences, TypeSelectorManagerImpl typeSelectorManager,
                                               boolean declareFinalIfAll, boolean anyAssignmentLHS, InputValidator validator,
                                               PsiElement anchor, JavaReplaceChoice replaceChoice) {
    final IntroduceVariableSettings variableSettings;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      variableSettings = new UnitTestMockVariableSettings(expr);
    } else {
      variableSettings = super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll,
        anyAssignmentLHS, validator, anchor, replaceChoice);
    }

    return getIntroduceVariableSettings(project, variableSettings);
  }

  private IntroduceVariableSettings getIntroduceVariableSettings(Project project, IntroduceVariableSettings variableSettings) {
    final PsiClassType psiClassType = PsiType.getTypeByName(selectedTypeFQN, project, GlobalSearchScope.projectScope(project));
    if (null != psiClassType) {
      return new IntroduceVariableSettingsDelegate(variableSettings, psiClassType);
    } else {
      return variableSettings;
    }
  }

  private static class UnitTestMockVariableSettings implements IntroduceVariableSettings {
    private final PsiExpression expr;

    UnitTestMockVariableSettings(PsiExpression expr) {
      this.expr = expr;
    }

    @Override
    public String getEnteredName() {
      return "foo";
    }

    @Override
    public boolean isReplaceAllOccurrences() {
      return false;
    }

    @Override
    public boolean isDeclareFinal() {
      return false;
    }

    @Override
    public boolean isReplaceLValues() {
      return false;
    }

    @Override
    public PsiType getSelectedType() {
      return expr.getType();
    }

    @Override
    public boolean isOK() {
      return true;
    }
  }
}
