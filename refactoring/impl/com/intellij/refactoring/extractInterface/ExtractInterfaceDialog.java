package com.intellij.refactoring.extractInterface;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.extractSuperclass.ExtractSuperBaseDialog;
import com.intellij.refactoring.extractSuperclass.ExtractSuperBaseProcessor;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.DelegatingMemberInfoModel;
import com.intellij.refactoring.util.classMembers.MemberInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class ExtractInterfaceDialog extends ExtractSuperBaseDialog {
  private JLabel myInterfaceNameLabel;
  private JLabel myPackageLabel;

  public ExtractInterfaceDialog(Project project, PsiClass sourceClass) {
    super(project, sourceClass, collectMembers(sourceClass), ExtractInterfaceHandler.REFACTORING_NAME);
    init();
  }

  private static MemberInfo[] collectMembers(PsiClass c) {
    return MemberInfo.extractClassMembers(c, new MemberInfo.Filter() {
      public boolean includeMember(PsiMember element) {
        if (element instanceof PsiMethod) {
          return element.hasModifierProperty(PsiModifier.PUBLIC)
                 && !element.hasModifierProperty(PsiModifier.STATIC);
        }
        else if (element instanceof PsiField) {
          return element.hasModifierProperty(PsiModifier.FINAL)
                 && element.hasModifierProperty(PsiModifier.STATIC)
                 && element.hasModifierProperty(PsiModifier.PUBLIC);
        }
        else if (element instanceof PsiClass) {
          return ((PsiClass)element).isInterface() || element.hasModifierProperty(PsiModifier.STATIC);
        }
        return false;
      }
    }, true);
  }

  public MemberInfo[] getSelectedMembers() {
    int[] rows = getCheckedRows();
    MemberInfo[] selectedMethods = new MemberInfo[rows.length];
    for (int idx = 0; idx < rows.length; idx++) {
      selectedMethods[idx] = myMemberInfos[rows[idx]];
    }
    return selectedMethods;
  }

    private int[] getCheckedRows() {
    int count = 0;
    for (MemberInfo info : myMemberInfos) {
      if (info.isChecked()) {
        count++;
      }
    }
    int[] rows = new int[count];
    int currentRow = 0;
    for (int idx = 0; idx < myMemberInfos.length; idx++) {
      if (myMemberInfos[idx].isChecked()) {
        rows[currentRow++] = idx;
      }
    }
    return rows;
  }

  protected JComponent createNorthPanel() {
    Box box = Box.createVerticalBox();

    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(new JLabel(RefactoringBundle.message("extract.interface.from")), BorderLayout.NORTH);
    _panel.add(mySourceClassField, BorderLayout.CENTER);
    box.add(_panel);

    box.add(Box.createVerticalStrut(10));

    box.add(createActionComponent());

    box.add(Box.createVerticalStrut(10));

    myInterfaceNameLabel = new JLabel();
    myInterfaceNameLabel.setText(RefactoringBundle.message("interface.name.prompt"));

    _panel = new JPanel(new BorderLayout());
    _panel.add(myInterfaceNameLabel, BorderLayout.NORTH);
    _panel.add(myExtractedSuperNameField, BorderLayout.CENTER);
    box.add(_panel);
    box.add(Box.createVerticalStrut(5));

    myPackageNameField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        PackageChooserDialog chooser = new PackageChooserDialog(RefactoringBundle.message("choose.destination.package"), myProject);
        chooser.selectPackage(myPackageNameField.getText());
        chooser.show();
        PsiPackage aPackage = chooser.getSelectedPackage();
        if (aPackage != null) {
          myPackageNameField.setText(aPackage.getQualifiedName());
        }
      }
    });
    _panel = new JPanel(new BorderLayout());
    myPackageLabel = new JLabel();
    myPackageLabel.setText(RefactoringBundle.message("package.for.new.interface"));

    _panel.add(myPackageLabel, BorderLayout.NORTH);
    _panel.add(myPackageNameField, BorderLayout.CENTER);
    box.add(_panel);
    box.add(Box.createVerticalStrut(10));

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(box, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected void updateDialogForExtractSubclass() {
    super.updateDialogForExtractSubclass();
    myInterfaceNameLabel.setText(RefactoringBundle.message("rename.implementation.class.to"));
  }

  @Override
  protected void updateDialogForExtractSuperclass() {
    super.updateDialogForExtractSuperclass();
    myInterfaceNameLabel.setText(RefactoringBundle.message("interface.name.prompt"));
  }

  protected String getClassNameLabelText() {
    return RefactoringBundle.message("superinterface.name");
  }

  protected JLabel getClassNameLabel() {
    return myInterfaceNameLabel;
  }

  protected JLabel getPackageNameLabel() {
    return myPackageLabel;
  }

  protected String getEntityName() {
    return RefactoringBundle.message("extractSuperInterface.interface");
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(RefactoringBundle.message("members.to.form.interface"),
                                                                               myMemberInfos, null);
    memberSelectionPanel.getTable().setMemberInfoModel(new DelegatingMemberInfoModel(memberSelectionPanel.getTable().getMemberInfoModel()) {
      public Boolean isFixedAbstract(MemberInfo member) {
        return Boolean.TRUE;
      }
    });
    panel.add(memberSelectionPanel, BorderLayout.CENTER);

    panel.add(myJavaDocPanel, BorderLayout.EAST);

    return panel;
  }

  @Override
  protected String getJavaDocPanelName() {
    return RefactoringBundle.message("extractSuperInterface.javadoc");
  }

  @Override
  protected String getExtractedSuperNameNotSpecifiedKey() {
    return RefactoringBundle.message("no.interface.name.specified");
  }

  @Override
  protected int getJavaDocPolicySetting() {
    return RefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC;
  }

  @Override
  protected void setJavaDocPolicySetting(int policy) {
    RefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC = policy;
  }

  @Override
  protected ExtractSuperBaseProcessor createProcessor() {
    return new ExtractInterfaceProcessor(myProject, false, getTargetDirectory(), getExtractedSuperName(),
                                         mySourceClass, getSelectedMembers(),
                                         new JavaDocPolicy(getJavaDocPolicy()));
  }

  @Override
  protected String getHelpId() {
    return HelpID.EXTRACT_INTERFACE;
  }
}