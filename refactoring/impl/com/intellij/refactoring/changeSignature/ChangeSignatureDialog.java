package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.inCallers.CallerChooser;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.*;
import com.intellij.ui.*;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.Tree;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public class ChangeSignatureDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.changeSignature.ChangeSignatureDialog");
  private Project myProject;
  private PsiMethod myMethod;
  private final boolean myAllowDelegation;
  private EditorTextField myNameField;
  private EditorTextField myReturnTypeField;
  private JTable myParametersTable;
  private ParameterTableModel myParametersTableModel;
  private JTextArea mySignatureArea;
  private Alarm myUpdateSignatureAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private VisibilityPanel myVisibilityPanel;
  private PsiTypeCodeFragment myReturnTypeCodeFragment;
  private DelegationPanel myDelegationPanel;
  private JTable myExceptionsTable;
  private ExceptionsTableModel myExceptionsTableModel;
  private JButton myPropagateParamChangesButton;
  private JButton myPropagateExnChangesButton;
  private Set<PsiMethod> myMethodsToPropagateParameters = null;
  private Set<PsiMethod> myMethodsToPropagateExceptions = null;

  private Tree myExceptionPropagationTree;
  private Tree myParameterPropagationTree;

  public ChangeSignatureDialog(Project project, final PsiMethod method, boolean allowDelegation) {
    super(project, true);
    myProject = project;
    myMethod = method;
    myParametersTableModel = new ParameterTableModel(myMethod.getParameterList(), this);
    myExceptionsTableModel = new ExceptionsTableModel(myMethod.getThrowsList());
    myAllowDelegation = allowDelegation;

    setParameterInfos(getParameterInfos(method));
    myExceptionsTableModel.setTypeInfos(method);

    setTitle(ChangeSignatureHandler.REFACTORING_NAME);
    init();
    doUpdateSignature();
  }

  public void setParameterInfos(List<ParameterInfo> parameterInfos) {
    myParametersTableModel.setParameterInfos(parameterInfos, myMethod.getParameterList());
    updateSignature();
  }

  private static List<ParameterInfo> getParameterInfos(PsiMethod method) {
    final ArrayList<ParameterInfo> result = new ArrayList<ParameterInfo>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      ParameterInfo info = new ParameterInfo(i, parameter.getName(), parameter.getType());
      info.defaultValue = "";
      result.add(info);
    }
    return result;
  }

  public String getMethodName() {
    if (myNameField != null) {
      return myNameField.getText().trim();
    }
    else {
      return myMethod.getName();
    }
  }

  public CanonicalTypes.Type getReturnType() {
    if (myReturnTypeField != null) {
      try {
        final PsiType type = myReturnTypeCodeFragment.getType();
        return CanonicalTypes.createTypeWrapper(type);
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return null;
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        return null;
      }
    }

    return null;
  }

  public String getVisibility() {
    if (myVisibilityPanel != null) {
      return myVisibilityPanel.getVisibility();
    }
    else {
      return VisibilityUtil.getVisibilityModifier(myMethod.getModifierList());
    }
  }

  public ParameterInfo[] getParameters() {
    return myParametersTableModel.getParameters();
  }

  public ThrownExceptionInfo[] getExceptions() {
    return myExceptionsTableModel.getThrownExceptions();
  }

  public boolean isGenerateDelegate() {
    return myAllowDelegation && myDelegationPanel.isGenerateDelegate();
  }

  public JComponent getPreferredFocusedComponent() {
    return myParametersTableModel.getRowCount() > 0 ? (JComponent)myParametersTable : myNameField;
  }


  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP));

    JPanel top = new JPanel(new BorderLayout());
    if (myAllowDelegation)
    {
      myDelegationPanel = createDelegationPanel();
      top.add(myDelegationPanel, BorderLayout.WEST);
    }

    JPanel propagatePanel = new JPanel();
    myPropagateParamChangesButton = new JButton(RefactoringBundle.message("changeSignature.propagate.parameters.title"));
    myPropagateParamChangesButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        new CallerChooser(myMethod, RefactoringBundle.message("changeSignature.parameter.caller.chooser"), myParameterPropagationTree) {
          protected void callersChosen(Set<PsiMethod> callers) {
            myMethodsToPropagateParameters = callers;
            myParameterPropagationTree = getTree();
          }
        }.show();
      }
    });
    propagatePanel.add(myPropagateParamChangesButton);

    myPropagateExnChangesButton = new JButton(RefactoringBundle.message("changeSignature.propagate.exceptions.title"));
    myPropagateExnChangesButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        new CallerChooser(myMethod, RefactoringBundle.message("changeSignature.exception.caller.chooser"), myExceptionPropagationTree) {
          protected void callersChosen(Set<PsiMethod> callers) {
            myMethodsToPropagateExceptions = callers;
            myExceptionPropagationTree = getTree();
          }
        }.show();
      }
    });
    propagatePanel.add(myPropagateExnChangesButton);
    top.add(propagatePanel, BorderLayout.EAST);

    panel.add(top);
    if (!myMethod.isConstructor()) {
      JLabel namePrompt = new JLabel();
      myNameField = new EditorTextField(myMethod.getName());
      namePrompt.setText(RefactoringBundle.message("name.prompt"));
      panel.add(namePrompt);
      panel.add(myNameField);

      JLabel typePrompt = new JLabel();
      panel.add(typePrompt);
      final PsiElementFactory factory = myMethod.getManager().getElementFactory();
      myReturnTypeCodeFragment = factory.createTypeCodeFragment(myMethod.getReturnTypeElement().getText(), myMethod.getParameterList(), true, true);
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(myReturnTypeCodeFragment);
      myReturnTypeField = new EditorTextField(document, myProject, StdFileTypes.JAVA);
      typePrompt.setText(RefactoringBundle.message("changeSignature.return.type.prompt"));
      panel.add(myReturnTypeField);

      final DocumentListener documentListener = new DocumentListener() {
        public void beforeDocumentChange(DocumentEvent event) {
        }

        public void documentChanged(DocumentEvent event) {
          updateSignature();
        }
      };

      myNameField.addDocumentListener(documentListener);
      myReturnTypeField.addDocumentListener(documentListener);
    }

    return panel;
  }

  private DelegationPanel createDelegationPanel() {
    return new DelegationPanel() {
      protected void stateModified() {
        myParametersTableModel.fireTableDataChanged();
        configureParameterTableEditors();
      }
    };
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel subPanel = new JPanel(new BorderLayout());
    subPanel.add(createParametersPanel(), BorderLayout.CENTER);

    if (myMethod.getContainingClass() != null
        && !myMethod.getContainingClass().isInterface()) {
      myVisibilityPanel = new VisibilityPanel(false, false);
      myVisibilityPanel.setVisibility(VisibilityUtil.getVisibilityModifier(myMethod.getModifierList()));
      myVisibilityPanel.addStateChangedListener(new VisibilityPanel.StateChanged() {
        public void visibilityChanged(String newVisibility) {
          updateSignature();
        }
      });
      subPanel.add(myVisibilityPanel, BorderLayout.EAST);
    }

    panel.add(subPanel, BorderLayout.CENTER);

    JPanel subPanel1 = new JPanel(new GridBagLayout());
    subPanel1.add(createExceptionsPanel(), new GridBagConstraints(0, 0, 1, 1, 0.5, 0.0, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(4,4,4,0), 0, 0));
    subPanel1.add(createSignaturePanel(), new GridBagConstraints(1, 0, 1, 1, 0.5, 0.0, GridBagConstraints.EAST, GridBagConstraints.BOTH, new Insets(4,0,4,4), 0, 0));
    panel.add(subPanel1, BorderLayout.SOUTH);

    return panel;
  }

  protected String getDimensionServiceKey() {
    return "refactoring.ChangeSignatureDialog";
  }

  private JPanel createParametersPanel() {
    myParametersTable = new Table(myParametersTableModel);
    myParametersTable.setFocusCycleRoot(true);
    final int minWidth = new JCheckBox().getPreferredSize().width;
    final TableColumn anyVarColumn = myParametersTable.getColumnModel().getColumn(3);
    final int headerWidth = myParametersTable.getFontMetrics(myParametersTable.getFont()).stringWidth(ParameterTableModel.ANY_VAR_COLUMN_NAME) + 8;
    anyVarColumn.setMaxWidth(Math.max(minWidth, headerWidth));
    configureParameterTableEditors();
    return createTablePanelImpl(myParametersTable, myParametersTableModel, RefactoringBundle.message("parameters.border.title"), true);
  }

  private JPanel createExceptionsPanel() {
    myExceptionsTable = new Table(myExceptionsTableModel);
    configureExceptionTableEditors();
    return createTablePanelImpl(myExceptionsTable, myExceptionsTableModel, RefactoringBundle.message("changeSignature.exceptions.panel.border.title"), false);
  }

  private JPanel createTablePanelImpl (JTable table, RowEditableTableModel tableModel, String borderTitle, boolean addMnemonics) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(borderTitle));

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(table);

    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.add(scrollPane, BorderLayout.CENTER);

    tablePanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    panel.add(tablePanel, BorderLayout.CENTER);

    table.setPreferredScrollableViewportSize(new Dimension(450, table.getRowHeight() * 8));
    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().setSelectionInterval(0, 0);
    table.setSurrendersFocusOnKeystroke(true);

    final EditableRowTableManager editableRowTableManager = new EditableRowTableManager(table, tableModel, addMnemonics);
    JPanel buttonsPanel = editableRowTableManager.getButtonsPanel();

    panel.add(buttonsPanel, BorderLayout.EAST);

    tableModel.addTableModelListener(
      new TableModelListener() {
        public void tableChanged(TableModelEvent e) {
          updateSignature();
        }
      }
    );

    return panel;
  }

  private void configureParameterTableEditors() {
    myParametersTable.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    myParametersTable.getColumnModel().getColumn(1).setCellRenderer(new MyCellRenderer());
    myParametersTable.getColumnModel().getColumn(2).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    if (myParametersTable.getColumnCount() == 4) {
      myParametersTable.getColumnModel().getColumn(3).setCellRenderer(new BooleanTableCellRenderer() {
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
          super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (!myParametersTableModel.isCellEditable(row, myParametersTable.convertColumnIndexToModel(column))) {
            setBackground(getBackground().darker());
          }
          return this;
        }
      });
    }
    myParametersTable.getColumnModel().getColumn(0).setCellEditor(new CodeFragmentTableCellEditor(myProject));
    myParametersTable.getColumnModel().getColumn(1).setCellEditor(new MyNameTableCellEditor(myProject));

    myParametersTable.getColumnModel().getColumn(2).setCellEditor(new CodeFragmentTableCellEditor(myProject) {
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        final Component editor = super.getTableCellEditorComponent(table, value, isSelected, row, column);

        if (myCodeFragment instanceof PsiExpressionCodeFragment) {
          final Object valueAt = table.getValueAt(row, 0);
          if (valueAt != null) {
            try {
              final PsiType type = ((PsiTypeCodeFragment)valueAt).getType();
              ((PsiExpressionCodeFragment)myCodeFragment).setExpectedType(type);
            }
            catch (PsiTypeCodeFragment.TypeSyntaxException e) {}
            catch (PsiTypeCodeFragment.NoTypeException e) {}
          }

        }
        return editor;
      }
    });
  }

  private void configureExceptionTableEditors () {
    myExceptionsTable.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    myExceptionsTable.getColumnModel().getColumn(0).setCellEditor(new CodeFragmentTableCellEditor(myProject));
  }

  private void completeVariable(EditorTextField editorTextField, PsiType type) {
    Editor editor = editorTextField.getEditor();
    String prefix = editorTextField.getText();
    if (prefix == null) prefix = "";
    Set<LookupItem> set = new LinkedHashSet<LookupItem>();
    CompletionUtil.completeVariableNameForRefactoring(myProject, set, prefix, type, VariableKind.PARAMETER);

    LookupItem[] lookupItems = set.toArray(new LookupItem[set.size()]);
    editor.getCaretModel().moveToOffset(prefix.length());
    editor.getSelectionModel().removeSelection();
    LookupManager.getInstance(myProject).showLookup(editor, lookupItems, prefix, null, new CharFilter() {
      public int accept(char c, final String prefix) {
        if (Character.isJavaIdentifierPart(c)) return CharFilter.ADD_TO_PREFIX;
        return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
    });
  }

  private JComponent createSignaturePanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("signature.preview.border.title")), IdeBorderFactory.createEmptyBorder(new Insets(4, 4, 4, 4))));

    String s = calculateSignature();
    s = StringUtil.convertLineSeparators(s, "\n");
    int height = new StringTokenizer(s, "\n\r").countTokens() + 2;
    if (height > 10) height = 10;
    mySignatureArea = new JTextArea(height, 50);
    mySignatureArea.setEditable(false);
    mySignatureArea.setBackground(getContentPane().getBackground());
    //mySignatureArea.setFont(myTableFont);
    JScrollPane scrollPane = new JScrollPane(mySignatureArea);
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder(new Insets(0,0,0,0)));
    panel.add(scrollPane, BorderLayout.CENTER);

    updateSignature();
    return panel;
  }

  private void updateSignature() {
    if (mySignatureArea == null) return;

    myUpdateSignatureAlarm.cancelAllRequests();
    myUpdateSignatureAlarm.addRequest(new Runnable() {
      public void run() {
        doUpdateSignature();
        updatePropagateButtons();
      }
    }, 100, ModalityState.stateForComponent(mySignatureArea));
  }

  private void doUpdateSignature() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    String signature = calculateSignature();
    mySignatureArea.setText(signature);
  }

  private void updatePropagateButtons () {
    myPropagateParamChangesButton.setEnabled(!isGenerateDelegate() && mayPropagateParameters());
    myPropagateExnChangesButton.setEnabled(!isGenerateDelegate() && mayPropagateExceptions());
  }

  private boolean mayPropagateExceptions() {
    final ThrownExceptionInfo[] thrownExceptions = myExceptionsTableModel.getThrownExceptions();
    final PsiClassType[] types = myMethod.getThrowsList().getReferencedTypes();
    if (thrownExceptions.length < types.length) return false;
    for (int i = 0; i < types.length; i++) {
      if (thrownExceptions[i].oldIndex != i) return false;
    }
    return true;
  }

  private boolean mayPropagateParameters() {
    final ParameterInfo[] infos = myParametersTableModel.getParameters();
    final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    if (infos.length < parameters.length) return false;
    for (int i = 0; i < parameters.length; i++) {
      if (infos[i].oldParameterIndex != i) return false;
    }
    return true;
  }

  private String calculateSignature() {
    @NonNls StringBuffer buffer = new StringBuffer();

    PsiModifierList modifierList = myMethod.getModifierList();
    String modifiers = modifierList.getText();
    String oldModifier = VisibilityUtil.getVisibilityModifier(modifierList);
    String newModifier = getVisibility();
    String newModifierStr = VisibilityUtil.getVisibilityString(newModifier);
    if (!newModifier.equals(oldModifier)) {
      int index = modifiers.indexOf(oldModifier);
      if (index >= 0) {
        StringBuffer buf = new StringBuffer(modifiers);
        buf.replace(index,
                    index + oldModifier.length() + ("".equals(newModifierStr) ? 1 : 0),
                    newModifierStr);
        modifiers = buf.toString();
      }
      else {
        if (!"".equals(newModifierStr)) newModifierStr += " ";
        modifiers = newModifierStr + modifiers;
      }
    }

    buffer.append(modifiers);
    if (modifiers.length() > 0 &&
        !StringUtil.endsWithChar(modifiers, '\n') && !StringUtil.endsWithChar(modifiers, '\r') && !StringUtil.endsWithChar(modifiers, ' ')) {
      buffer.append(" ");
    }
    if (!myMethod.isConstructor()) {
      final CanonicalTypes.Type returnType = getReturnType();
      if (returnType != null) {
        buffer.append(returnType.getTypeText());
      }
      buffer.append(" ");
    }
    buffer.append(getMethodName());
    buffer.append("(");

    final String indent = "    ";
    final List<PsiTypeCodeFragment> codeFraments = myParametersTableModel.getCodeFraments();

    final ParameterInfo[] parameterInfos = myParametersTableModel.getParameters();
    LOG.assertTrue(codeFraments.size() == parameterInfos.length);
    for (int i = 0; i < parameterInfos.length; i++) {
      ParameterInfo info = parameterInfos[i];
      if (i > 0) {
        buffer.append(",");
      }
      buffer.append("\n");
      buffer.append(indent);
      buffer.append(codeFraments.get(i).getText());
      buffer.append(" ");
      buffer.append(info.getName());
    }
    if (parameterInfos.length > 0) {
      buffer.append("\n");
    }
    buffer.append(")");
    PsiTypeCodeFragment[] thrownExceptionsFragments = myExceptionsTableModel.getTypeCodeFragments();
    if (thrownExceptionsFragments.length > 0) {
      buffer.append("\n");
      buffer.append("throws\n");
      for (PsiTypeCodeFragment thrownExceptionsFragment : thrownExceptionsFragments) {
        String text = thrownExceptionsFragment.getText();
        buffer.append(indent);
        buffer.append(text);
        buffer.append("\n");
      }
    }
    return buffer.toString();
  }

  protected void doAction() {
    stopEditing();
    String message = validateAndCommitData();
    if (message != null) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.incorrect.data"), message, HelpID.CHANGE_SIGNATURE, myProject);
      return;
    }
    if (!checkMethodConflicts()) {
      return;
    }

    if (myMethodsToPropagateParameters != null && !mayPropagateParameters()) {
      Messages.showWarningDialog(myProject, RefactoringBundle.message("changeSignature.parameters.wont.propagate"), ChangeSignatureHandler.REFACTORING_NAME);
      myMethodsToPropagateParameters = null;
    }
    if (myMethodsToPropagateExceptions != null && !mayPropagateExceptions()) {
      Messages.showWarningDialog(myProject, RefactoringBundle.message("changeSignature.exceptions.wont.propagate"), ChangeSignatureHandler.REFACTORING_NAME);
      myMethodsToPropagateExceptions = null;
    }

    invokeRefactoring(new ChangeSignatureProcessor(getProject(), myMethod, isGenerateDelegate(),
                                                   getVisibility(), getMethodName(), getReturnType(),
                                                   getParameters(), getExceptions(),
                                                   myMethodsToPropagateParameters,
                                                   myMethodsToPropagateExceptions));
  }

  private String validateAndCommitData() {
    PsiManager manager = PsiManager.getInstance(myProject);
    PsiElementFactory factory = manager.getElementFactory();

    String name = getMethodName();
    if (!manager.getNameHelper().isIdentifier(name)) {
      return RefactoringMessageUtil.getIncorrectIdentifierMessage(name);
    }

    if (!myMethod.isConstructor()) {
      try {
        myReturnTypeCodeFragment.getType();
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        myReturnTypeField.requestFocus();
        return RefactoringBundle.message("changeSignature.wrong.return.type", myReturnTypeCodeFragment.getText());
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        myReturnTypeField.requestFocus();
        return RefactoringBundle.message("changeSignature.no.return.type");
      }
    }

    final List<PsiTypeCodeFragment> codeFraments = myParametersTableModel.getCodeFraments();
    final List<PsiCodeFragment> defaultValueFraments = myParametersTableModel.getDefaultValueFraments();
    ParameterInfo[] parameterInfos = myParametersTableModel.getParameters();
    final int newParametersNumber = parameterInfos.length;
    LOG.assertTrue(codeFraments.size() == newParametersNumber);

    for (int i = 0; i < newParametersNumber; i++) {
      ParameterInfo info = parameterInfos[i];
      PsiTypeCodeFragment psiCodeFragment = codeFraments.get(i);
      PsiCodeFragment defaultValueFragment = defaultValueFraments.get(i);

      if (!manager.getNameHelper().isIdentifier(info.getName())) {
        return RefactoringMessageUtil.getIncorrectIdentifierMessage(info.getName());
      }

      final PsiType type;
      try {
        type = psiCodeFragment.getType();
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return RefactoringBundle.message("changeSignature.wrong.type.for.parameter", psiCodeFragment.getText(), info.getName());
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        return RefactoringBundle.message("changeSignature.no.type.for.parameter", info.getName());
      }

      info.setType(type);

      if (type instanceof PsiEllipsisType && i != newParametersNumber - 1) {
        return RefactoringBundle.message("changeSignature.vararg.not.last");
      }

      if (info.oldParameterIndex < 0) {
        info.defaultValue = defaultValueFragment.getText();
        String def = info.defaultValue;
        def = def.trim();
        if (!(type instanceof PsiEllipsisType)) {
          if (def.length() == 0) {
            return RefactoringBundle.message("changeSignature.no.default.value", info.getName());
          }

          try {
            factory.createExpressionFromText(info.defaultValue, null);
          }
          catch (IncorrectOperationException e) {
            return e.getMessage();
          }
        }
      }
    }

    ThrownExceptionInfo[] exceptionInfos = myExceptionsTableModel.getThrownExceptions();
    PsiTypeCodeFragment[] typeCodeFragments = myExceptionsTableModel.getTypeCodeFragments();
    for (int i = 0; i < exceptionInfos.length; i++) {
      ThrownExceptionInfo exceptionInfo = exceptionInfos[i];
      PsiTypeCodeFragment typeCodeFragment = typeCodeFragments[i];
      try {
        PsiType type = typeCodeFragment.getType();
        if (!(type instanceof PsiClassType)) {
          return RefactoringBundle.message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText());
        }

        PsiClassType throwable = myMethod.getManager().getElementFactory().createTypeByFQClassName("java.lang.Throwable", type.getResolveScope());
        if (!throwable.isAssignableFrom(type)) {
          return RefactoringBundle.message("changeSignature.not.throwable.type", typeCodeFragment.getText());
        }
        exceptionInfo.setType((PsiClassType)type);
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException e) {
        return RefactoringBundle.message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText());
      }
      catch (PsiTypeCodeFragment.NoTypeException e) {
        return RefactoringBundle.message("changeSignature.no.type.for.exception");
      }
    }

    return null;
  }

  private boolean checkMethodConflicts() {
    try {
      PsiManager manager = PsiManager.getInstance(myProject);
      ParameterInfo[] parameters = getParameters();

      for (ParameterInfo info : parameters) {
        final PsiType parameterType = info.createType(myMethod, manager);

        if (!RefactoringUtil.isResolvableType(parameterType)) {
          final int ret = Messages.showOkCancelDialog(myProject,
                                                      RefactoringBundle.message("changeSignature.cannot.resolve.type", info.getTypeText()),
                                                      ChangeSignatureHandler.REFACTORING_NAME, Messages.getErrorIcon()
          );
          if (ret != 0) return false;
        }

      }
    }
    catch (IncorrectOperationException e) {}
    return true;
  }

  private void stopEditing() {
    TableUtil.stopEditing(myParametersTable);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.CHANGE_SIGNATURE);
  }

  private class MyCellRenderer extends ColoredTableCellRenderer {

    public void customizeCellRenderer(JTable table, Object value,
                                      boolean isSelected, boolean hasFocus, int row, int column) {
      if (value == null) return;
      if (!myParametersTableModel.isCellEditable(row, myParametersTable.convertColumnIndexToModel(column))) {
        setBackground(getBackground().darker());
      }
      append((String)value, new SimpleTextAttributes(Font.PLAIN, null));
    }
  }

  private class MyNameTableCellEditor extends StringTableCellEditor {
    public MyNameTableCellEditor(Project project) {
      super(project);
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      final EditorTextField textField = (EditorTextField)super.getTableCellEditorComponent(table, value, isSelected, row, column);
      textField.registerKeyboardAction(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int column = myParametersTable.convertColumnIndexToModel(myParametersTable.getEditingColumn());
          if (column == 1) {
            int row = myParametersTable.getEditingRow();
            PsiType type = myParametersTableModel.getTypeByRow(row);
            if (type != null) {
              completeVariable(textField, type);
            }
          }
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
      return textField;
    }
  }
}