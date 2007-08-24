package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.*;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.LinkedHashSet;
import java.util.Set;

class IntroduceVariableDialog extends DialogWrapper implements IntroduceVariableSettings {
  private Project myProject;
  private final PsiExpression myExpression;
  private final int myOccurrencesCount;
  private boolean myAnyLValueOccurences;
  private final boolean myDeclareFinalIfAll;
  private final TypeSelectorManager myTypeSelectorManager;
  private final IntroduceVariableHandler.Validator myValidator;

  private NameSuggestionsField myNameField;
  private JCheckBox myCbReplaceAll;
  private StateRestoringCheckBox myCbReplaceWrite = null;
  private JCheckBox myCbFinal;
  private boolean myCbFinalState;
  private TypeSelector myTypeSelector;
  private NameSuggestionsManager myNameSuggestionsManager;
  private static final String REFACTORING_NAME = RefactoringBundle.message("introduce.variable.title");

  public IntroduceVariableDialog(Project project,
                                 PsiExpression expression, int occurrencesCount, boolean anyLValueOccurences,
                                 boolean declareFinalIfAll, TypeSelectorManager typeSelectorManager,
                                 IntroduceVariableHandler.Validator validator) {
    super(project, true);
    myProject = project;
    myExpression = expression;
    myOccurrencesCount = occurrencesCount;
    myAnyLValueOccurences = anyLValueOccurences;
    myDeclareFinalIfAll = declareFinalIfAll;
    myTypeSelectorManager = typeSelectorManager;
    myValidator = validator;

    setTitle(REFACTORING_NAME);
    init();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void init() {
    super.init();
    updateOkStatus();
  }

  public String getEnteredName() {
    return myNameField.getName();
  }

  public boolean isReplaceAllOccurrences() {
    if (myOccurrencesCount <= 1) return false;
    return myCbReplaceAll.isSelected();
  }

  public boolean isDeclareFinal() {
    if (myCbFinal.isEnabled()) {
      return myCbFinalState;
    } else {
      return true;
    }
  }

  public boolean isReplaceLValues() {
    if (myOccurrencesCount <= 1 || !myAnyLValueOccurences || myCbReplaceWrite == null) {
      return true;
    }
    else {
      return myCbReplaceWrite.isSelected();
    }
  }

  public PsiType getSelectedType() {
    return myTypeSelector.getSelectedType();
  }

  protected JComponent createNorthPanel() {
    myNameField = new NameSuggestionsField(myProject);
    myNameField.addDataChangedListener(new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        updateOkStatus();
      }
    });

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.anchor = GridBagConstraints.WEST;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    JLabel type = new JLabel(RefactoringBundle.message("variable.of.type"));
    panel.add(type, gbConstraints);

    gbConstraints.gridx++;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    panel.add(myTypeSelector.getComponent(), gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    JLabel namePrompt = new JLabel(RefactoringBundle.message("name.prompt"));
    namePrompt.setLabelFor(myNameField.getComponent());
    panel.add(namePrompt, gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 1;
    panel.add(myNameField.getComponent(), gbConstraints);

    myNameSuggestionsManager = new NameSuggestionsManager(myTypeSelector, myNameField,
            new NameSuggestionsGenerator() {
              public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
                final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
                final SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, myExpression, type);
                return codeStyleManager.suggestUniqueVariableName(nameInfo, myExpression, true);
              }

              public Pair<LookupItemPreferencePolicy, Set<LookupItem>> completeVariableName(String prefix, PsiType type) {
                LinkedHashSet<LookupItem> set = new LinkedHashSet<LookupItem>();
                LookupItemPreferencePolicy policy = CompletionUtil.completeVariableNameForRefactoring(myProject, set, prefix, type, VariableKind.LOCAL_VARIABLE);
                return new Pair<LookupItemPreferencePolicy, Set<LookupItem>> (policy, set);
              }
            }, myProject);
    myNameSuggestionsManager.setLabelsFor(type, namePrompt);

    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.insets = new Insets(0, 0, 0, 0);

    if (myOccurrencesCount > 1) {
      myCbReplaceAll = new NonFocusableCheckBox();
      myCbReplaceAll.setText(RefactoringBundle.message("replace.all.occurences", myOccurrencesCount));

      panel.add(myCbReplaceAll, gbConstraints);
      myCbReplaceAll.addItemListener(
              new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                  updateControls();
                }
              }
      );

      if (myAnyLValueOccurences) {
        myCbReplaceWrite = new StateRestoringCheckBox();
        myCbReplaceWrite.setText(RefactoringBundle.message("replace.write.access.occurrences"));
        gbConstraints.insets = new Insets(0, 8, 0, 0);
        gbConstraints.gridy++;
        panel.add(myCbReplaceWrite, gbConstraints);
      }
    }

    myCbFinal = new NonFocusableCheckBox();
    myCbFinal.setText(RefactoringBundle.message("declare.final"));
    final Boolean createFinals = RefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS;
    myCbFinalState = createFinals == null ?
                     CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_LOCALS :
                     createFinals;

    gbConstraints.insets = new Insets(0, 0, 0, 0);
    gbConstraints.gridy++;
    panel.add(myCbFinal, gbConstraints);
    myCbFinal.addItemListener(
            new ItemListener() {
              public void itemStateChanged(ItemEvent e) {
                if (myCbFinal.isEnabled()) {
                  myCbFinalState = myCbFinal.isSelected();
                }
              }
            }
    );

    updateControls();

    return panel;
  }

  private void updateControls() {
    if (myCbReplaceWrite != null) {
      if (myCbReplaceAll.isSelected()) {
        myCbReplaceWrite.makeSelectable();
      } else {
        myCbReplaceWrite.makeUnselectable(true);
      }
    }

    if (myCbReplaceAll != null) {
      myTypeSelectorManager.setAllOccurences(myCbReplaceAll.isSelected());
    } else {
      myTypeSelectorManager.setAllOccurences(false);
    }

    if (myDeclareFinalIfAll && myCbReplaceAll != null && myCbReplaceAll.isSelected()) {
      myCbFinal.setEnabled(false);
      myCbFinal.setSelected(true);
    } else {
      myCbFinal.setEnabled(true);
      myCbFinal.setSelected(myCbFinalState);
    }
  }

  protected void doOKAction() {
    if (!myValidator.isOK(this)) return;
    myNameSuggestionsManager.nameSelected();
    if (myCbFinal.isEnabled()) {
      RefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS = myCbFinalState;
    }
    super.doOKAction();
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(PsiManager.getInstance(myProject).getNameHelper().isIdentifier(text));
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_VARIABLE);
  }
}