package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameHelper;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringDialog;
import com.intellij.refactoring.ui.NameSuggestionsField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryDialog extends RefactoringDialog {
  private final Callback myCallback;

  private NameSuggestionsField myNameField;
  private final TextFieldWithBrowseButton myTfTargetClassName;
  private JComboBox myTargetClassNameCombo;
  private Project myProject;
  private PsiClass myContainingClass;
  private final boolean myIsInner;

  public interface Callback {
    void run(ReplaceConstructorWithFactoryDialog dialog);
  }

  ReplaceConstructorWithFactoryDialog(Project project, PsiClass containingClass, Callback callback) {
    super(project, true);
    myProject = project;

    myCallback = callback;
    myContainingClass = containingClass;
    myIsInner = myContainingClass.getContainingClass() != null
            && !myContainingClass.hasModifierProperty(PsiModifier.STATIC);

    setTitle(ReplaceConstructorWithFactoryHandler.REFACTORING_NAME);

    myTfTargetClassName = new TextFieldWithBrowseButton(new ChooseClassAction());

    init();
  }

  public String getName() {
    return myNameField.getName();
  }

  protected boolean hasHelpAction () { return false; }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField.getComponent();
  }

  public String getTargetClassName() {
    if (!myIsInner) {
      return myTfTargetClassName.getText();
    } else {
      return (String) myTargetClassNameCombo.getSelectedItem();
    }
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.BOTH;

    gbc.insets = new Insets(4, 0, 4, 8);
    gbc.gridwidth = 1;
    gbc.gridx = 0;
    gbc.gridy = 0;
    panel.add(new JLabel("Factory method name:"), gbc);

    gbc.gridx++;
    gbc.weightx = 1.0;
    final String[] nameSuggestions = new String[]{
      "create" + myContainingClass.getName(),
      "new" + myContainingClass.getName(),
      "getInstance"
    };
    myNameField = new NameSuggestionsField(nameSuggestions, myProject);
    myNameField.addDataChangedListener(new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    });
    panel.add(myNameField.getComponent(), gbc);

    JPanel targetClassPanel = createTargetPanel();

    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = 2;
    panel.add(targetClassPanel, gbc);


    return panel;

  }

  private JPanel createTargetPanel() {
    JPanel targetClassPanel = new JPanel(new BorderLayout());
    if (!myIsInner) {
      JLabel label = new JLabel("In (fully qualified name):");
      label.setLabelFor(myTfTargetClassName);
      targetClassPanel.add(label, BorderLayout.NORTH);
      targetClassPanel.add(myTfTargetClassName, BorderLayout.CENTER);
      myTfTargetClassName.setText(myContainingClass.getQualifiedName());
    } else {
      ArrayList<String> list = new ArrayList<String>();
      PsiElement parent = myContainingClass;
      while (parent instanceof PsiClass) {
        list.add(((PsiClass) parent).getQualifiedName());
        parent = parent.getParent();
      }

      myTargetClassNameCombo = new JComboBox(list.toArray(new String[list.size()]));
      JLabel label = new JLabel("In (fully qualified name):");
      label.setLabelFor(myTargetClassNameCombo.getEditor().getEditorComponent());
      targetClassPanel.add(label, BorderLayout.NORTH);
      targetClassPanel.add(myTargetClassNameCombo, BorderLayout.CENTER);
    }
    return targetClassPanel;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryDialog";
  }

  private class ChooseClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooserDialog chooser = new TreeClassChooserDialog("Choose Destination Class", myProject);
      chooser.selectDirectory(myContainingClass.getContainingFile().getContainingDirectory());
      chooser.show();
      PsiClass aClass = chooser.getSelectedClass();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
      }
    }
  }


  protected JComponent createCenterPanel() {
    return null;
  }

  protected void doAction() {
    myCallback.run(this);
  }

  protected boolean areButtonsValid() {
    final String name = myNameField.getName();
    final PsiNameHelper nameHelper = myContainingClass.getManager().getNameHelper();
    return nameHelper.isIdentifier(name);
  }

}