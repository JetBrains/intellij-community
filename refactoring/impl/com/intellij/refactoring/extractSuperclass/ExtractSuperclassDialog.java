package com.intellij.refactoring.extractSuperclass;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

class ExtractSuperclassDialog extends ExtractSuperBaseDialog {
  private final InterfaceContainmentVerifier myContainmentVerifier = new InterfaceContainmentVerifier() {
    public boolean checkedInterfacesContain(PsiMethod psiMethod) {
      return PullUpHelper.checkedInterfacesContain(myMemberInfos, psiMethod);
    }
  };

  public static interface Callback {
    boolean checkConflicts(ExtractSuperclassDialog dialog);
  }

  private JLabel myClassNameLabel;
  private JLabel myPackageLabel;
  private Callback myCallback;

  public ExtractSuperclassDialog(Project project, PsiClass sourceClass, MemberInfo[] selectedMembers, Callback callback) {
    super(project, sourceClass, selectedMembers, ExtractSuperclassHandler.REFACTORING_NAME);
    myCallback = callback;
  }

  public MemberInfo[] getSelectedMemberInfos() {
    ArrayList<MemberInfo> list = new ArrayList<MemberInfo>(myMemberInfos.length);
    for (MemberInfo info : myMemberInfos) {
      if (info.isChecked()) {
        list.add(info);
      }
    }
    return list.toArray(new MemberInfo[list.size()]);
  }

  InterfaceContainmentVerifier getContainmentVerifier() {
    return myContainmentVerifier;
  }

  protected JComponent createNorthPanel() {
    Box box = Box.createVerticalBox();

    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(new JLabel(RefactoringBundle.message("extract.superclass.from")), BorderLayout.NORTH);
    _panel.add(mySourceClassField, BorderLayout.CENTER);
    box.add(_panel);

    box.add(Box.createVerticalStrut(10));

    box.add(createActionComponent());

    box.add(Box.createVerticalStrut(10));

    myClassNameLabel = new JLabel();
    myClassNameLabel.setLabelFor(myExtractedSuperNameField);
    _panel = new JPanel(new BorderLayout());
    _panel.add(myClassNameLabel, BorderLayout.NORTH);
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
    myPackageLabel.setText(RefactoringBundle.message("package.for.new.superclass"));
    _panel.add(myPackageLabel, BorderLayout.NORTH);
    _panel.add(myPackageNameField, BorderLayout.CENTER);
    //_panel.add(myBtnPackageChooser, BorderLayout.EAST);
    box.add(_panel);
    box.add(Box.createVerticalStrut(10));

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(box, BorderLayout.CENTER);
    return panel;
  }

  protected String getClassNameLabelText() {
    return RefactoringBundle.message("superclass.name");
  }

  protected JLabel getClassNameLabel() {
    return myClassNameLabel;
  }

  protected JLabel getPackageNameLabel() {
    return myPackageLabel;
  }

  protected String getEntityName() {
    return RefactoringBundle.message("ExtractSuperClass.superclass");
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(RefactoringBundle.message("members.to.form.superclass"),
                                                                               myMemberInfos, RefactoringBundle.message("make.abstract"));
    panel.add(memberSelectionPanel, BorderLayout.CENTER);
    final MemberInfoModel memberInfoModel =
      new UsesAndInterfacesDependencyMemberInfoModel(mySourceClass, null, false, myContainmentVerifier) {
        public Boolean isFixedAbstract(MemberInfo member) {
          return Boolean.TRUE;
        }
      };
    memberInfoModel.memberInfoChanged(new MemberInfoChange(myMemberInfos));
    memberSelectionPanel.getTable().setMemberInfoModel(memberInfoModel);
    memberSelectionPanel.getTable().addMemberInfoChangeListener(memberInfoModel);

    panel.add(myJavaDocPanel, BorderLayout.EAST);

    return panel;
  }

  @Override
  protected String getJavaDocPanelName() {
    return RefactoringBundle.message("javadoc.for.abstracts");
  }

  @Override
  protected String getExtractedSuperNameNotSpecifiedKey() {
    return RefactoringBundle.message("no.superclass.name.specified");
  }

  @Override
  protected boolean checkConflicts() {
    return myCallback.checkConflicts(this);
  }

  @Override
  protected int getJavaDocPolicySetting() {
    return RefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC;
  }

  @Override
  protected void setJavaDocPolicySetting(int policy) {
    RefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC = policy;
  }


  @Override
  protected ExtractSuperBaseProcessor createProcessor() {
    return new ExtractSuperClassProcessor(myProject, getTargetDirectory(), getExtractedSuperName(),
                                          mySourceClass, getSelectedMemberInfos(), false,
                                          new JavaDocPolicy(getJavaDocPolicy()));
  }

  @Override
  protected String getHelpId() {
    return HelpID.EXTRACT_SUPERCLASS;
  }
}