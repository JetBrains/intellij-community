package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashSet;
import java.util.Set;

class IntroduceConstantDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.IntroduceConstantDialog");

  private Project myProject;
  private final PsiClass myParentClass;
  private final PsiExpression myInitializerExpression;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myInvokedOnDeclaration;
  private final PsiExpression[] myOccurrences;
  private final int myOccurrencesCount;
  private final PsiClass myTargetClass;
  private final TypeSelectorManager myTypeSelectorManager;

  private NameSuggestionsField myNameField;
  private JCheckBox myCbReplaceAll;

  private JRadioButton myRbPrivate;
  private JRadioButton myRbProtected;
  private JRadioButton myRbpackageLocal;
  private JRadioButton myRbPublic;

  private TypeSelector myTypeSelector;
  private StateRestoringCheckBox myCbDeleteVariable;
  private final CodeStyleManager myCodeStyleManager;
  private ReferenceEditorWithBrowseButton myTfTargetClassName;
  private PsiClass myDestinationClass;
  private JPanel myTypePanel;
  private JPanel myTargetClassNamePanel;
  private JPanel myPanel;
  private JLabel myTypeLabel;
  private JPanel myNameSuggestionPanel;
  private JLabel myNameSuggestionLabel;
  private JLabel myTargetClassNameLabel;

  public IntroduceConstantDialog(Project project,
                                 PsiClass parentClass,
                                 PsiExpression initializerExpression,
                                 PsiLocalVariable localVariable, boolean isInvokedOnDeclaration,
                                 PsiExpression[] occurrences, PsiClass targetClass, TypeSelectorManager typeSelectorManager) {

    super(project, true);
    myProject = project;
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myLocalVariable = localVariable;
    myInvokedOnDeclaration = isInvokedOnDeclaration;
    myOccurrences = occurrences;
    myOccurrencesCount = occurrences.length;
    myTargetClass = targetClass;
    myTypeSelectorManager = typeSelectorManager;
    myDestinationClass = null;

    setTitle(IntroduceConstantHandler.REFACTORING_NAME);
    myCodeStyleManager = CodeStyleManager.getInstance(myProject);
    init();

    final String ourLastVisibility = RefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY;
    if (PsiModifier.PUBLIC.equals(ourLastVisibility)) {
      myRbPublic.setSelected(true);
    } else if (PsiModifier.PROTECTED.equals(ourLastVisibility)) {
      myRbProtected.setSelected(true);
    } else if (PsiModifier.PACKAGE_LOCAL.equals(ourLastVisibility)) {
      myRbpackageLocal.setSelected(true);
    } else if (PsiModifier.PRIVATE.equals(ourLastVisibility)) {
      myRbPrivate.setSelected(true);
    } else {
      myRbPrivate.setSelected(true);
    }
  }

  public String getEnteredName() {
    return myNameField.getName();
  }

  private String getTargetClassName() {
    return myTfTargetClassName.getText();
  }

  public PsiClass getDestinationClass () {
    return myDestinationClass;
  }

  public String getFieldVisibility() {
    if (myRbPublic.isSelected()) {
      return PsiModifier.PUBLIC;
    }
    if (myRbpackageLocal.isSelected()) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (myRbProtected.isSelected()) {
      return PsiModifier.PROTECTED;
    }
    if (myRbPrivate.isSelected()) {
      return PsiModifier.PRIVATE;
    }
    LOG.assertTrue(false);
    return null;
  }

  public boolean isReplaceAllOccurrences() {
    if (myOccurrencesCount <= 1) return false;
    return myCbReplaceAll.isSelected();
  }

  public PsiType getSelectedType() {
    return myTypeSelector.getSelectedType();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_CONSTANT);
  }

  protected JComponent createNorthPanel() {
    final NameSuggestionsManager nameSuggestionsManager;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    myTypePanel.setLayout(new BorderLayout());
    myTypePanel.add(myTypeSelector.getComponent(), BorderLayout.CENTER);
    if (myTypeSelector.getFocusableComponent() != null) {
      myTypeLabel.setDisplayedMnemonic(KeyEvent.VK_T);
      myTypeLabel.setLabelFor(myTypeSelector.getFocusableComponent());
    }

    myNameField = new NameSuggestionsField(myProject);
    myNameSuggestionPanel.setLayout(new BorderLayout());

    myNameSuggestionPanel.add(myNameField.getComponent(), BorderLayout.CENTER);
    myNameSuggestionLabel.setLabelFor(myNameField.getFocusableComponent());

    myTfTargetClassName = new ReferenceEditorWithBrowseButton(new ChooseClassAction(), "", PsiManager.getInstance(myProject), true);
    myTargetClassNamePanel.setLayout(new BorderLayout());
    myTargetClassNamePanel.add(myTfTargetClassName, BorderLayout.CENTER);
    myTargetClassNameLabel.setLabelFor(myTfTargetClassName);

    final String propertyName;
    if (myLocalVariable != null) {
      propertyName = myCodeStyleManager.variableNameToPropertyName(myLocalVariable.getName(), VariableKind.LOCAL_VARIABLE);
    }
    else {
      propertyName = null;
    }
    nameSuggestionsManager = new NameSuggestionsManager(myTypeSelector, myNameField,
                                                          new NameSuggestionsGenerator() {
      public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
        return myCodeStyleManager.suggestVariableName(
          VariableKind.STATIC_FINAL_FIELD, propertyName, myInitializerExpression, type
        );
      }

      public Pair<LookupItemPreferencePolicy, Set<LookupItem>> completeVariableName(String prefix, PsiType type) {
        LinkedHashSet<LookupItem> set = new LinkedHashSet<LookupItem>();
        LookupItemPreferencePolicy policy =
          CompletionUtil.completeVariableName(myProject, set, prefix, type, VariableKind.STATIC_FINAL_FIELD);
        return new Pair<LookupItemPreferencePolicy, Set<LookupItem>>(policy, set);
      }
    },
                                                          myProject);

    nameSuggestionsManager.setMnemonics(myTypeLabel, myNameSuggestionLabel);
    //////////
    if (myOccurrencesCount > 1) {
      ItemListener itemListener = new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          updateTypeSelector();

          myNameField.requestFocusInWindow();
        }
      };
      myCbReplaceAll.addItemListener(itemListener);
      myCbReplaceAll.setText("Replace all occurrences of expression (" + myOccurrencesCount + " occurrences)");
    }
    else {
      myCbReplaceAll.setVisible(false);
    }

    if (myLocalVariable != null) {
      if (myInvokedOnDeclaration) {
        myCbDeleteVariable.setEnabled(false);
        myCbDeleteVariable.setSelected(true);
      }
      else if (myCbReplaceAll != null) {
        updateCbDeleteVariable();
        myCbReplaceAll.addItemListener(
          new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            updateCbDeleteVariable();
          }
        }
        );
      }
    }
    else {
      myCbDeleteVariable.setVisible(false);
    }
    updateTypeSelector();
    updateVisibilityPanel();
    return myPanel;
  }

  protected JComponent createCenterPanel() {
    return new JPanel();
  }

  public boolean isDeleteVariable() {
    if (myInvokedOnDeclaration) return true;
    if (myCbDeleteVariable == null) return false;
    return myCbDeleteVariable.isSelected();
  }

  private void updateCbDeleteVariable() {
    if (!myCbReplaceAll.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    } else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  private void updateTypeSelector() {
    if (myCbReplaceAll != null) {
      myTypeSelectorManager.setAllOccurences(myCbReplaceAll.isSelected());
    } else {
      myTypeSelectorManager.setAllOccurences(false);
    }
  }

  private void updateVisibilityPanel() {
    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbPrivate);
    bg.add(myRbpackageLocal);
    bg.add(myRbProtected);
    bg.add(myRbPublic);


    if (myTargetClass.isInterface()) {
      myRbPrivate.setEnabled(false);
      myRbProtected.setEnabled(false);
      myRbpackageLocal.setEnabled(false);
      myRbPublic.setEnabled(true);
      myRbPublic.setSelected(true);
    }
  }

  protected void doOKAction() {

    final String targetClassName = getTargetClassName();
    if (!"".equals (targetClassName)) {
      final PsiManager manager = PsiManager.getInstance(myProject);
      final PsiClass  newClass = manager.findClass(targetClassName);
      if (newClass == null) {
        RefactoringMessageUtil.showErrorMessage(
                IntroduceConstantHandler.REFACTORING_NAME,
                "Class does not exist",
                HelpID.INTRODUCE_FIELD,
                myProject);
        return;
      }
      myDestinationClass = newClass;
    }

    String fieldName = getEnteredName();
    String errorString = null;
    if ("".equals(fieldName)) {
      errorString = "No field name specified";
    } else if (!PsiManager.getInstance(myProject).getNameHelper().isIdentifier(fieldName)) {
      errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    }
    if (errorString != null) {
      RefactoringMessageUtil.showErrorMessage(
              IntroduceFieldHandler.REFACTORING_NAME,
              errorString,
              HelpID.INTRODUCE_FIELD,
              myProject);
      return;
    }
    PsiField oldField = myParentClass.findFieldByName(fieldName, true);

    if (oldField != null) {
      int answer = Messages.showYesNoDialog(
              myProject,
              "The field with the name " + fieldName + "\nalready exists in class '"
              + oldField.getContainingClass().getQualifiedName() + "'.\nContinue?",
              IntroduceFieldHandler.REFACTORING_NAME,
              Messages.getWarningIcon()
      );
      if (answer != 0) {
        return;
      }
    }

    RefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getFieldVisibility();

    super.doOKAction();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField.getComponent();
  }

  private class ChooseClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser("Choose Destination Class", GlobalSearchScope.projectScope(myProject), new TreeClassChooser.ClassFilter() {
        public boolean isAccepted(PsiClass aClass) {
          return aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC);
        }
      }, null);
      chooser.selectDirectory(myTargetClass.getContainingFile().getContainingDirectory());
      chooser.showDialog();
      PsiClass aClass = chooser.getSelectedClass();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
      }
    }
  }
}