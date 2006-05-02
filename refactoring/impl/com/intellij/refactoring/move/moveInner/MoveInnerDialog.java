/**
 * created at Sep 24, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveInner;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.NonFocusableCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import org.jetbrains.annotations.NotNull;

public class MoveInnerDialog extends RefactoringDialog {
  private Project myProject;
  private PsiClass myInnerClass;
  private final PsiElement myTargetContainer;
  private MoveInnerProcessor myProcessor;

  private EditorTextField myClassNameField = new EditorTextField("");
  private NameSuggestionsField myParameterField;
  private JCheckBox myCbPassOuterClass;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchForTextOccurences;
  private SuggestedNameInfo mySuggestedNameInfo;
  private PsiClass myOuterClass;

  public MoveInnerDialog(Project project, PsiClass innerClass, MoveInnerProcessor processor, final PsiElement targetContainer) {
    super(project, true);
    myProject = project;
    myInnerClass = innerClass;
    myTargetContainer = targetContainer;
    myOuterClass = myInnerClass.getContainingClass();
    myProcessor = processor;
    setTitle(MoveInnerImpl.REFACTORING_NAME);
    init();
  }

  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  public boolean isSearchInNonJavaFiles() {
    return myCbSearchForTextOccurences.isSelected();
  }

  public String getClassName() {
    return myClassNameField.getText().trim();
  }

  public String getParameterName() {
    if (myParameterField != null) {
      return myParameterField.getName();
    }
    else {
      return null;
    }
  }

  public boolean isPassOuterClass() {
    return myCbPassOuterClass.isSelected();
  }

  @NotNull
  public PsiClass getInnerClass() {
    return myInnerClass;
  }

  protected void init() {
    final PsiManager manager = myInnerClass.getManager();
    myClassNameField.setText(myInnerClass.getName());
    myClassNameField.selectAll();

    if (!myInnerClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiType outerType = manager.getElementFactory().createType(myInnerClass.getContainingClass());
      mySuggestedNameInfo =
      CodeStyleManager.getInstance(myProject).suggestVariableName(VariableKind.PARAMETER, null, null, outerType);
      String[] variants = mySuggestedNameInfo.names;
      myParameterField = new NameSuggestionsField(variants, myProject);
      myCbPassOuterClass = new NonFocusableCheckBox();
      myCbPassOuterClass.setSelected(true);
      myCbPassOuterClass.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          myParameterField.setEnabled(myCbPassOuterClass.isSelected());
        }
      });
    }
    else {
      myParameterField = new NameSuggestionsField(new String[]{""}, myProject);
      myParameterField.getComponent().setEnabled(false);
      myCbPassOuterClass = new NonFocusableCheckBox();
      myCbPassOuterClass.setSelected(false);
      myCbPassOuterClass.setEnabled(false);
      myParameterField.setEnabled(false);
    }
    myCbPassOuterClass.setText(RefactoringBundle.message("pass.outer.class.instance.as.parameter"));
    myCbSearchInComments = new NonFocusableCheckBox();
    myCbSearchInComments.setText(RefactoringBundle.getSearchInCommentsAndStringsText());
    myCbSearchForTextOccurences = new NonFocusableCheckBox();
    myCbSearchForTextOccurences.setText(RefactoringBundle.getSearchForTextOccurrencesText());
    super.init();
  }

  public JComponent getPreferredFocusedComponent() {
    return myClassNameField;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.move.moveInner.MoveInnerDialog";
  }

  protected JComponent createNorthPanel() {
    Box options = Box.createVerticalBox();

    JPanel panel = new JPanel(new BorderLayout());
    JLabel label = new JLabel();
    label.setText(RefactoringBundle.message("class.name.prompt"));
    panel.add(label, BorderLayout.NORTH);
    panel.add(myClassNameField, BorderLayout.CENTER);
    options.add(panel);

    options.add(Box.createVerticalStrut(10));

    panel = new JPanel(new BorderLayout());
    label = new JLabel();
    label.setText(RefactoringBundle.message("parameter.name.prompt"));

    panel.add(label, BorderLayout.NORTH);
    panel.add(myParameterField.getComponent(), BorderLayout.CENTER);
    options.add(panel);

    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(myCbPassOuterClass, BorderLayout.NORTH);
    _panel.add(panel, BorderLayout.CENTER);
    _panel.add(Box.createHorizontalStrut(15), BorderLayout.WEST);
    options.add(_panel);
    options.add(Box.createVerticalStrut(10));

    if (myCbPassOuterClass.isEnabled()) {
      final boolean thisNeeded = MoveInstanceMembersUtil.getThisClassesToMembers(myInnerClass).containsKey(myOuterClass);
      myCbPassOuterClass.setSelected(thisNeeded);
      myParameterField.setEnabled(thisNeeded);
    }

    final Box searchOptions = Box.createHorizontalBox();
    searchOptions.add(myCbSearchInComments);
    searchOptions.add(Box.createHorizontalStrut(10));
    searchOptions.add(myCbSearchForTextOccurences);
    searchOptions.add(Box.createHorizontalGlue());
    options.add(searchOptions);

    myCbPassOuterClass.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        boolean selected = myCbPassOuterClass.isSelected();
        myParameterField.getComponent().setEnabled(selected);
      }
    });

    panel = new JPanel(new BorderLayout());
    panel.add(options, BorderLayout.CENTER);
    return panel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  @NotNull
  public PsiElement getTargetContainer() {
    return myTargetContainer;
  }

  protected void doAction() {
    String message = null;
    final String className = getClassName();
    final String parameterName = getParameterName();
    PsiManager manager = PsiManager.getInstance(myProject);
    if ("".equals(className)) {
      message = RefactoringBundle.message("no.class.name.specified");
    }
    else if (!manager.getNameHelper().isIdentifier(className)) {
      message = RefactoringMessageUtil.getIncorrectIdentifierMessage(className);
    }
    else {
      if (myCbPassOuterClass.isSelected()) {
        if ("".equals(parameterName)) {
          message = RefactoringBundle.message("no.parameter.name.specified");
        }
        else if (!manager.getNameHelper().isIdentifier(parameterName)) {
          message = RefactoringMessageUtil.getIncorrectIdentifierMessage(parameterName);
        }
      }
      if (message == null) {
        if (myTargetContainer instanceof PsiClass) {
          PsiClass targetClass = (PsiClass)myTargetContainer;
          PsiClass[] classes = targetClass.getInnerClasses();
          for (PsiClass aClass : classes) {
            if (className.equals(aClass.getName())) {
              message = RefactoringBundle.message("inner.class.exists", className, targetClass.getName());
              break;
            }
          }
        }
        else if (myTargetContainer instanceof PsiDirectory) {
          message = RefactoringMessageUtil.checkCanCreateClass((PsiDirectory)myTargetContainer, className);
        }
      }
    }

    if (message != null) {
      CommonRefactoringUtil.showErrorMessage(
        MoveInnerImpl.REFACTORING_NAME,
        message,
        HelpID.MOVE_INNER_UPPER,
        myProject);
      return;
    }

    RefactoringSettings.getInstance().MOVE_INNER_PREVIEW_USAGES = isPreviewUsages();
    if (myCbPassOuterClass.isSelected() && mySuggestedNameInfo != null) {
      mySuggestedNameInfo.nameChoosen(getParameterName());
    }

    myProcessor.setup(getInnerClass(), className, isPassOuterClass(), parameterName,
                      isSearchInComments(), isSearchInNonJavaFiles(), getTargetContainer());
    invokeRefactoring(myProcessor);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MOVE_INNER_UPPER);
  }
}
