package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.ui.VisibilityPanel;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;


class ExtractMethodDialog extends DialogWrapper {
  private final Project myProject;
  private final PsiType myReturnType;
  private final PsiTypeParameterList myTypeParameterList;
  private final PsiType[] myExceptions;
  private final boolean myStaticFlag;
  private final boolean myCanBeStatic;
  private PsiElement[] myElementsToExtract;
  private final String myHelpId;

  private final EditorTextField myNameField;
  private final JTextArea mySignatureArea;
  private final JCheckBox myCbMakeStatic;
  private JCheckBox myCbMakeVarargs;
  private JCheckBox myCbChainedConstructor;

  private final ParameterTablePanel.VariableData[] myVariableData;
  private final PsiClass myTargetClass;
  private VisibilityPanel myVisibilityPanel;
  private boolean myWasStatic;
  private boolean myDefaultVisibility = true;
  private boolean myChangingVisibility;


  public ExtractMethodDialog(Project project,
                             PsiClass targetClass, final PsiVariable[] inputVariables, PsiType returnType,
                             PsiTypeParameterList typeParameterList, PsiType[] exceptions, boolean isStatic, boolean canBeStatic,
                             final boolean canBeChainedConstructor,
                             String initialMethodName,
                             String title,
                             String helpId,
                             final PsiElement[] elementsToExtract) {
    super(project, true);
    myProject = project;
    myTargetClass = targetClass;
    myReturnType = returnType;
    myTypeParameterList = typeParameterList;
    myExceptions = exceptions;
    myStaticFlag = isStatic;
    myCanBeStatic = canBeStatic;
    myElementsToExtract = elementsToExtract;

    final List<ParameterTablePanel.VariableData> variableData = new ArrayList<ParameterTablePanel.VariableData>(inputVariables.length);
    boolean canBeVarargs = false;
    for (PsiVariable var : inputVariables) {
      String name = var.getName();
      if (!(var instanceof PsiParameter)) {
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
        VariableKind kind = codeStyleManager.getVariableKind(var);
        name = codeStyleManager.variableNameToPropertyName(name, kind);
        name = codeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
      }
      PsiType type = var.getType();
      if (type instanceof PsiEllipsisType) {
        if (var == inputVariables[inputVariables.length - 1]) {
          myWasStatic = true;
        }
        type = ((PsiEllipsisType)type).toArrayType();
      }

      canBeVarargs |= type instanceof PsiArrayType;

      ParameterTablePanel.VariableData data = new ParameterTablePanel.VariableData(var, type);
      data.name = name;
      data.passAsParameter = true;
      variableData.add(data);
    }
    myVariableData = variableData.toArray(new ParameterTablePanel.VariableData[variableData.size()]);

    setTitle(title);
    myHelpId = helpId;

    // Create UI components

    myNameField = new EditorTextField(initialMethodName);
    //myTfName.setText(initialMethodName);

    int height = myVariableData.length + 2;
    if (myExceptions.length > 0) {
      height += myExceptions.length + 1;
    }
    mySignatureArea = new JTextArea(height, 30);
    myCbMakeStatic = new NonFocusableCheckBox();
    myCbMakeStatic.setText(RefactoringBundle.message("declare.static.checkbox"));
    if (canBeChainedConstructor) {
      myCbChainedConstructor = new NonFocusableCheckBox(RefactoringBundle.message("extract.chained.constructor.checkbox"));
    }
    if (canBeVarargs) {
      myCbMakeVarargs = new NonFocusableCheckBox(RefactoringBundle.message("declare.varargs.checkbox"));
    }

    // Initialize UI

    init();
  }

  protected boolean areTypesDirected() {
    return true;
  }

  public boolean isMakeStatic() {
    if (myStaticFlag) return true;
    if (!myCanBeStatic) return false;
    return myCbMakeStatic.isSelected();
  }

  public boolean isChainedConstructor() {
    return myCbChainedConstructor != null && myCbChainedConstructor.isSelected();  
  }

  protected Action[] createActions() {
    if (myHelpId != null) {
      return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    } else {
      return new Action[]{getOKAction(), getCancelAction()};
    }
  }

  public String getChosenMethodName() {
    return myNameField.getText();
  }

  public ParameterTablePanel.VariableData[] getChosenParameters() {
    return myVariableData;
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpId);
  }

  protected void doOKAction() {
    Set<String> conflicts = new HashSet<String>();
    checkMethodConflicts(conflicts);
    if (conflicts.size() > 0) {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
      conflictsDialog.show();
      if (!conflictsDialog.isOK()) return;
    }

    if (myCbMakeVarargs != null && myCbMakeVarargs.isSelected()) {
      final ParameterTablePanel.VariableData data = myVariableData[myVariableData.length - 1];
      if (data.type instanceof PsiArrayType) {
        data.type = new PsiEllipsisType(((PsiArrayType)data.type).getComponentType());
      }
    }
    super.doOKAction();
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP));
    panel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("extract.method.method.panel.border")));

    JLabel nameLabel = new JLabel();
    nameLabel.setText(RefactoringBundle.message("name.prompt"));
    panel.add(nameLabel);

    panel.add(myNameField);
    nameLabel.setLabelFor(myNameField);

    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        update();
      }
    });

    setOKActionEnabled(false);
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

    if (myCbMakeVarargs != null) {
      updateVarargsEnabled();
      myCbMakeVarargs.setSelected(myWasStatic);
      panel.add(myCbMakeVarargs);
    }
    if (myCbChainedConstructor != null) {
      panel.add(myCbChainedConstructor);
      myCbChainedConstructor.addItemListener(new ItemListener() {
        public void itemStateChanged(final ItemEvent e) {
          if (myDefaultVisibility) {
            myChangingVisibility = true;
            try {
              if (isChainedConstructor()) {
                myVisibilityPanel.setVisibility(VisibilityUtil.getVisibilityModifier(myTargetClass.getModifierList()));
              }
              else {
                myVisibilityPanel.setVisibility(PsiModifier.PRIVATE);
              }
            }
            finally {
              myChangingVisibility = false;
            }
          }
          update();
        }
      });
    }
    setOKActionEnabled(PsiManager.getInstance(myProject).getNameHelper().isIdentifier(myNameField.getText()));

    return panel;
  }

  private void updateVarargsEnabled() {
    if (myCbMakeVarargs != null) {
      myCbMakeVarargs.setEnabled(myVariableData[myVariableData.length - 1].type instanceof PsiArrayType);
    }
  }

  private void update() {
    myNameField.setEnabled(!isChainedConstructor());
    if (myCbMakeStatic != null) {
      myCbMakeStatic.setEnabled(!isChainedConstructor());
    }
    updateSignature();
    setOKActionEnabled(PsiManager.getInstance(myProject).getNameHelper().isIdentifier(myNameField.getText()) ||
                       isChainedConstructor());
  }

  public String getVisibility() {
    return myVisibilityPanel.getVisibility();
  }


  protected JComponent createCenterPanel() {
    myVisibilityPanel = new VisibilityPanel(false, false);
    myVisibilityPanel.setVisibility(PsiModifier.PRIVATE);
    myVisibilityPanel.addStateChangedListener(new VisibilityPanel.StateChanged() {
      public void visibilityChanged(String newVisibility) {
        updateSignature();
        if (!myChangingVisibility) {
          myDefaultVisibility = false;
        }
      }
    });
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(createParametersPanel(), BorderLayout.CENTER);
    panel.add(createSignaturePanel(), BorderLayout.SOUTH);
    panel.add(myVisibilityPanel, BorderLayout.EAST);
    return panel;
  }

  private JComponent createParametersPanel() {
    JPanel panel = new ParameterTablePanel(myProject, myVariableData, myElementsToExtract) {
      protected void updateSignature() {
        updateVarargsEnabled();
        ExtractMethodDialog.this.updateSignature();
      }

      protected void doEnterAction() {
        clickDefaultButton();
      }

      protected void doCancelAction() {
        ExtractMethodDialog.this.doCancelAction();
      }

      protected boolean areTypesDirected() {
        return ExtractMethodDialog.this.areTypesDirected();
      }
    };
    panel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("parameters.border.title")));
    return panel;
  }

  private JComponent createSignaturePanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("signature.preview.border.title")));

    mySignatureArea.setEditable(false);
    mySignatureArea.setBackground(getContentPane().getBackground());
    panel.add(mySignatureArea, BorderLayout.CENTER);

    updateSignature();
    Dimension size = mySignatureArea.getPreferredSize();
    mySignatureArea.setMaximumSize(size);
    mySignatureArea.setMinimumSize(size);
    return panel;
  }

  private void updateSignature() {
    if (mySignatureArea == null) return;
    @NonNls StringBuffer buffer = new StringBuffer();
    final String visibilityString = VisibilityUtil.getVisibilityString(myVisibilityPanel.getVisibility());
    buffer.append(visibilityString);
    if (buffer.length() > 0) {
      buffer.append(" ");
    }
    if (isMakeStatic() && !isChainedConstructor()) {
      buffer.append("static ");
    }
    if (myTypeParameterList != null) {
      buffer.append(myTypeParameterList.getText());
      buffer.append(" ");
    }

    if (isChainedConstructor()) {
      buffer.append(myTargetClass.getName());
    }
    else {
      buffer.append(PsiFormatUtil.formatType(myReturnType, 0, PsiSubstitutor.EMPTY));
      buffer.append(" ");
      buffer.append(myNameField.getText());
    }
    buffer.append("(");
    int count = 0;
    final String INDENT = "    ";
    for (int i = 0; i < myVariableData.length; i++) {
      ParameterTablePanel.VariableData data = myVariableData[i];
      if (data.passAsParameter) {
        //String typeAndModifiers = PsiFormatUtil.formatVariable(data.variable,
        //  PsiFormatUtil.SHOW_MODIFIERS | PsiFormatUtil.SHOW_TYPE);
        PsiType type = data.type;
        if (i == myVariableData.length - 1 && type instanceof PsiArrayType && myCbMakeVarargs.isSelected()) {
          type = new PsiEllipsisType(((PsiArrayType)type).getComponentType());
        }

        String typeText = type.getPresentableText();
        if (count > 0) {
          buffer.append(",");
        }
        buffer.append("\n");
        buffer.append(INDENT);
        buffer.append(typeText);
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
      for (PsiType exception : myExceptions) {
        buffer.append(INDENT);
        buffer.append(PsiFormatUtil.formatType(exception, 0, PsiSubstitutor.EMPTY));
        buffer.append("\n");
      }
    }
    mySignatureArea.setText(buffer.toString());
  }

  private void checkMethodConflicts(Collection<String> conflicts) {
    PsiMethod prototype;
    try {
      PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
      prototype = factory.createMethod(
              myNameField.getText().trim(),
              myReturnType
      );
      if (myTypeParameterList != null) prototype.getTypeParameterList().replace(myTypeParameterList);
      for (ParameterTablePanel.VariableData data : myVariableData) {
        if (data.passAsParameter) {
          prototype.getParameterList().add(factory.createParameter(data.name, data.type));
        }
      }
      // set the modifiers with which the method is supposed to be created
      prototype.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);
    } catch (IncorrectOperationException e) {
      return;
    }

    ConflictsUtil.checkMethodConflicts(
      myTargetClass,
      null,
      prototype, conflicts);
  }
}