package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.ui.ClassCellRenderer;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.classMembers.InterfaceMemberDependencyGraph;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoChange;
import com.intellij.refactoring.util.classMembers.MemberInfoModel;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

public class InheritanceToDelegationDialog extends RefactoringDialog {
  private PsiClass[] mySuperClasses;

  private PsiClass myClass;
  private HashMap<PsiClass,MemberInfo[]> myBasesToMemberInfos;

  private NameSuggestionsField myFieldNameField;
  private NameSuggestionsField myInnerClassNameField;
  private JCheckBox myCbGenerateGetter;
  private MemberSelectionPanel myMemberSelectionPanel;
  private JComboBox myClassCombo;
  private Project myProject;

  public InheritanceToDelegationDialog(Project project,
                                       PsiClass aClass,
                                       PsiClass[] superClasses,
                                       HashMap<PsiClass,MemberInfo[]> basesToMemberInfos) {
    super(project, true);
    myProject = project;
    myClass = aClass;
    mySuperClasses = superClasses;
    myBasesToMemberInfos = basesToMemberInfos;

    setTitle(InheritanceToDelegationHandler.REFACTORING_NAME);
    init();
  }

  @NotNull
  public String getFieldName() {
    return myFieldNameField.getName();
  }

  public String getInnerClassName() {
    if (myInnerClassNameField != null) {
      return myInnerClassNameField.getName();
    }
    else {
      return null;
    }
  }

  public boolean isGenerateGetter() {
    return myCbGenerateGetter.isSelected();
  }

  protected boolean areButtonsValid() {
    final String fieldName = getFieldName();
    final PsiNameHelper helper = PsiManager.getInstance(myProject).getNameHelper();
    if (!helper.isIdentifier(fieldName)) return false;
    if (myInnerClassNameField != null) {
      final String className = myInnerClassNameField.getName();
      if (!helper.isIdentifier(className)) return false;
    }
    return true;
  }

  public MemberInfo[] getSelectedMemberInfos() {
    return myMemberSelectionPanel.getTable().getSelectedMemberInfos();
  }

  public PsiClass getSelectedTargetClass() {
    return (PsiClass)myClassCombo.getSelectedItem();
  }


  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INHERITANCE_TO_DELEGATION);
  }

  protected void doAction() {
    RefactoringSettings.getInstance().INHERITANCE_TO_DELEGATION_DELEGATE_OTHER = myCbGenerateGetter.isSelected();

    final MemberInfo[] selectedMemberInfos = getSelectedMemberInfos();
    final ArrayList<PsiClass> implementedInterfaces = new ArrayList<PsiClass>();
    final ArrayList<PsiMethod> delegatedMethods = new ArrayList<PsiMethod>();

    for (MemberInfo memberInfo : selectedMemberInfos) {
      final PsiElement member = memberInfo.getMember();
      if (member instanceof PsiClass && Boolean.FALSE.equals(memberInfo.getOverrides())) {
        implementedInterfaces.add((PsiClass)member);
      }
      else if (member instanceof PsiMethod) {
        delegatedMethods.add((PsiMethod)member);
      }
    }
    invokeRefactoring(new InheritanceToDelegationProcessor(myProject, myClass,
                                                           getSelectedTargetClass(), getFieldName(),
                                                           getInnerClassName(),
                                                           implementedInterfaces.toArray(new PsiClass[implementedInterfaces.size()]),
                                                           delegatedMethods.toArray(new PsiMethod[delegatedMethods.size()]),
                                                           isGenerateGetter(), isGenerateGetter()));
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridy = 0;
    gbc.gridx = 0;


    gbc.insets = new Insets(4, 8, 0, 8);
    myClassCombo = new JComboBox(mySuperClasses);
    myClassCombo.setRenderer(new ClassCellRenderer());
    gbc.gridwidth = 2;
    final JLabel classComboLabel = new JLabel();
    panel.add(classComboLabel, gbc);
    gbc.gridy++;
    panel.add(myClassCombo, gbc);
    classComboLabel.setText(RefactoringBundle.message("replace.inheritance.from"));

    myClassCombo.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateTargetClass();
        }
      }
    });

    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.insets = new Insets(4, 8, 4, 0);
    final JLabel fieldNameLabel = new JLabel();
    panel.add(fieldNameLabel, gbc);

    myFieldNameField = new NameSuggestionsField(myProject);
    gbc.gridx++;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = new Insets(4, 4, 4, 8);
    gbc.weightx = 1.0;
    panel.add(myFieldNameField.getComponent(), gbc);
    fieldNameLabel.setText(RefactoringBundle.message("field.name"));

    //    if(InheritanceToDelegationUtil.isInnerClassNeeded(myClass, mySuperClass)) {
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.insets = new Insets(4, 8, 4, 0);
    gbc.weightx = 0.0;
    final JLabel innerClassNameLabel = new JLabel();
    panel.add(innerClassNameLabel, gbc);

    /*String[] suggestions = new String[mySuperClasses.length];
    for (int i = 0; i < suggestions.length; i++) {
      suggestions[i] = "My" + mySuperClasses[i].getName();
    }*/
    myInnerClassNameField = new NameSuggestionsField(myProject);
    gbc.gridx++;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = new Insets(4, 4, 4, 8);
    gbc.weightx = 1.0;
    panel.add(myInnerClassNameField.getComponent(), gbc);
    innerClassNameLabel.setText(RefactoringBundle.message("inner.class.name"));

    return panel;
  }


  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.weightx = 1.0;

    gbc.weighty = 1.0;
    gbc.gridwidth = 1;
    gbc.insets = new Insets(4, 8, 4, 4);

    myMemberSelectionPanel = new MemberSelectionPanel(RefactoringBundle.message("delegate.members"), new MemberInfo[0], null);
    panel.add(myMemberSelectionPanel, gbc);
    MyMemberInfoModel memberInfoModel = new InheritanceToDelegationDialog.MyMemberInfoModel();
    myMemberSelectionPanel.getTable().setMemberInfoModel(memberInfoModel);
    myMemberSelectionPanel.getTable().addMemberInfoChangeListener(memberInfoModel);


    gbc.gridy++;
    gbc.insets = new Insets(4, 8, 0, 8);
    gbc.weighty = 0.0;
    myCbGenerateGetter = new JCheckBox(RefactoringBundle.message("generate.getter.for.delegated.component"));
    myCbGenerateGetter.setFocusable(false);
    panel.add(myCbGenerateGetter, gbc);
    myCbGenerateGetter.setSelected(RefactoringSettings.getInstance().INHERITANCE_TO_DELEGATION_DELEGATE_OTHER);
    updateTargetClass();

    return panel;
  }

  private void updateTargetClass() {
    final PsiClass targetClass = getSelectedTargetClass();
    PsiManager psiManager = myClass.getManager();
    PsiType superType = psiManager.getElementFactory().createType(targetClass);
    SuggestedNameInfo suggestedNameInfo =
      CodeStyleManager.getInstance(psiManager.getProject()).suggestVariableName(VariableKind.FIELD, null, null, superType);
    myFieldNameField.setSuggestions(suggestedNameInfo.names);
    myInnerClassNameField.getComponent().setEnabled(InheritanceToDelegationUtil.isInnerClassNeeded(myClass, targetClass));
    @NonNls final String suggestion = "My" + targetClass.getName();
    myInnerClassNameField.setSuggestions(new String[]{suggestion});

    final NameSuggestionsField.DataChanged listener = new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    };
    myInnerClassNameField.addDataChangedListener(listener);
    myFieldNameField.addDataChangedListener(listener);

    myMemberSelectionPanel.getTable().setMemberInfos(myBasesToMemberInfos.get(targetClass));
    myMemberSelectionPanel.getTable().fireExternalDataChange();
  }

  private class MyMemberInfoModel implements MemberInfoModel {
    final HashMap<PsiClass,InterfaceMemberDependencyGraph> myGraphs;

    public MyMemberInfoModel() {
      myGraphs = new HashMap<PsiClass, InterfaceMemberDependencyGraph>();
      for (PsiClass superClass : mySuperClasses) {
        myGraphs.put(superClass, new InterfaceMemberDependencyGraph(superClass));
      }
    }

    public boolean isMemberEnabled(MemberInfo memberInfo) {
      if (getGraph().getDependent().contains(memberInfo.getMember())) {
        return false;
      }
      else {
        return true;
      }
    }

    public boolean isCheckedWhenDisabled(MemberInfo member) {
      return true;
    }

    public boolean isAbstractEnabled(MemberInfo member) {
      return false;
    }

    public boolean isAbstractWhenDisabled(MemberInfo member) {
      return false;
    }

    public Boolean isFixedAbstract(MemberInfo member) {
      return null;
    }

    public int checkForProblems(@NotNull MemberInfo member) {
      return OK;
    }

    public String getTooltipText(MemberInfo member) {
      return null;
    }

    public void memberInfoChanged(MemberInfoChange event) {
      final MemberInfo[] changedMembers = event.getChangedMembers();

      for (MemberInfo changedMember : changedMembers) {
        getGraph().memberChanged(changedMember);
      }
    }

    private InterfaceMemberDependencyGraph getGraph() {
      return myGraphs.get(getSelectedTargetClass());
    }
  }
}
