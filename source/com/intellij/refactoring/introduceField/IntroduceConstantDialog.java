package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.ide.util.TreeClassChooserDialog;

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
  private NameSuggestionsManager myNameSuggestionsManager;
  private final CodeStyleManager myCodeStyleManager;
  private TextFieldWithBrowseButton myTfTargetClassName;
  private PsiClass myDestinationClass;

  public IntroduceConstantDialog(Project project,
                                 PsiClass parentClass,
                                 PsiExpression initializerExpression,
                                 PsiLocalVariable localVariable, boolean isInvokedOnDeclaration,
                                 int occurrencesCount, PsiClass targetClass, TypeSelectorManager typeSelectorManager) {

    super(project, true);
    myProject = project;
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myLocalVariable = localVariable;
    myInvokedOnDeclaration = isInvokedOnDeclaration;
    myOccurrencesCount = occurrencesCount;
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

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.anchor = GridBagConstraints.EAST;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.insets = new Insets(4, 4, 4, 0);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    JLabel type = new JLabel("Constant (static final field) of type: ");
    panel.add(type, gbConstraints);

    gbConstraints.gridx++;
    gbConstraints.insets = new Insets(4, 0, 4, 4);
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    panel.add(myTypeSelector.getComponent(), gbConstraints);
    if (myTypeSelector.getFocusableComponent() != null) {
      type.setDisplayedMnemonic(KeyEvent.VK_T);
      type.setLabelFor(myTypeSelector.getFocusableComponent());
    }


    final JLabel namePrompt;
    JPanel nameInputPanel;
    {
      nameInputPanel = new JPanel(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.insets = new Insets(0, 0, 0, 2);
      gbc.anchor = GridBagConstraints.EAST;
      gbc.fill = GridBagConstraints.BOTH;
      gbc.gridwidth = 1;
      gbc.weightx = 0;
      gbc.weighty = 1;
      gbc.gridx = 0;
      gbc.gridy = 0;
      namePrompt = new JLabel("Name: ");
      nameInputPanel.add(namePrompt, gbc);

      gbc.gridx++;
      gbc.insets = new Insets(0, 2, 0, 0);
      gbc.weightx = 1;
      myNameField = new NameSuggestionsField(myProject);
      nameInputPanel.add(myNameField.getComponent(), gbc);
      namePrompt.setDisplayedMnemonic(KeyEvent.VK_N);
      namePrompt.setLabelFor(myNameField.getFocusableComponent());
    }


    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.gridwidth = 2;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    panel.add(nameInputPanel, gbConstraints);

    {
      myTfTargetClassName = new TextFieldWithBrowseButton(new ChooseClassAction());
      JPanel _panel = new JPanel(new BorderLayout());
      JLabel label = new JLabel("To (fully qualified name):");
      label.setLabelFor(myTfTargetClassName);
      _panel.add(label, BorderLayout.NORTH);
      _panel.add(myTfTargetClassName, BorderLayout.CENTER);
      gbConstraints.gridy++;
      panel.add(_panel, gbConstraints);
    }

    /*gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;

    panel.add(namePrompt, gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 1;*/

//    panel.add(myNameField.getComponent(), gbConstraints);

    final String propertyName;
    if(myLocalVariable != null) {
      propertyName = myCodeStyleManager.variableNameToPropertyName(myLocalVariable.getName(), VariableKind.LOCAL_VARIABLE);
    } else {
      propertyName = null;
    }
    myNameSuggestionsManager = new NameSuggestionsManager(myTypeSelector, myNameField,
            new NameSuggestionsGenerator() {
              public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
                return myCodeStyleManager.suggestVariableName(
                        VariableKind.STATIC_FINAL_FIELD, propertyName, myInitializerExpression, type
                );
              }

              public Pair<LookupItemPreferencePolicy, Set<LookupItem>> completeVariableName(String prefix, PsiType type) {
                LinkedHashSet<LookupItem> set = new LinkedHashSet<LookupItem>();
                LookupItemPreferencePolicy policy = CompletionUtil.completeVariableName(myProject, set, prefix, type, VariableKind.STATIC_FINAL_FIELD);
                return new Pair<LookupItemPreferencePolicy, Set<LookupItem>> (policy, set);
              }
            },
            myProject);

    myNameSuggestionsManager.setMnemonics(type, namePrompt);

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

    panel.add(createVisibilityPanel(), gbConstraints);
    ItemListener itemListener = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateTypeSelector();

        myNameField.requestFocusInWindow();
      }
    };
    if (myOccurrencesCount > 1) {
      myCbReplaceAll = new NonFocusableCheckBox("Replace all occurrences of expression (" + myOccurrencesCount + " occurrences)");
      myCbReplaceAll.setMnemonic('R');
      myCbReplaceAll.setFocusable(false);
      gbConstraints.gridy++;
      panel.add(myCbReplaceAll, gbConstraints);
      myCbReplaceAll.addItemListener(itemListener);
    }

    if (myLocalVariable != null) {
      gbConstraints.gridy++;
      if (myCbReplaceAll != null) {
        gbConstraints.insets = new Insets(0, 8, 0, 0);
      }
      myCbDeleteVariable = new StateRestoringCheckBox("Delete variable declaration");
      myCbDeleteVariable.setFocusable(false);
      panel.add(myCbDeleteVariable, gbConstraints);
      myCbDeleteVariable.setMnemonic(KeyEvent.VK_D);
      if (myInvokedOnDeclaration) {
        myCbDeleteVariable.setEnabled(false);
        myCbDeleteVariable.setSelected(true);
      } else if (myCbReplaceAll != null) {
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
    updateTypeSelector();
    return panel;
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

  private JComponent createVisibilityPanel() {
    JPanel visibilityPanel = new JPanel();
    visibilityPanel.setBorder(IdeBorderFactory.createTitledBorder("Visibility"));
    visibilityPanel.setLayout(new BoxLayout(visibilityPanel, BoxLayout.Y_AXIS));


    myRbPrivate = new JRadioButton("Private");
    myRbPrivate.setMnemonic('v');
    myRbPrivate.setFocusable(false);
    myRbpackageLocal = new JRadioButton("Package local");
    myRbpackageLocal.setMnemonic('k');
    myRbpackageLocal.setFocusable(false);
    myRbProtected = new JRadioButton("Protected");
    myRbProtected.setMnemonic('o');
    myRbProtected.setFocusable(false);
    myRbPublic = new JRadioButton("Public");
    myRbPublic.setMnemonic('b');
    myRbPublic.setFocusable(false);


    visibilityPanel.add(myRbPrivate);
    visibilityPanel.add(myRbpackageLocal);
    visibilityPanel.add(myRbProtected);
    visibilityPanel.add(myRbPublic);
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

    return visibilityPanel;
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
      TreeClassChooserDialog chooser = TreeClassChooserDialog.withInnerClasses("Choose Destination Class", myProject, GlobalSearchScope.projectScope(myProject), new TreeClassChooserDialog.ClassFilter() {
        public boolean isAccepted(PsiClass aClass) {
          return aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC);
        }
      }, null);
      chooser.selectDirectory(myTargetClass.getContainingFile().getContainingDirectory());
      chooser.show();
      PsiClass aClass = chooser.getSelectedClass();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
      }
    }
  }
}