package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.TestUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import static com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler.InitializationPlace.*;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.LinkedHashSet;
import java.util.Set;

class IntroduceFieldDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.IntroduceFieldDialog");

  private static boolean ourLastCbFinalState = false;
  private static BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace;

  private Project myProject;
  private final PsiClass myParentClass;
  private final PsiExpression myInitializerExpression;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myIsCurrentMethodConstructor;
  private final boolean myIsInvokedOnDeclaration;
  private boolean myWillBeDeclaredStatic;
  private final int myOccurrencesCount;
  private final boolean myAllowInitInMethod;
  private final boolean myAllowInitInMethodIfAll;
  private final TypeSelectorManager myTypeSelectorManager;

  private NameSuggestionsField myNameField;
  private JCheckBox myCbReplaceAll;
  private StateRestoringCheckBox myCbDeleteVariable;
  private StateRestoringCheckBox myCbFinal;

  private JRadioButton myRbInConstructor;
  private JRadioButton myRbInCurrentMethod;
  private JRadioButton myRbInFieldDeclaration;
  private JRadioButton myRbInSetUp;

  private JRadioButton myRbPrivate;
  private JRadioButton myRbProtected;
  private JRadioButton myRbPackageLocal;
  private JRadioButton myRbPublic;
  private TypeSelector myTypeSelector;
  private NameSuggestionsManager myNameSuggestionsManager;
  private static final String REFACTORING_NAME = RefactoringBundle.message("introduce.field.title");

  public IntroduceFieldDialog(Project project,
                              PsiClass parentClass,
                              PsiExpression initializerExpression,
                              PsiLocalVariable localVariable,
                              boolean isCurrentMethodConstructor, boolean isInvokedOnDeclaration, boolean willBeDeclaredStatic,
                              int occurrencesCount, boolean allowInitInMethod, boolean allowInitInMethodIfAll,
                              TypeSelectorManager typeSelectorManager) {
    super(project, true);
    myProject = project;
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myLocalVariable = localVariable;
    myIsCurrentMethodConstructor = isCurrentMethodConstructor;
    myIsInvokedOnDeclaration = isInvokedOnDeclaration;
    myWillBeDeclaredStatic = willBeDeclaredStatic;
    myOccurrencesCount = occurrencesCount;
    myAllowInitInMethod = allowInitInMethod;
    myAllowInitInMethodIfAll = allowInitInMethodIfAll;
    myTypeSelectorManager = typeSelectorManager;

    setTitle(REFACTORING_NAME);
    init();

    initializeControls(initializerExpression);

  }

  private void initializeControls(PsiExpression initializerExpression) {
    if (initializerExpression != null) {
      setEnabledInitializationPlaces(initializerExpression, initializerExpression);
      if (!myAllowInitInMethod) {
        myRbInCurrentMethod.setEnabled(false);
      }
    } else {
      myRbInConstructor.setEnabled(false);
      myRbInCurrentMethod.setEnabled(false);
      myRbInFieldDeclaration.setEnabled(false);
      if (myRbInSetUp != null) myRbInSetUp.setEnabled(false);
    }

    if (ourLastInitializerPlace == IN_CONSTRUCTOR) {
      if (myRbInConstructor.isEnabled()) {
        myRbInConstructor.setSelected(true);
      } else {
        selectInCurrentMethod();
      }
    } else if (ourLastInitializerPlace == IN_FIELD_DECLARATION) {
      if (myRbInFieldDeclaration.isEnabled()) {
        myRbInFieldDeclaration.setSelected(true);
      } else {
        selectInCurrentMethod();
      }
    } else if (ourLastInitializerPlace == IN_SETUP_METHOD && TestUtil.isTestClass(myParentClass) && myRbInSetUp.isEnabled()) {
      myRbInSetUp.setSelected(true);
    } else {
      selectInCurrentMethod();
    }
    String ourLastVisibility = RefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    if (PsiModifier.PUBLIC.equals(ourLastVisibility)) {
      myRbPublic.setSelected(true);
    } else if (PsiModifier.PROTECTED.equals(ourLastVisibility)) {
      myRbProtected.setSelected(true);
    } else if (PsiModifier.PACKAGE_LOCAL.equals(ourLastVisibility)) {
      myRbPackageLocal.setSelected(true);
    } else if (PsiModifier.PRIVATE.equals(ourLastVisibility)) {
      myRbPrivate.setSelected(true);
    } else {
      myRbPrivate.setSelected(true);
    }
    myCbFinal.setSelected(myCbFinal.isEnabled() && ourLastCbFinalState);
  }

  private void selectInCurrentMethod() {
    if (myRbInCurrentMethod.isEnabled()) {
      myRbInCurrentMethod.setSelected(true);
    }
    else if (myRbInFieldDeclaration.isEnabled()) {
      myRbInFieldDeclaration.setSelected(true);
    }
    else {
      myRbInCurrentMethod.setSelected(true);
    }
  }

  public String getEnteredName() {
    return myNameField.getName();
  }

  public BaseExpressionToFieldHandler.InitializationPlace getInitializerPlace() {
    if (myRbInConstructor.isSelected()) {
      return IN_CONSTRUCTOR;
    }
    if (myRbInCurrentMethod.isSelected()) {
      return IN_CURRENT_METHOD;
    }
    if (myRbInFieldDeclaration.isSelected()) {
      return IN_FIELD_DECLARATION;
    }
    if (myRbInSetUp != null && myRbInSetUp.isSelected()) {
      return IN_SETUP_METHOD;
    }

    LOG.assertTrue(false);
    return IN_FIELD_DECLARATION;
  }

  public String getFieldVisibility() {
    if (myRbPublic.isSelected()) {
      return PsiModifier.PUBLIC;
    }
    if (myRbPackageLocal.isSelected()) {
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
    if (myIsInvokedOnDeclaration) return true;
    if (myOccurrencesCount <= 1) return false;
    return myCbReplaceAll.isSelected();
  }

  public boolean isDeleteVariable() {
    if (myIsInvokedOnDeclaration) return true;
    if (myCbDeleteVariable == null) return false;
    return myCbDeleteVariable.isSelected();
  }

  public boolean isDeclareFinal() {
    return myCbFinal.isSelected();
  }

  public PsiType getFieldType() {
    return myTypeSelector.getSelectedType();
  }


  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }


  protected JComponent createNorthPanel() {

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 0);
    gbConstraints.anchor = GridBagConstraints.EAST;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;

    JLabel type = new JLabel(getTypeLabel());

    panel.add(type, gbConstraints);

    gbConstraints.gridx++;
    gbConstraints.insets = new Insets(4, 0, 4, 4);
    gbConstraints.weightx = 0;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    panel.add(myTypeSelector.getComponent(), gbConstraints);

    gbConstraints.insets = new Insets(4, 4, 4, 0);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    JLabel namePrompt = new JLabel(RefactoringBundle.message("name.prompt"));
    panel.add(namePrompt, gbConstraints);

    gbConstraints.insets = new Insets(4, 0, 4, 4);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 1;
    myNameField = new NameSuggestionsField(myProject);
    panel.add(myNameField.getComponent(), gbConstraints);
    namePrompt.setLabelFor(myNameField.getFocusableComponent());

    myNameSuggestionsManager = new NameSuggestionsManager(myTypeSelector, myNameField, createGenerator(), myProject);
    myNameSuggestionsManager.setLabelsFor(type, namePrompt);

    return panel;
  }

  private String getTypeLabel() {
    return myWillBeDeclaredStatic ?
           RefactoringBundle.message("introduce.field.static.field.of.type") :
           RefactoringBundle.message("introduce.field.field.of.type");
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
    final Insets standardInsets = new Insets(0, 0, 0, 0);
    gbConstraints.insets = standardInsets;

    panel.add(createInitializerPlacePanel(), gbConstraints);
    ItemListener itemListener = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (myCbReplaceAll != null && myAllowInitInMethod) {
          myRbInCurrentMethod.setEnabled(myAllowInitInMethodIfAll || !myCbReplaceAll.isSelected());
          if (!myRbInCurrentMethod.isEnabled() && myRbInCurrentMethod.isSelected()) {
            myRbInCurrentMethod.setSelected(false);
            myRbInFieldDeclaration.setSelected(true);
          }
        }
        updateTypeSelector();

        myNameField.requestFocusInWindow();
      }
    };
    ItemListener finalUpdater = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateCbFinal();
      }
    };
    myRbInConstructor.addItemListener(itemListener);
    myRbInCurrentMethod.addItemListener(itemListener);
    myRbInFieldDeclaration.addItemListener(itemListener);
    myRbInConstructor.addItemListener(finalUpdater);
    myRbInCurrentMethod.addItemListener(finalUpdater);
    myRbInFieldDeclaration.addItemListener(finalUpdater);
    if (myRbInSetUp != null) myRbInSetUp.addItemListener(finalUpdater);
    if (myOccurrencesCount > 1) {
      myCbReplaceAll = new NonFocusableCheckBox();
      myCbReplaceAll.setText(RefactoringBundle.message("replace.all.occurrences.of.expression.0.occurrences", myOccurrencesCount));
      gbConstraints.gridy++;
      panel.add(myCbReplaceAll, gbConstraints);
      myCbReplaceAll.addItemListener(itemListener);
      if (myIsInvokedOnDeclaration) {
        myCbReplaceAll.setEnabled(false);
        myCbReplaceAll.setSelected(true);
      }
    }

    if (myLocalVariable != null) {
      gbConstraints.gridy++;
      if (myCbReplaceAll != null) {
        gbConstraints.insets = new Insets(0, 8, 0, 0);
      }
      myCbDeleteVariable = new StateRestoringCheckBox();
      myCbDeleteVariable.setText(RefactoringBundle.message("delete.variable.declaration"));
      panel.add(myCbDeleteVariable, gbConstraints);
      if (myIsInvokedOnDeclaration) {
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
      gbConstraints.insets = standardInsets;
    }
    myCbFinal.addItemListener(itemListener);
//    myCbStatic.addItemListener(itemListener);
//    myCbStatic.addItemListener(finalUpdater);
//    myCbStatic.addItemListener(
//      new ItemListener() {
//        public void itemStateChanged(ItemEvent e) {
//          updateNameList();
//        }
//      }
//    );

    updateTypeSelector();
    return panel;
  }

  private void updateTypeSelector() {
    if (myCbReplaceAll != null) {
      myTypeSelectorManager.setAllOccurences(myCbReplaceAll.isSelected());
    } else {
      myTypeSelectorManager.setAllOccurences(false);
    }
  }

  private void updateCbDeleteVariable() {
    if (!myCbReplaceAll.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    } else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  private JComponent createInitializerPlacePanel() {
    JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());

    JPanel initializationPanel = new JPanel();
    initializationPanel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("initialize.in.border.title")));
    initializationPanel.setLayout(new BoxLayout(initializationPanel, BoxLayout.Y_AXIS));

    JPanel visibilityPanel = new JPanel();
    visibilityPanel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("visibility.border.title")));
    visibilityPanel.setLayout(new BoxLayout(visibilityPanel, BoxLayout.Y_AXIS));

    /*JPanel modifiersPanel = new GroupPanel();
    modifiersPanel.setBorder(BorderFactory.createTitledBorder("Other Modifiers"));
    modifiersPanel.setLayout(new BoxLayout(modifiersPanel, BoxLayout.Y_AXIS));*/

    myRbInCurrentMethod = new JRadioButton();
    myRbInCurrentMethod.setText(RefactoringBundle.message("current.method.radio"));
    myRbInCurrentMethod.setEnabled(myAllowInitInMethod);
    myRbInFieldDeclaration = new JRadioButton();
    myRbInFieldDeclaration.setText(RefactoringBundle.message("field.declaration.radio"));
    myRbInConstructor = new JRadioButton();
    myRbInConstructor.setText(RefactoringBundle.message("class.constructors.radio"));

    myRbPrivate = new JRadioButton();
    myRbPrivate.setText(RefactoringBundle.message("visibility.private"));
    myRbPrivate.setFocusable(false);
    myRbPackageLocal = new JRadioButton();
    myRbPackageLocal.setText(RefactoringBundle.message("visibility.package.local"));
    myRbPackageLocal.setFocusable(false);
    myRbProtected = new JRadioButton();
    myRbProtected.setText(RefactoringBundle.message("visibility.protected"));
    myRbProtected.setFocusable(false);
    myRbPublic = new JRadioButton();
    myRbPublic.setText(RefactoringBundle.message("visibility.public"));
    myRbPublic.setFocusable(false);

    myCbFinal = new StateRestoringCheckBox();
    myCbFinal.setText(RefactoringBundle.message("declare.final"));

    initializationPanel.add(myRbInCurrentMethod);
    initializationPanel.add(myRbInFieldDeclaration);
    initializationPanel.add(myRbInConstructor);

    if (TestUtil.isTestClass(myParentClass)) {
      myRbInSetUp = new JRadioButton();
      myRbInSetUp.setText(RefactoringBundle.message("setup.method.radio"));
      initializationPanel.add(myRbInSetUp);
    }

    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbInCurrentMethod);
    bg.add(myRbInFieldDeclaration);
    bg.add(myRbInConstructor);
    if (myRbInSetUp != null) bg.add(myRbInSetUp);

    visibilityPanel.add(myRbPrivate);
    visibilityPanel.add(myRbPackageLocal);
    visibilityPanel.add(myRbProtected);
    visibilityPanel.add(myRbPublic);
    bg = new ButtonGroup();
    bg.add(myRbPrivate);
    bg.add(myRbPackageLocal);
    bg.add(myRbProtected);
    bg.add(myRbPublic);

//    modifiersPanel.add(myCbFinal);
//    modifiersPanel.add(myCbStatic);

    JPanel groupPanel = new JPanel(new GridLayout(1, 2));
    groupPanel.add(initializationPanel);
    groupPanel.add(visibilityPanel);
    mainPanel.add(groupPanel, BorderLayout.CENTER);
    mainPanel.add(myCbFinal, BorderLayout.SOUTH);

    return mainPanel;
  }

  private void updateCbFinal() {
    boolean allowFinal = myRbInFieldDeclaration.isSelected() || (myRbInConstructor.isSelected() && !myWillBeDeclaredStatic);
    if (myRbInCurrentMethod.isSelected() && myIsCurrentMethodConstructor) {
      final PsiMethod[] constructors = myParentClass.getConstructors();
      allowFinal = constructors.length <= 1;
    }
    if (!allowFinal) {
      myCbFinal.makeUnselectable(false);
    } else {
      myCbFinal.makeSelectable();
    }
  }


  private NameSuggestionsGenerator createGenerator() {
    return new NameSuggestionsGenerator() {
      private CodeStyleManager myCodeStyleManager = CodeStyleManager.getInstance(myProject);
      public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
        VariableKind variableKind = myWillBeDeclaredStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;

        String propertyName = null;
        if (myIsInvokedOnDeclaration) {
          propertyName = myCodeStyleManager.variableNameToPropertyName(myLocalVariable.getName(),
                                                                       VariableKind.LOCAL_VARIABLE
          );
        }
        return myCodeStyleManager.suggestVariableName(variableKind, propertyName, myInitializerExpression, type);
      }

      public Pair<LookupItemPreferencePolicy, Set<LookupItem>> completeVariableName(String prefix, PsiType type) {
        LinkedHashSet<LookupItem> set = new LinkedHashSet<LookupItem>();
        VariableKind kind = myWillBeDeclaredStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
        LookupItemPreferencePolicy policy = CompletionUtil.completeVariableNameForRefactoring(myProject, set, prefix, type, kind);
        return new Pair<LookupItemPreferencePolicy, Set<LookupItem>> (policy, set);
      }
    };
  }


  protected void doOKAction() {
    String fieldName = getEnteredName();
    String errorString = null;
    if ("".equals(fieldName)) {
      errorString = RefactoringBundle.message("no.field.name.specified");
    } else if (!PsiManager.getInstance(myProject).getNameHelper().isIdentifier(fieldName)) {
      errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    }
    if (errorString != null) {
      CommonRefactoringUtil.showErrorMessage(
              IntroduceFieldHandler.REFACTORING_NAME,
              errorString,
              HelpID.INTRODUCE_FIELD,
              myProject
      );
      return;
    }

    PsiField oldField = myParentClass.findFieldByName(fieldName, true);

    if (oldField != null) {
      int answer = Messages.showYesNoDialog(
              myProject,
              RefactoringBundle.message("field.exists", fieldName,
                                   oldField.getContainingClass().getQualifiedName()),
              IntroduceFieldHandler.REFACTORING_NAME,
              Messages.getWarningIcon()
      );
      if (answer != 0) {
        return;
      }
    }

    ourLastCbFinalState = myCbFinal.isSelected();
    ourLastInitializerPlace = getInitializerPlace();
    RefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY = getFieldVisibility();

    myNameSuggestionsManager.nameSelected();
    super.doOKAction();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_FIELD);
  }

  private boolean setEnabledInitializationPlaces(PsiElement initializerPart, PsiElement initializer) {
    if (initializerPart instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression) initializerPart;
      if (refExpr.getQualifierExpression() == null) {
        PsiElement refElement = refExpr.resolve();
        if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
          if (!PsiTreeUtil.isAncestor(initializer, refElement, true)) {
            myRbInFieldDeclaration.setEnabled(false);
            myRbInConstructor.setEnabled(false);
            if (myRbInSetUp != null) myRbInSetUp.setEnabled(false);
            myCbFinal.setEnabled(false);
            return false;
          }
        }
      }
    }
    PsiElement[] children = initializerPart.getChildren();
    for (PsiElement child : children) {
      if (!setEnabledInitializationPlaces(child, initializer)) return false;
    }
    return true;
  }
}