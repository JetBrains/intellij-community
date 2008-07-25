package com.intellij.refactoring.extractMethodObject;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.extractMethod.AbstractExtractDialog;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;


public class ExtractMethodObjectDialog extends AbstractExtractDialog {
  private final Project myProject;
  private final PsiType myReturnType;
  private final PsiTypeParameterList myTypeParameterList;
  private final PsiType[] myExceptions;
  private final boolean myStaticFlag;
  private final boolean myCanBeStatic;
  private PsiElement[] myElementsToExtract;

  private final ParameterTablePanel.VariableData[] myVariableData;
  private final PsiClass myTargetClass;
  private boolean myWasStatic;


  private JRadioButton myCreateInnerClassRb;
  private JRadioButton myCreateAnonymousClassWrapperRb;
  private JTextArea mySignatureArea;
  private JCheckBox myCbMakeStatic;
  private JCheckBox myCbMakeVarargs;
  private JCheckBox myCbMakeVarargsAnonymous;

  private JPanel myWholePanel;
  private JPanel myParametersTableContainer;
  private JRadioButton myPrivateRadioButton;
  private JRadioButton myProtectedRadioButton;
  private JRadioButton myPackageLocalRadioButton;
  private JRadioButton myPublicRadioButton;

  private EditorTextField myInnerClassName;
  private EditorTextField myMethodName;

  private JPanel myInnerClassPanel;
  private JPanel myAnonymousClassPanel;
  private ButtonGroup myVisibilityGroup;


  public ExtractMethodObjectDialog(Project project, PsiClass targetClass, final PsiVariable[] inputVariables, PsiType returnType,
                                   PsiTypeParameterList typeParameterList, PsiType[] exceptions, boolean isStatic, boolean canBeStatic,
                                   final PsiElement[] elementsToExtract) {
    super(project);
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
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
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

    setTitle(ExtractMethodObjectProcessor.REFACTORING_NAME);

    // Create UI components



    int height = myVariableData.length + 2;
    if (myExceptions.length > 0) {
      height += myExceptions.length + 1;
    }


    myCbMakeVarargs.setVisible(canBeVarargs);
    myCbMakeVarargsAnonymous.setVisible(canBeVarargs);

    // Initialize UI
     init();

  }

  public boolean isMakeStatic() {
    if (myStaticFlag) return true;
    if (!myCanBeStatic) return false;
    return myCbMakeStatic.isSelected();
  }

  public boolean isChainedConstructor() {
    return false;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public String getChosenMethodName() {
    return myCreateInnerClassRb.isSelected() ? myInnerClassName.getText() : myMethodName.getText();
  }

  public ParameterTablePanel.VariableData[] getChosenParameters() {
    return myVariableData;
  }

  public JComponent getPreferredFocusedComponent() {
    return myInnerClassName;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.EXTRACT_METHOD_OBJECT);
  }

  protected void doOKAction() {
    Set<String> conflicts = new HashSet<String>();
    if (myCreateInnerClassRb.isSelected()) {
      if (myTargetClass.findInnerClassByName(myInnerClassName.getText(), false) != null) {
        conflicts.add("Inner class " + myInnerClassName.getText() + " already defined in class " + myTargetClass.getName());
      }
    }
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

  private void updateVarargsEnabled() {
    final boolean enabled = myVariableData.length > 0 && myVariableData[myVariableData.length - 1].type instanceof PsiArrayType;
    if (myCreateInnerClassRb.isSelected()) {
      myCbMakeVarargs.setEnabled(enabled);
    } else {
      myCbMakeVarargsAnonymous.setEnabled(enabled);
    }
  }

  private void update() {
    myCbMakeStatic.setEnabled(myCanBeStatic && !myStaticFlag);
    updateSignature();
    final PsiNameHelper helper = JavaPsiFacade.getInstance(myProject).getNameHelper();
    setOKActionEnabled((myCreateInnerClassRb.isSelected() && helper.isIdentifier(myInnerClassName.getText())) ||
                        (!myCreateInnerClassRb.isSelected() && helper.isIdentifier(myMethodName.getText())));
  }

  public String getVisibility() {
    if (myPublicRadioButton.isSelected()) {
      return PsiModifier.PUBLIC;
    }
    if (myPackageLocalRadioButton.isSelected()) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (myProtectedRadioButton.isSelected()) {
      return PsiModifier.PROTECTED;
    }
    if (myPrivateRadioButton.isSelected()) {
      return PsiModifier.PRIVATE;
    }
    return null;
  }


  protected JComponent createCenterPanel() {
    myCreateInnerClassRb.setSelected(true);
    enable(true);
    final ActionListener enableDisableListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        enable(myCreateInnerClassRb.isSelected());
      }
    };
    myCreateInnerClassRb.addActionListener(enableDisableListener);
    myCreateAnonymousClassWrapperRb.addActionListener(enableDisableListener);
    myParametersTableContainer.add(createParametersPanel(), BorderLayout.CENTER);

    final ActionListener updateSugnatureListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateSignature();
      }
    };

    if (myStaticFlag || myCanBeStatic) {
      myCbMakeStatic.setEnabled(!myStaticFlag);
      myCbMakeStatic.setSelected(myStaticFlag);

      myCbMakeStatic.addActionListener(updateSugnatureListener);
    } else {
      myCbMakeStatic.setSelected(false);
      myCbMakeStatic.setEnabled(false);
    }

    updateVarargsEnabled();

    myCbMakeVarargs.setSelected(myWasStatic);
    myCbMakeVarargs.addActionListener(updateSugnatureListener);

    myCbMakeVarargsAnonymous.setSelected(myWasStatic);
    myCbMakeVarargsAnonymous.addActionListener(updateSugnatureListener);

    final com.intellij.openapi.editor.event.DocumentAdapter nameListener = new com.intellij.openapi.editor.event.DocumentAdapter() {
      @Override
      public void documentChanged(final DocumentEvent e) {
        update();
      }
    };
    myInnerClassName.getDocument().addDocumentListener(nameListener);
    myMethodName.getDocument().addDocumentListener(nameListener);

    myPrivateRadioButton.setSelected(true);

    myCreateInnerClassRb.addActionListener(updateSugnatureListener);
    myCreateAnonymousClassWrapperRb.addActionListener(updateSugnatureListener);

    final Enumeration<AbstractButton> visibilities = myVisibilityGroup.getElements();
    while(visibilities.hasMoreElements()) {
      visibilities.nextElement().addActionListener(updateSugnatureListener);
    }

    update();

    return myWholePanel;
  }

  private void enable(boolean innerClassSelected){
    UIUtil.setEnabled(myInnerClassPanel, innerClassSelected, true);
    UIUtil.setEnabled(myAnonymousClassPanel, !innerClassSelected, true);
  }

  private JComponent createParametersPanel() {
    return new ParameterTablePanel(myProject, myVariableData, myElementsToExtract) {
      protected void updateSignature() {
        updateVarargsEnabled();
        ExtractMethodObjectDialog.this.updateSignature();
      }

      protected void doEnterAction() {
        clickDefaultButton();
      }

      protected void doCancelAction() {
        ExtractMethodObjectDialog.this.doCancelAction();
      }

      protected boolean areTypesDirected() {
        return true;
      }
    };
  }

  protected void updateSignature() {
    if (mySignatureArea == null) return;
    @NonNls StringBuffer buffer = getSignature();
    mySignatureArea.setText(buffer.toString());
  }

  protected StringBuffer getSignature() {
    final String INDENT = "    ";
    @NonNls StringBuffer buffer = new StringBuffer();
    final String visibilityString = VisibilityUtil.getVisibilityString(getVisibility());
    if (myCreateInnerClassRb.isSelected()) {
      buffer.append(visibilityString);
      if (buffer.length() > 0) {
        buffer.append(" ");
      }
      if (isMakeStatic()) {
        buffer.append("static ");
      }
      buffer.append("class ");
      buffer.append(myInnerClassName.getText());
      if (myTypeParameterList != null) {
        buffer.append(myTypeParameterList.getText());
        buffer.append(" ");
      }
      buffer.append("{\n");
      buffer.append(INDENT);
      buffer.append("public ");
      buffer.append(myInnerClassName.getText());
      methodSignature(INDENT, buffer);
      buffer.append("\n}");
    } else {
      buffer.append("new Object(){\n");
      buffer.append(INDENT);
      buffer.append("private ");
      buffer.append(PsiFormatUtil.formatType(myReturnType, 0, PsiSubstitutor.EMPTY));
      buffer.append(" ");
      buffer.append(myMethodName.getText());
      methodSignature(INDENT, buffer);
      buffer.append("\n}.");
      buffer.append(myMethodName.getText());
      buffer.append("(");
      buffer.append(StringUtil.join(Arrays.asList(myVariableData), new Function<ParameterTablePanel.VariableData, String>() {
        public String fun(final ParameterTablePanel.VariableData variableData) {
          return variableData.name;
        }
      }, ", "));
      buffer.append(")");
    }

    return buffer;
  }

  private void methodSignature(final String INDENT, final StringBuffer buffer) {
    buffer.append("(");
    int count = 0;

    for (int i = 0; i < myVariableData.length; i++) {
      ParameterTablePanel.VariableData data = myVariableData[i];
      if (data.passAsParameter) {
        //String typeAndModifiers = PsiFormatUtil.formatVariable(data.variable,
        //  PsiFormatUtil.SHOW_MODIFIERS | PsiFormatUtil.SHOW_TYPE);
        PsiType type = data.type;
        if (i == myVariableData.length - 1 && type instanceof PsiArrayType && ((myCreateInnerClassRb.isSelected() && myCbMakeVarargs.isSelected()) || (myCreateAnonymousClassWrapperRb.isSelected() && myCbMakeVarargsAnonymous.isSelected()))) {
          type = new PsiEllipsisType(((PsiArrayType)type).getComponentType());
        }

        String typeText = type.getPresentableText();
        if (count > 0) {
          buffer.append(", ");
        }
        buffer.append(typeText);
        buffer.append(" ");
        buffer.append(data.name);
        count++;
      }
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
    buffer.append("{}");
  }

  public boolean createInnerClass() {
    return myCreateInnerClassRb.isSelected();
  }
}