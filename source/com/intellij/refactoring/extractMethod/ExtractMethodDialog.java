package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.ui.VisibilityPanel;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;


class ExtractMethodDialog extends DialogWrapper {
  private final Project myProject;
  private final PsiType myReturnType;
  private final PsiTypeParameterList myTypeParameterList;
  private final PsiType[] myExceptions;
  private final boolean myStaticFlag;
  private final boolean myCanBeStatic;
  private final String myHelpId;

  private final JTextField myTfName;
  private final JTextArea mySignatureArea;
  private final JCheckBox myCbMakeStatic;

  private final ParameterTablePanel.VariableData[] myVariableData;
  private final PsiClass myTargetClass;
  private VisibilityPanel myVisibilityPanel;


  // TODO : choose visibility?
  public ExtractMethodDialog(Project project,
                             PsiClass targetClass, final PsiVariable[] inputVariables, PsiType returnType,
                             PsiTypeParameterList typeParameterList, PsiType[] exceptions, boolean isStatic, boolean canBeStatic, String initialMethodName,
                             String title, String helpId) {
    super(project, true);
    myProject = project;
    myTargetClass = targetClass;
    myReturnType = returnType;
    myTypeParameterList = typeParameterList;
    myExceptions = exceptions;
    myStaticFlag = isStatic;
    myCanBeStatic = canBeStatic;

    final java.util.List variableData = new ArrayList(inputVariables.length);
    for (int i = 0; i < inputVariables.length; i++) {
      PsiVariable var = inputVariables[i];
      String name = var.getName();
      if (!(var instanceof PsiParameter)) {
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
        VariableKind kind = codeStyleManager.getVariableKind(var);
        name = codeStyleManager.variableNameToPropertyName(name, kind);
        name = codeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
      }
      ParameterTablePanel.VariableData data = new ParameterTablePanel.VariableData(var);
      data.name = name;
      data.passAsParameter = true;
      variableData.add(data);
    }
    myVariableData = (ParameterTablePanel.VariableData[]) variableData.toArray(new ParameterTablePanel.VariableData[variableData.size()]);

    setTitle(title);
    myHelpId = helpId;

    // Create UI components

    myTfName = new JTextField(30);
    myTfName.setText(initialMethodName);

    int height = myVariableData.length + 2;
    if (myExceptions.length > 0) {
      height += myExceptions.length + 1;
    }
    mySignatureArea = new JTextArea(height, 30);
    myCbMakeStatic = new NonFocusableCheckBox("Declare static");

    // Initialize UI

    init();
  }

  public boolean isMakeStatic() {
    if (myStaticFlag) return true;
    if (!myCanBeStatic) return false;
    return myCbMakeStatic.isSelected();
  }

  protected Action[] createActions() {
    if (myHelpId != null) {
      return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    } else {
      return new Action[]{getOKAction(), getCancelAction()};
    }
  }

  public String getChoosenMethodName() {
    return myTfName.getText();
  }

  public ParameterTablePanel.VariableData[] getChoosenParameters() {
    return myVariableData;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTfName;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpId);
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP));
    panel.setBorder(IdeBorderFactory.createTitledBorder("Method"));

    JLabel lblName = new JLabel("Name:");
    lblName.setDisplayedMnemonic('N');
    lblName.setLabelFor(myTfName);
    panel.add(lblName);

    panel.add(myTfName);

    myTfName.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        update();
      }
    });
    setOKActionEnabled(false);
    myCbMakeStatic.setMnemonic('s');
    panel.add(myCbMakeStatic);
    if (myStaticFlag || myCanBeStatic) {
      myCbMakeStatic.setEnabled(!myStaticFlag);
      myCbMakeStatic.setSelected(myStaticFlag);
      myCbMakeStatic.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          updateSignature();
        }
      });
    } else {
      myCbMakeStatic.setSelected(false);
      myCbMakeStatic.setEnabled(false);
    }
    setOKActionEnabled(PsiManager.getInstance(myProject).getNameHelper().isIdentifier(myTfName.getText()));

    return panel;
  }
  private void update() {
    updateSignature();
    setOKActionEnabled(PsiManager.getInstance(myProject).getNameHelper().isIdentifier(myTfName.getText()));
  }

  public String getVisibility() {
    return myVisibilityPanel.getVisibility();
  }


  protected JComponent createCenterPanel() {
    myVisibilityPanel = new VisibilityPanel(false);
    myVisibilityPanel.setVisibility(PsiModifier.PRIVATE);
    myVisibilityPanel.addStateChangedListener(new VisibilityPanel.StateChanged() {
      public void visibilityChanged(String newVisibility) {
        updateSignature();
      }
    });
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(createParametersPanel(), BorderLayout.CENTER);
    panel.add(createSignaturePanel(), BorderLayout.SOUTH);
    panel.add(myVisibilityPanel, BorderLayout.EAST);
    return panel;
  }

  private JComponent createParametersPanel() {
    JPanel panel = new ParameterTablePanel(myProject, myVariableData) {
      protected void updateSignature() {
        ExtractMethodDialog.this.updateSignature();
      }

      protected void doEnterAction() {
        ExtractMethodDialog.this.clickDefaultButton();
      }

      protected void doCancelAction() {
        ExtractMethodDialog.this.doCancelAction();
      }
    };
    panel.setBorder(IdeBorderFactory.createTitledBorder("Parameters"));
    return panel;
  }

  private JComponent createSignaturePanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder("Signature Preview"));

    mySignatureArea.setEditable(false);
    mySignatureArea.setBackground(this.getContentPane().getBackground());
    panel.add(mySignatureArea, BorderLayout.CENTER);

    updateSignature();
    Dimension size = mySignatureArea.getPreferredSize();
    mySignatureArea.setMaximumSize(size);
    mySignatureArea.setMinimumSize(size);
    return panel;
  }

  protected void doOKAction() {
    if (!checkMethodConflicts()) {
      return;
    }
    super.doOKAction();
  }

  private boolean checkMethodConflicts() {
    PsiMethod prototype;
    try {
      PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
      prototype = factory.createMethod(
              myTfName.getText().trim(),
              myReturnType
      );
      if (myTypeParameterList != null) prototype.getTypeParameterList().replace(myTypeParameterList);
      for (int idx = 0; idx < myVariableData.length; idx++) {
        ParameterTablePanel.VariableData data = myVariableData[idx];
        if (data.passAsParameter) {
          prototype.getParameterList().add(factory.createParameter(data.name, data.variable.getType()));
        }
      }
      // set the modifiers with which the method is supposed to be created
      prototype.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);
    } catch (IncorrectOperationException e) {
      prototype = null;
    }
    return RefactoringMessageUtil.checkMethodConflicts(
            myTargetClass,
            null,
            prototype
    );
  }

  private void updateSignature() {
    if (mySignatureArea == null) return;
    StringBuffer buffer = new StringBuffer();
    final String visibilityString = VisibilityUtil.getVisibilityString(myVisibilityPanel.getVisibility());
    buffer.append(visibilityString);
    if (buffer.length() > 0) {
      buffer.append(" ");
    }
    if (isMakeStatic()) {
      buffer.append("static ");
    }
    if (myTypeParameterList != null) {
      buffer.append(myTypeParameterList.getText());
      buffer.append(" ");
    }

    buffer.append(PsiFormatUtil.formatType(myReturnType, 0, PsiSubstitutor.EMPTY));
    buffer.append(" ");
    buffer.append(myTfName.getText());
    buffer.append("(");
    int count = 0;
    final String INDENT = "    ";
    for (int idx = 0; idx < myVariableData.length; idx++) {
      ParameterTablePanel.VariableData data = myVariableData[idx];
      if (data.passAsParameter) {
        //String typeAndModifiers = PsiFormatUtil.formatVariable(data.variable,
        //  PsiFormatUtil.SHOW_MODIFIERS | PsiFormatUtil.SHOW_TYPE);
        String type = PsiManager.getInstance(myProject).getElementFactory().createTypeElement(data.variable.getType()).getText();
        if (count > 0) {
          buffer.append(",");
        }
        buffer.append("\n");
        buffer.append(INDENT);
        buffer.append(type);
        buffer.append(" ");
        buffer.append(data.name);
        count++;
      }
    }
    if (count > 0) {
      buffer.append("\n");
    }
    buffer.append(")");
    if (myExceptions.length > 0) {
      buffer.append("\n");
      buffer.append("throws\n");
      for (int i = 0; i < myExceptions.length; i++) {
        PsiType exception = myExceptions[i];
        buffer.append(INDENT);
        buffer.append(PsiFormatUtil.formatType(exception, 0, PsiSubstitutor.EMPTY));
        buffer.append("\n");
      }
    }
    mySignatureArea.setText(buffer.toString());
  }
}