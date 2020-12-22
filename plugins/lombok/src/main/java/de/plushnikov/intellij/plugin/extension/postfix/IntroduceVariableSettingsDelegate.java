package de.plushnikov.intellij.plugin.extension.postfix;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;

public class IntroduceVariableSettingsDelegate implements IntroduceVariableSettings {
  private final IntroduceVariableSettings variableSettings;
  private final PsiClassType psiClassType;

  public IntroduceVariableSettingsDelegate(IntroduceVariableSettings variableSettings, PsiClassType psiClassType) {
    this.variableSettings = variableSettings;
    this.psiClassType = psiClassType;
  }

  @Override
  public String getEnteredName() {
    return variableSettings.getEnteredName();
  }

  @Override
  public boolean isReplaceAllOccurrences() {
    return variableSettings.isReplaceAllOccurrences();
  }

  @Override
  public boolean isDeclareFinal() {
    return variableSettings.isDeclareFinal();
  }

  @Override
  public boolean isReplaceLValues() {
    return variableSettings.isReplaceLValues();
  }

  @Override
  public PsiType getSelectedType() {
    return psiClassType;
  }

  @Override
  public boolean isOK() {
    return variableSettings.isOK();
  }
}
