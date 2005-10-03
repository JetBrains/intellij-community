/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 16:54:19
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.ui.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

public class IntroduceParameterDialog extends RefactoringDialog {
  private TypeSelector myTypeSelector;
  private NameSuggestionsManager myNameSuggestionsManager;

  private Project myProject;
  private List myClassMembersList;
  private int myOccurenceNumber;
  private final boolean myIsInvokedOnDeclaration;
  private final PsiMethod myMethodToSearchFor;
  private final PsiMethod myMethodToReplaceIn;
  private PsiExpression myExpression;
  private PsiLocalVariable myLocalVar;
  private boolean myIsLocalVariable;
  private boolean myHasInitializer;

//  private JComponent myParameterNameField = null;
  private NameSuggestionsField myParameterNameField;
  private JCheckBox myCbReplaceAllOccurences = null;
  private JCheckBox myCbDeclareFinal = null;
  private StateRestoringCheckBox myCbDeleteLocalVariable = null;
  private StateRestoringCheckBox myCbUseInitializer = null;

  private JRadioButton myReplaceFieldsWithGettersNoneRadio = null;
  private JRadioButton myReplaceFieldsWithGettersInaccessibleRadio = null;
  private JRadioButton myReplaceFieldsWithGettersAllRadio = null;

  private ButtonGroup myReplaceFieldsWithGettersButtonGroup = new ButtonGroup();

  private final NameSuggestionsGenerator myNameSuggestionsGenerator;
  private final TypeSelectorManager myTypeSelectorManager;
  private static final String REFACTORING_NAME = RefactoringBundle.message("introduce.parameter.title");


  IntroduceParameterDialog(Project project,
                           List classMembersList,
                           int occurenceNumber,
                           PsiLocalVariable onLocalVariable,
                           PsiExpression onExpression,
                           NameSuggestionsGenerator generator,
                           TypeSelectorManager typeSelectorManager,
                           PsiMethod methodToSearchFor,
                           PsiMethod methodToReplaceIn) {
    super(project, true);
    myProject = project;
    myClassMembersList = classMembersList;
    myOccurenceNumber = occurenceNumber;
    myExpression = onExpression;
    myLocalVar = onLocalVariable;
    myMethodToReplaceIn = methodToReplaceIn;
    myIsInvokedOnDeclaration = onExpression == null;
    myMethodToSearchFor = methodToSearchFor;
    myIsLocalVariable = onLocalVariable != null;
    myHasInitializer = onLocalVariable != null && onLocalVariable.getInitializer() != null;
    myNameSuggestionsGenerator = generator;
    myTypeSelectorManager = typeSelectorManager;

    setTitle(REFACTORING_NAME);
    init();
  }

  public boolean isDeclareFinal() {
    if (myCbDeclareFinal != null) {
      return myCbDeclareFinal.isSelected();
    } else {
      return false;
    }
  }

  public boolean isReplaceAllOccurences() {
    if(myIsInvokedOnDeclaration)
      return true;
    if (myCbReplaceAllOccurences != null) {
      return myCbReplaceAllOccurences.isSelected();
    }
    else
      return false;
  }

  public boolean isDeleteLocalVariable() {
    if(myIsInvokedOnDeclaration)
      return true;
    if(myCbDeleteLocalVariable != null) {
      return myCbDeleteLocalVariable.isSelected();
    }
    else
      return false;
  }

  public boolean isUseInitializer() {
    if(myIsInvokedOnDeclaration)
      return myHasInitializer;
    if(myCbUseInitializer != null) {
      return myCbUseInitializer.isSelected();
    }
    return false;
  }

  public String getParameterName() {
    return  myParameterNameField.getName();
  }

  public int getReplaceFieldsWithGetters() {
    if(myReplaceFieldsWithGettersAllRadio != null && myReplaceFieldsWithGettersAllRadio.isSelected()) {
      return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL;
    }
    else if(myReplaceFieldsWithGettersInaccessibleRadio != null
            && myReplaceFieldsWithGettersInaccessibleRadio.isSelected()) {
      return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE;
    }
    else if(myReplaceFieldsWithGettersNoneRadio != null && myReplaceFieldsWithGettersNoneRadio.isSelected()) {
      return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE;
    }

    return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE;
  }

  public JComponent getPreferredFocusedComponent() {
    return myParameterNameField.getComponent();
  }

  public PsiType getSelectedType() {
    return myTypeSelector.getSelectedType();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_PARAMETER);
  }

  protected JComponent createNorthPanel() {
    GridBagConstraints gbConstraints = new GridBagConstraints();

    JPanel panel = new JPanel(new GridBagLayout());

    gbConstraints.anchor = GridBagConstraints.WEST;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.gridx = 0;

    gbConstraints.insets = new Insets(4, 4, 4, 0);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.gridy = 0;
    JLabel type = new JLabel(RefactoringBundle.message("parameter.of.type"));
    panel.add(type, gbConstraints);

    gbConstraints.insets = new Insets(4, 4, 4, 8);
    gbConstraints.gridx++;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    panel.add(myTypeSelector.getComponent(), gbConstraints);


    gbConstraints.insets = new Insets(4, 4, 4, 8);
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    gbConstraints.fill = GridBagConstraints.NONE;

    myParameterNameField = new NameSuggestionsField(myProject);
    final JLabel nameLabel = new JLabel(RefactoringBundle.message("name.prompt"));
    nameLabel.setLabelFor(myParameterNameField.getComponent());
    panel.add(nameLabel, gbConstraints);

/*
    if (myNameSuggestions.length > 1) {
      myParameterNameField = createComboBoxForName();
    }
    else {
      myParameterNameField = createTextFieldForName();
    }
*/
    gbConstraints.gridx++;
    gbConstraints.insets = new Insets(4, 4, 4, 8);
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    panel.add(myParameterNameField.getComponent(), gbConstraints);
    myParameterNameField.addDataChangedListener(new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    });

    myNameSuggestionsManager =
            new NameSuggestionsManager(myTypeSelector, myParameterNameField, myNameSuggestionsGenerator, myProject);
    myNameSuggestionsManager.setLabelsFor(type, nameLabel);

    gbConstraints.gridx = 0;
    gbConstraints.insets = new Insets(4, 0, 4, 8);
    gbConstraints.gridwidth = 2;
    if (myOccurenceNumber > 1 && !myIsInvokedOnDeclaration) {
      gbConstraints.gridy++;
      myCbReplaceAllOccurences = new NonFocusableCheckBox();
      myCbReplaceAllOccurences.setText(RefactoringBundle.message("replace.all.occurences", myOccurenceNumber));

      panel.add(myCbReplaceAllOccurences, gbConstraints);
      myCbReplaceAllOccurences.setSelected(false);
    }

    RefactoringSettings settings = RefactoringSettings.getInstance();

    gbConstraints.gridy++;
    myCbDeclareFinal = new NonFocusableCheckBox();
    myCbDeclareFinal.setText(RefactoringBundle.message("declare.final"));

    final Boolean settingsFinals = settings.INTRODUCE_PARAMETER_CREATE_FINALS;
    myCbDeclareFinal.setSelected(settingsFinals == null ?
                                 CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS :
                                 settingsFinals.booleanValue());
    panel.add(myCbDeclareFinal, gbConstraints);


    if(myIsLocalVariable && !myIsInvokedOnDeclaration) {
      myCbDeleteLocalVariable = new StateRestoringCheckBox();
      myCbDeleteLocalVariable.setText(RefactoringBundle.message("delete.variable.definition"));

      if(myCbReplaceAllOccurences != null) {
        gbConstraints.insets = new Insets(0, 16, 4, 8);
      }
      gbConstraints.gridy++;
      panel.add(myCbDeleteLocalVariable, gbConstraints);
      myCbDeleteLocalVariable.setSelected(settings.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE);

      gbConstraints.insets = new Insets(4, 0, 4, 8);
      if(myHasInitializer) {
        myCbUseInitializer = new StateRestoringCheckBox();
        myCbUseInitializer.setText(RefactoringBundle.message("use.variable.initializer.to.initialize.parameter"));

        gbConstraints.gridy++;
        panel.add(myCbUseInitializer, gbConstraints);
      }
    }

    updateControls();
    if (myCbReplaceAllOccurences != null) {
      myCbReplaceAllOccurences.addItemListener(
              new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                  updateControls();
                }
              }
      );
    }
    return panel;
  }

  private void updateControls() {
    if(myCbReplaceAllOccurences != null) {
      myTypeSelectorManager.setAllOccurences(myCbReplaceAllOccurences.isSelected());
      if(myCbReplaceAllOccurences.isSelected()) {
        if (myCbDeleteLocalVariable != null) {
          myCbDeleteLocalVariable.makeSelectable();
        }
      }
      else {
        if (myCbDeleteLocalVariable != null) {
          myCbDeleteLocalVariable.makeUnselectable(false);
        }
      }
    } else {
      myTypeSelectorManager.setAllOccurences(myIsInvokedOnDeclaration);
    }

  }

  private JPanel createReplaceFieldsWithGettersPanel() {
    JPanel radioButtonPanel = new JPanel(new GridBagLayout());

    radioButtonPanel.setBorder(IdeBorderFactory.createBorder());

    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    radioButtonPanel.add(
            new JLabel(RefactoringBundle.message("replace.fields.used.in.expressions.with.their.getters")), gbConstraints);

    myReplaceFieldsWithGettersNoneRadio = new JRadioButton();
    myReplaceFieldsWithGettersNoneRadio.setText(RefactoringBundle.message("do.not.replace"));

    myReplaceFieldsWithGettersInaccessibleRadio = new JRadioButton();
    myReplaceFieldsWithGettersInaccessibleRadio.setText(RefactoringBundle.message("replace.fields.inaccessible.in.usage.context"));

    myReplaceFieldsWithGettersAllRadio = new JRadioButton();
    myReplaceFieldsWithGettersAllRadio.setText(RefactoringBundle.message("replace.all.fields"));

    gbConstraints.gridy++;
    radioButtonPanel.add(myReplaceFieldsWithGettersNoneRadio, gbConstraints);
    gbConstraints.gridy++;
    radioButtonPanel.add(myReplaceFieldsWithGettersInaccessibleRadio, gbConstraints);
    gbConstraints.gridy++;
    radioButtonPanel.add(myReplaceFieldsWithGettersAllRadio, gbConstraints);

    final int currentSetting = RefactoringSettings.getInstance().INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS;

    myReplaceFieldsWithGettersButtonGroup.add(myReplaceFieldsWithGettersNoneRadio);
    myReplaceFieldsWithGettersButtonGroup.add(myReplaceFieldsWithGettersInaccessibleRadio);
    myReplaceFieldsWithGettersButtonGroup.add(myReplaceFieldsWithGettersAllRadio);

    if(currentSetting == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL) {
      myReplaceFieldsWithGettersAllRadio.setSelected(true);
    }
    else if(currentSetting == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE) {
      myReplaceFieldsWithGettersInaccessibleRadio.setSelected(true);
    }
    else if(currentSetting == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE) {
      myReplaceFieldsWithGettersNoneRadio.setSelected(true);
    }

    return radioButtonPanel;
  }

  protected JComponent createCenterPanel() {
    if(Util.anyFieldsWithGettersPresent(myClassMembersList)) {
      return createReplaceFieldsWithGettersPanel();
    }
    else
      return null;
  }

  protected void doAction() {
    final RefactoringSettings settings = RefactoringSettings.getInstance();
    settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS =
            getReplaceFieldsWithGetters();
    if (myCbDeclareFinal != null) settings.INTRODUCE_PARAMETER_CREATE_FINALS = new Boolean(myCbDeclareFinal.isSelected());

    if(myCbDeleteLocalVariable != null) {
      settings.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE =
              myCbDeleteLocalVariable.isSelectedWhenSelectable();
    }

    myNameSuggestionsManager.nameSelected();

    boolean isDeleteLocalVariable = false;

    PsiExpression parameterInitializer = myExpression;
    if (myLocalVar != null) {
      if (isUseInitializer()) {
      parameterInitializer = myLocalVar.getInitializer();      }
      isDeleteLocalVariable = isDeleteLocalVariable();
    }

    final IntroduceParameterProcessor processor = new IntroduceParameterProcessor(
      myProject, myMethodToReplaceIn, myMethodToSearchFor,
      parameterInitializer, myExpression,
      myLocalVar, isDeleteLocalVariable,
      getParameterName(), isReplaceAllOccurences(),
      getReplaceFieldsWithGetters(), isDeclareFinal(),
      getSelectedType());
    invokeRefactoring(processor);
    myParameterNameField.requestFocusInWindow();
  }


  protected boolean areButtonsValid () {
    String name = getParameterName();
    if (name == null) {
      return false;
    }
    else {
      return PsiManager.getInstance(myProject).getNameHelper().isIdentifier(name.trim());
    }
  }


}
