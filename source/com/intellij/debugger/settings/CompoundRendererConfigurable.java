/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.settings;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.ui.DebuggerExpressionTextField;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiClass;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 24, 2005
 */
public class CompoundRendererConfigurable implements UnnamedConfigurable{
  private CompoundReferenceRenderer myRenderer;
  private CompoundReferenceRenderer myOriginalRenderer;
  private final Project myProject;
  private TextFieldWithBrowseButton myClassNameField;
  private JRadioButton myRbDefaultLabel;
  private JRadioButton myRbExpressionLabel;
  private JRadioButton myRbDefaultChildrenRenderer;
  private JRadioButton myRbExpressionChildrenRenderer;
  private JRadioButton myRbListChildrenRenderer;
  private DebuggerExpressionTextField myLabelEditor;
  private DebuggerExpressionTextField myChildrenEditor;
  private DebuggerExpressionTextField myChildrenExpandedEditor;
  private DebuggerExpressionTextField myListChildrenEditor;
  private JComponent myChildrenListEditor;
  private JLabel myExpandedLabel;
  private JPanel myMainPanel;
  private Table myTable;
  private static final String EMPTY_PANEL_ID = "EMPTY";
  private static final String DATA_PANEL_ID = "DATA";
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JButton myUpButton;
  private JButton myDownButton;
  private static final String NAME_TABLE_COLUMN = "Name";
  private static final String EXPRESSION_TABLE_COLUMN = "Expression";

  public CompoundRendererConfigurable(Project project) {
    myProject = project;
  }

  public void setRenderer(NodeRenderer renderer) {
    if (renderer instanceof CompoundReferenceRenderer) {
      myRenderer = (CompoundReferenceRenderer)renderer;
      myOriginalRenderer = (CompoundReferenceRenderer)renderer.clone();
    }
    else {
      myRenderer = myOriginalRenderer = null;
    }
    reset();
  }

  public CompoundReferenceRenderer getRenderer() {
    return myRenderer;
  }

  public JComponent createComponent() {
    final JPanel panel = new JPanel(new GridBagLayout());
    myClassNameField = new TextFieldWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        PsiClass psiClass = DebuggerUtils.getInstance().chooseClassDialog("Renderer reference type", myProject);
        if(psiClass != null) {
          myClassNameField.setText(psiClass.getQualifiedName());
        }
      }
    });
    myClassNameField.getTextField().addFocusListener(new FocusAdapter() {
      public void focusLost(FocusEvent e) {
        final String qName = myClassNameField.getText();
        updateContext(qName);
      }
    });

    myRbDefaultLabel = new JRadioButton("Use default renderer");
    myRbExpressionLabel = new JRadioButton("Use following expression:");
    final ButtonGroup labelButtonsGroup = new ButtonGroup();
    labelButtonsGroup.add(myRbDefaultLabel);
    labelButtonsGroup.add(myRbExpressionLabel);

    myRbDefaultChildrenRenderer = new JRadioButton("Use default renderer");
    myRbExpressionChildrenRenderer = new JRadioButton("Use following expression:");
    myRbListChildrenRenderer = new JRadioButton("Use list of expressions:");
    final ButtonGroup childrenButtonGroup = new ButtonGroup();
    childrenButtonGroup.add(myRbDefaultChildrenRenderer);
    childrenButtonGroup.add(myRbExpressionChildrenRenderer);
    childrenButtonGroup.add(myRbListChildrenRenderer);

    myLabelEditor = new DebuggerExpressionTextField(myProject, null, "ClassLabelExpression");
    myChildrenEditor = new DebuggerExpressionTextField(myProject, null, "ClassChildrenExpression");
    myChildrenExpandedEditor = new DebuggerExpressionTextField(myProject, null, "ClassChildrenExpression");
    myChildrenListEditor = createChildrenListEditor();

    final ItemListener updateListener = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledState();
      }
    };
    myRbExpressionLabel.addItemListener(updateListener);
    myRbListChildrenRenderer.addItemListener(updateListener);
    myRbExpressionChildrenRenderer.addItemListener(updateListener);
    myRbListChildrenRenderer.addItemListener(updateListener);

    panel.add(new JLabel("Apply renderer to objects of type (fully-qualified name):"), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
    panel.add(myClassNameField, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 6), 0, 0));

    panel.add(new JLabel("When rendering the node"), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(20, 0, 0, 0), 0, 0));
    panel.add(myRbDefaultLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 10, 0, 0), 0, 0));
    panel.add(myRbExpressionLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 10, 0, 0), 0, 0));
    panel.add(myLabelEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 30, 0, 6), 0, 0));

    panel.add(new JLabel("When expanding the node"), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(20, 0, 0, 0), 0, 0));
    panel.add(myRbDefaultChildrenRenderer, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 10, 0, 0), 0, 0));
    panel.add(myRbExpressionChildrenRenderer, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 10, 0, 0), 0, 0));
    panel.add(myChildrenEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 30, 0, 6), 0, 0));
    myExpandedLabel = new JLabel("Test if the node can be expanded (optional):");
    panel.add(myExpandedLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(4, 30, 0, 0), 0, 0));
    panel.add(myChildrenExpandedEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 30, 0, 6), 0, 0));
    panel.add(myRbListChildrenRenderer, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 10, 0, 0), 0, 0));
    panel.add(myChildrenListEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(4, 30, 0, 6), 0, 0));

    myMainPanel = new JPanel(new CardLayout());
    myMainPanel.add(new JPanel(), EMPTY_PANEL_ID);
    myMainPanel.add(panel, DATA_PANEL_ID);
    return myMainPanel;
  }

  private void updateContext(final String qName) {
    PsiClass psiClass = DebuggerUtils.findClass(qName, myProject);
    myLabelEditor.setContext(psiClass);
    myChildrenEditor.setContext(psiClass);
    myChildrenExpandedEditor.setContext(psiClass);
    myListChildrenEditor.setContext(psiClass);
  }

  private void updateEnabledState() {
    myLabelEditor.setEnabled(myRbExpressionLabel.isSelected());

    final boolean isChildrenExpression = myRbExpressionChildrenRenderer.isSelected();
    myChildrenExpandedEditor.setEnabled(isChildrenExpression);
    myExpandedLabel.setEnabled(isChildrenExpression);
    myChildrenEditor.setEnabled(isChildrenExpression);

    myChildrenListEditor.setEnabled(myRbListChildrenRenderer.isSelected());
  }

  private JComponent createChildrenListEditor() {
    final JPanel panel = new JPanel(new GridBagLayout());
    myTable = new Table(new DefaultTableModel());
    getModel().addColumn(NAME_TABLE_COLUMN, (Object[])null);
    getModel().addColumn(EXPRESSION_TABLE_COLUMN, (Object[])null);

    myListChildrenEditor = new DebuggerExpressionTextField(myProject, null, "NamedChildrenConfigurable");

    myTable.setDragEnabled(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));

    myTable.getColumn(EXPRESSION_TABLE_COLUMN).setCellEditor(new AbstractTableCellEditor() {
      public Object getCellEditorValue() {
        return myListChildrenEditor.getText();
      }

      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        myListChildrenEditor.setText((TextWithImports)value);
        return myListChildrenEditor;
      }
    });

    myAddButton = new JButton("Add");
    myAddButton.setMnemonic('A');
    myRemoveButton = new JButton("Remove");
    myRemoveButton.setMnemonic('R');
    myUpButton = new JButton("Move Up");
    myUpButton.setMnemonic('U');
    myDownButton = new JButton("Move Down");
    myDownButton.setMnemonic('D');

    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        getModel().addRow(new Object[] {"", DebuggerUtils.getInstance().createExpressionWithImports("") });
      }
    });

    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int selectedRow = myTable.getSelectedRow();
        if(selectedRow >= 0 && selectedRow < myTable.getRowCount()) {
          getModel().removeRow(selectedRow);
        }
      }
    });

    myDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TableUtil.moveSelectedItemsDown(myTable);
      }
    });

    myUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TableUtil.moveSelectedItemsUp(myTable);
      }
    });

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });

    final JScrollPane scrollPane = new JScrollPane(myTable);
    panel.add(scrollPane, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 4, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    panel.add(myAddButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 4, 0, 0), 0, 0));
    panel.add(myRemoveButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 4, 0, 0), 0, 0));
    panel.add(myUpButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 4, 0, 0), 0, 0));
    panel.add(myDownButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 4, 0, 0), 0, 0));

    return panel;
  }

  public boolean isModified() {
    if (myRenderer == null) {
      return false;
    }
    return !myOriginalRenderer.equals(myRenderer);
  }

  public void apply() throws ConfigurationException {
    if (myRenderer == null) {
      return;
    }

  }

  public void reset() {
    ((CardLayout)myMainPanel.getLayout()).show(myMainPanel, myRenderer == null? EMPTY_PANEL_ID : DATA_PANEL_ID);
    if (myRenderer == null) {
      return;
    }
    final String className = myRenderer.getClassName();
    myClassNameField.setText(className);
    final ValueLabelRenderer labelRenderer = myRenderer.getLabelRenderer();
    final ChildrenRenderer childrenRenderer = myRenderer.getChildrenRenderer();
    final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

    if (rendererSettings.isBase(labelRenderer)) {
      myRbDefaultLabel.setSelected(true);
    }
    else {
      myRbExpressionLabel.setSelected(true);
      myLabelEditor.setText(((LabelRenderer)labelRenderer).getLabelExpression());
    }

    if (rendererSettings.isBase(childrenRenderer)) {
      myRbDefaultChildrenRenderer.setSelected(true);
    }
    else if (childrenRenderer instanceof ExpressionChildrenRenderer) {
      myRbExpressionChildrenRenderer.setSelected(true);
      final ExpressionChildrenRenderer exprRenderer = (ExpressionChildrenRenderer)childrenRenderer;
      myChildrenEditor.setText(exprRenderer.getChildrenExpression());
      myChildrenExpandedEditor.setText(exprRenderer.getChildrenExpandable());
    }
    else {
      myRbListChildrenRenderer.setSelected(true);
      // todo
      //myChildrenListEditor.
    }

    updateEnabledState();
    updateContext(className);
  }

  public void disposeUIResources() {
  }

  private DefaultTableModel getModel() {
    return ((DefaultTableModel)myTable.getModel());
  }

  private void updateButtons() {
    int selectedRow = myTable.getSelectedRow();
    myRemoveButton.setEnabled(selectedRow != -1);
    myUpButton.setEnabled(selectedRow > 0);
    myDownButton.setEnabled(selectedRow < myTable.getRowCount() - 1);
  }

}
