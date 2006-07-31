package com.intellij.refactoring.extractInterface;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractSuperclass.ExtractSuperBaseDialog;
import com.intellij.refactoring.memberPullUp.JavaDocPanel;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.classMembers.DelegatingMemberInfoModel;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class ExtractInterfaceDialog extends ExtractSuperBaseDialog {
  private final Project myProject;
  private final PsiClass myClass;

  private PsiDirectory myTargetDirectory;

  private MemberInfo[] myMemberInfos;

  private JTextField myInterfaceNameField;
  private final ReferenceEditorWithBrowseButton myPackageNameField;
  private JTextField mySourceClassField;

  private JavaDocPanel myJavaDocPanel;
  private JLabel myInterfaceNameLabel;
  private JLabel myPackageLabel;

  public ExtractInterfaceDialog(Project project, PsiClass aClass) {
    super(project, true);
    setTitle(ExtractInterfaceHandler.REFACTORING_NAME);

    myProject = project;
    myClass = aClass;

    myPackageNameField = new ReferenceEditorWithBrowseButton(null, "", PsiManager.getInstance(myProject), false);
    init();
  }

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  protected void init() {
    myTargetDirectory = myClass.getContainingFile().getContainingDirectory();
    myMemberInfos = MemberInfo.extractClassMembers(myClass, new MemberInfo.Filter() {
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

    super.init();

    mySourceClassField.setText(myClass.getQualifiedName());

    PsiFile file = myClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      myPackageNameField.setText(((PsiJavaFile)file).getPackageName());
    }

    updateDialogForExtractSuperclass();
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }


  public MemberInfo[] getSelectedMembers() {
    int[] rows = getCheckedRows();
    MemberInfo[] selectedMethods = new MemberInfo[rows.length];
    for (int idx = 0; idx < rows.length; idx++) {
      selectedMethods[idx] = myMemberInfos[rows[idx]];
    }
    return selectedMethods;
  }

  private String getTargetPackageName() {
    return myPackageNameField.getText().trim();
  }

  public String getInterfaceName() {
    return myInterfaceNameField.getText().trim();
  }

  protected JComponent createNorthPanel() {
    Box box = Box.createVerticalBox();

    mySourceClassField = new JTextField();
    mySourceClassField.setEditable(false);
    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(new JLabel(RefactoringBundle.message("extract.interface.from")), BorderLayout.NORTH);
    _panel.add(mySourceClassField, BorderLayout.CENTER);
    box.add(_panel);

    box.add(Box.createVerticalStrut(10));

    box.add(createActionComponent());

    box.add(Box.createVerticalStrut(10));

    myInterfaceNameLabel = new JLabel();
    myInterfaceNameField = new JTextField();
    myInterfaceNameLabel.setText(RefactoringBundle.message("interface.name.prompt"));

    _panel = new JPanel(new BorderLayout());
    _panel.add(myInterfaceNameLabel, BorderLayout.NORTH);
    _panel.add(myInterfaceNameField, BorderLayout.CENTER);
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

  protected JTextField getSuperEntityNameField() {
    return myInterfaceNameField;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    //panel.setBorder(BorderFactory.createLineBorder(Color.gray));
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(RefactoringBundle.message("members.to.form.interface"),
                                                                               myMemberInfos, null);
    memberSelectionPanel.getTable().setMemberInfoModel(new DelegatingMemberInfoModel(memberSelectionPanel.getTable().getMemberInfoModel()) {
      public Boolean isFixedAbstract(MemberInfo member) {
        return Boolean.TRUE;
      }
    });
    panel.add(memberSelectionPanel, BorderLayout.CENTER);

    myJavaDocPanel = new JavaDocPanel(RefactoringBundle.message("extractSuperInterface.javadoc"));
    final int oldJavaDocPolicy = RefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC;
    myJavaDocPanel.setPolicy(oldJavaDocPolicy);
    panel.add(myJavaDocPanel, BorderLayout.EAST);

    return panel;
  }


  public JComponent getPreferredFocusedComponent() {
    return myInterfaceNameField;
  }

  protected void doAction() {
    final String[] errorString = new String[]{null};
    final String interfaceName = getInterfaceName();
    final String packageName = getTargetPackageName();
    final PsiManager manager = PsiManager.getInstance(myProject);
    if ("".equals(interfaceName)) {
      errorString[0] = RefactoringBundle.message("no.interface.name.specified");
      myInterfaceNameField.requestFocusInWindow();
    }
    else if (!manager.getNameHelper().isIdentifier(interfaceName)) {
      errorString[0] = RefactoringMessageUtil.getIncorrectIdentifierMessage(interfaceName);
      myInterfaceNameField.requestFocusInWindow();
    }
    else {

      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          try {
            final PsiPackage aPackage = manager.findPackage(packageName);
            if (aPackage != null) {
              final PsiDirectory[] directories = aPackage.getDirectories(myClass.getResolveScope());
              if (directories.length >= 1) {
                myTargetDirectory = directories[0];
              }
            }
            myTargetDirectory =
            PackageUtil.findOrCreateDirectoryForPackage(myProject, packageName, myTargetDirectory, true);
            if (myTargetDirectory == null) {
              errorString[0] = ""; // message already reported by PackageUtil
              return;
            }
          }
          catch (IncorrectOperationException e) {
            errorString[0] = e.getMessage();
            return;
          }
          final Runnable action = new Runnable() {
            public void run() {
              errorString[0] = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, interfaceName);
            }
          };
          ApplicationManager.getApplication().runWriteAction(action);
        }
      }, RefactoringBundle.message("create.directory"), null);
    }
    if (errorString[0] != null) {
      if (errorString[0].length() > 0) {
        CommonRefactoringUtil.showErrorMessage(ExtractInterfaceHandler.REFACTORING_NAME, errorString[0],
                                                HelpID.EXTRACT_INTERFACE, myProject);
      }
      return;
    }

    if (!isExtractSuperclass()) {
      invokeRefactoring(new ExtractInterfaceProcessor(myProject, false, getTargetDirectory(), interfaceName,
                                                      myClass,
                                                      getSelectedMembers(),
                                                      new JavaDocPolicy(getJavaDocPolicy())));
    }

    RefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC = getJavaDocPolicy();
    closeOKAction();
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

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.EXTRACT_INTERFACE);
  }
}