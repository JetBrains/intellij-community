package com.intellij.refactoring.extractSuperclass;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.memberPullUp.JavaDocPanel;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.classMembers.*;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

class ExtractSuperclassDialog extends ExtractSuperBaseDialog {
  private final InterfaceContainmentVerifier myContainmentVerifier = new InterfaceContainmentVerifier() {
    public boolean checkedInterfacesContain(PsiMethod psiMethod) {
      return PullUpHelper.checkedInterfacesContain(myMemberInfos, psiMethod);
    };
  };
  private JLabel myClassNameLabel;
  private JLabel myPackageLabel;

  public static interface Callback {
    boolean checkConflicts(ExtractSuperclassDialog dialog);
  }

  private final Project myProject;
  private MemberInfo[] myMemberInfos;
  private Callback myCallback;
  private PsiDirectory myTargetDirectory;
  private JTextField mySourceClassField;
  private JTextField myClassNameField;
  private final JTextField myTfPackageName;
  private PsiClass mySourceClass;
  private final FixedSizeButton myBtnPackageChooser;

  private JavaDocPanel myJavaDocPanel;


  public ExtractSuperclassDialog(Project project, final PsiClass sourceClass, MemberInfo[] selectedMembers, String targetPackageName, Callback callback) {
    super(project, true);
    myProject = project;
    myMemberInfos = selectedMembers;
    myCallback = callback;
    setTitle(ExtractSuperclassHandler.REFACTORING_NAME);
    mySourceClass = sourceClass;

    myTfPackageName=new JTextField(targetPackageName);
    myBtnPackageChooser=new FixedSizeButton(myTfPackageName);

    init();
    updateDialogForExtractSuperclass();
    mySourceClassField.setText(sourceClass.getQualifiedName());
  }

  public MemberInfo[] getSelectedMemberInfos() {
    ArrayList list = new ArrayList(myMemberInfos.length);
    for (int idx = 0; idx < myMemberInfos.length; idx++) {
      MemberInfo info = myMemberInfos[idx];
      if (info.isChecked()) {
        list.add(info);
      }
    }
    return (MemberInfo[]) list.toArray(new MemberInfo[list.size()]);
  }

  InterfaceContainmentVerifier getContainmentVerifier() {
    return myContainmentVerifier;
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  public String getSuperclassName() {
    return myClassNameField.getText().trim();
  }

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  protected boolean getInitialPreviewResults() {
    return false;
  }

  protected void setInitialPreviewResults(boolean value) {
  }

  protected JComponent createNorthPanel() {
    Box box = Box.createVerticalBox();

    mySourceClassField = new JTextField();
    mySourceClassField.setEditable(false);
    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(new JLabel("Extract superclass from:"), BorderLayout.NORTH);
    _panel.add(mySourceClassField, BorderLayout.CENTER);
    box.add(_panel);

    box.add(Box.createVerticalStrut(10));

    box.add(createActionComponent());

    box.add(Box.createVerticalStrut(10));

    myClassNameLabel = new JLabel();
    myClassNameField = new JTextField();
    myClassNameLabel.setLabelFor(myClassNameField);
    _panel = new JPanel(new BorderLayout());
    _panel.add(myClassNameLabel, BorderLayout.NORTH);
    _panel.add(myClassNameField, BorderLayout.CENTER);
    box.add(_panel);
    box.add(Box.createVerticalStrut(5));

    myBtnPackageChooser.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        PackageChooserDialog chooser = new PackageChooserDialog("Choose Destination Package", myProject);
        chooser.selectPackage(myTfPackageName.getText());
        chooser.show();
        PsiPackage aPackage = chooser.getSelectedPackage();
        if (aPackage != null) {
          myTfPackageName.setText(aPackage.getQualifiedName());
        }
      }
    });
    _panel = new JPanel(new BorderLayout());
    myPackageLabel = new JLabel("Package for new superclass:");
    myPackageLabel.setLabelFor(myTfPackageName);
    myPackageLabel.setDisplayedMnemonic('P');
    _panel.add(myPackageLabel, BorderLayout.NORTH);
    _panel.add(myTfPackageName, BorderLayout.CENTER);
    _panel.add(myBtnPackageChooser, BorderLayout.EAST);
    box.add(_panel);
    box.add(Box.createVerticalStrut(10));

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(box, BorderLayout.CENTER);
    return panel;
  }

  protected JLabel getClassNameLabel() {
    return myClassNameLabel;
  }

  protected JLabel getPackageNameLabel() {
    return myPackageLabel;
  }

  protected String getEntityName() {
    return "Superclass";
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel =
            new MemberSelectionPanel("Members to Form Superclass", myMemberInfos, "Make abstract");
    panel.add(memberSelectionPanel, BorderLayout.CENTER);
    final MemberInfoModel memberInfoModel = new UsesAndInterfacesDependencyMemberInfoModel(mySourceClass, null, false,
                                                                                           myContainmentVerifier) {
      public Boolean isFixedAbstract(MemberInfo member) {
        return Boolean.TRUE;
      }
    };
    memberInfoModel.memberInfoChanged(new MemberInfoChange(myMemberInfos));
    memberSelectionPanel.getTable().setMemberInfoModel(memberInfoModel);
    memberSelectionPanel.getTable().addMemberInfoChangeListener(memberInfoModel);

    myJavaDocPanel = new JavaDocPanel("JavaDoc for abstracts");
    myJavaDocPanel.setPolicy(RefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC);
    panel.add(myJavaDocPanel, BorderLayout.EAST);

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myClassNameField;
  }

  protected void doAction() {
    final String[] errorString = new String[]{null};
    final String superclassName = getSuperclassName();
    final String packageName = myTfPackageName.getText().trim();
    final PsiManager manager = PsiManager.getInstance(myProject);
    if ("".equals(superclassName)) {
      errorString[0] = "No superclass name specified";
      mySourceClassField.requestFocusInWindow();
    }
    else if (!manager.getNameHelper().isIdentifier(superclassName)) {
      errorString[0] = RefactoringMessageUtil.getIncorrectIdentifierMessage(superclassName);
      mySourceClassField.requestFocusInWindow();
    }
    else {
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          try {
            myTargetDirectory = PackageUtil.findOrCreateDirectoryForPackage(myProject, packageName, myTargetDirectory, true);
            if (myTargetDirectory == null) {
              errorString[0] = ""; // message already reported by PackageUtil
              return;
            }
            errorString[0] = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, superclassName);
          }
          catch (IncorrectOperationException e) {
            errorString[0] = e.getMessage();
            myTfPackageName.requestFocusInWindow();
          }
        }
      }, "Create directory", null);
    }
    if (errorString[0] != null) {
      if (errorString[0].length() > 0) {
        RefactoringMessageUtil.showErrorMessage(ExtractSuperclassHandler.REFACTORING_NAME, errorString[0], HelpID.EXTRACT_SUPERCLASS, myProject);
      }
      return;
    }

    if (!myCallback.checkConflicts(this)) {
      return;
    }
    RefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC = getJavaDocPolicy();
    if (!isExtractSuperclass()) {
      final ExtractSuperClassProcessor processor = new ExtractSuperClassProcessor(myProject,
                                           getTargetDirectory(), getSuperclassName(),
                                           mySourceClass, getSelectedMemberInfos(), false,
                                           new JavaDocPolicy(getJavaDocPolicy()));
      invokeRefactoring(processor);
    } else {
      closeOKAction();
    }
 }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.EXTRACT_SUPERCLASS);
  }


}