package com.intellij.refactoring.move.moveMembers;

import com.intellij.ide.util.PackageUtil;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringDialog;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.ui.MemberSelectionTable;
import com.intellij.refactoring.ui.VisibilityPanel;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoChange;
import com.intellij.refactoring.util.classMembers.UsesAndInterfacesDependencyMemberInfoModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Set;

public class MoveMembersDialog extends RefactoringDialog implements MoveMembersOptions {
  private MyMemberInfoModel myMemberInfoModel;

  public static interface Callback {
    void invoke(MoveMembersDialog dialog);
  }

  private Project myProject;
  private Callback myCallback;
  private PsiClass mySourceClass;
  private String mySourceClassName;
  private MemberInfo[] myMemberInfos;
  private final TextFieldWithBrowseButton myTfTargetClassName;
  private MemberSelectionTable myTable;
  private Set myPreselectMembers;

  VisibilityPanel myVisibilityPanel;

  public MoveMembersDialog(Project project, PsiClass sourceClass, final PsiClass initialTargetClass,
                           Set preselectMembers, Callback callback) {


    super(project, true);
    myProject = project;
    myCallback = callback;
    mySourceClass = sourceClass;
    myPreselectMembers = preselectMembers;
    setTitle(MoveMembersImpl.REFACTORING_NAME);

    mySourceClassName = mySourceClass.getQualifiedName();

    PsiField[] fields = mySourceClass.getFields();
    PsiMethod[] methods = mySourceClass.getMethods();
    PsiClass[] innerClasses = mySourceClass.getInnerClasses();
    ArrayList<MemberInfo> memberList = new ArrayList<MemberInfo>(fields.length + methods.length);

    for (int idx = 0; idx < innerClasses.length; idx++) {
      PsiClass innerClass = innerClasses[idx];
      if (!innerClass.hasModifierProperty(PsiModifier.STATIC)) continue;
      MemberInfo info = new MemberInfo(innerClass);
      if (myPreselectMembers.contains(innerClass)) {
        info.setChecked(true);
      }
      memberList.add(info);
    }
    for (int idx = 0; idx < fields.length; idx++) {
      PsiField field = fields[idx];
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        MemberInfo info = new MemberInfo(field);
        if (myPreselectMembers.contains(field)) {
          info.setChecked(true);
        }
        memberList.add(info);
      }
    }
    for (int idx = 0; idx < methods.length; idx++) {
      PsiMethod method = methods[idx];
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        MemberInfo info = new MemberInfo(method);
        if (myPreselectMembers.contains(method)) {
          info.setChecked(true);
        }
        memberList.add(info);
      }
    }
    myMemberInfos = memberList.toArray(new MemberInfo[memberList.size()]);
    myTfTargetClassName = new TextFieldWithBrowseButton(new ChooseClassAction());

    init();

    if (initialTargetClass != null && !sourceClass.equals(initialTargetClass)) {
      myTfTargetClassName.setText(initialTargetClass.getQualifiedName());
    }
  }

  public String getMemberVisibility() {
    return myVisibilityPanel.getVisibility();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.move.moveMembers.MoveMembersDialog";
  }

  private JTable createTable() {
    myMemberInfoModel = new MyMemberInfoModel();
    myTable = new MemberSelectionTable(myMemberInfos, null);
    myTable.setMemberInfoModel(myMemberInfoModel);
    myTable.addMemberInfoChangeListener(myMemberInfoModel);
    myMemberInfoModel.memberInfoChanged(new MemberInfoChange(myMemberInfos));
    return myTable;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    JPanel _panel;
    Box box = Box.createVerticalBox();

    _panel = new JPanel(new BorderLayout());
    JTextField sourceClassField = new JTextField();
    sourceClassField.setText(mySourceClassName);
    sourceClassField.setEditable(false);
    _panel.add(new JLabel("Move members from:"), BorderLayout.NORTH);
    _panel.add(sourceClassField, BorderLayout.CENTER);
    box.add(_panel);

    box.add(Box.createVerticalStrut(10));

    _panel = new JPanel(new BorderLayout());
    JLabel label = new JLabel("To (fully qualified name):");
    label.setLabelFor(myTfTargetClassName);
    _panel.add(label, BorderLayout.NORTH);
    _panel.add(myTfTargetClassName, BorderLayout.CENTER);
    box.add(_panel);

    myTfTargetClassName.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        myMemberInfoModel.updateTargetClass();
      }
    });

    panel.add(box, BorderLayout.CENTER);
    panel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);

    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JTable table = createTable();
    if (table.getRowCount() > 0) {
      table.getSelectionModel().addSelectionInterval(0, 0);
    }
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(table);
    Border titledBorder = IdeBorderFactory.createTitledBorder("Members to be moved (static only)");
    Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
    Border border = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
    scrollPane.setBorder(border);
    panel.add(scrollPane, BorderLayout.CENTER);

    myVisibilityPanel = new VisibilityPanel(true);
    myVisibilityPanel.setVisibility(null);
    panel.add(myVisibilityPanel, BorderLayout.EAST);

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTfTargetClassName.getTextField();
  }

  public PsiMember[] getSelectedMembers() {
    final MemberInfo[] selectedMemberInfos = myTable.getSelectedMemberInfos();
    ArrayList<PsiMember> list = new ArrayList<PsiMember>();
    for (int i = 0; i < selectedMemberInfos.length; i++) {
      list.add(selectedMemberInfos[i].getMember());
    }
    return list.toArray(new PsiMember[list.size()]);
  }

  public String getTargetClassName() {
    return myTfTargetClassName.getText();
  }

  protected void doAction() {
    String message = validateInputData();

    if (message != null) {
      if (message.length() != 0) {
        RefactoringMessageUtil.showErrorMessage(
                MoveMembersImpl.REFACTORING_NAME,
                message,
                HelpID.MOVE_MEMBERS,
                myProject);
      }
      return;
    }

    myCallback.invoke(this);
    RefactoringSettings.getInstance().MOVE_PREVIEW_USAGES = isPreviewUsages();
  }

  private String validateInputData() {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final String fqName = getTargetClassName();
    if ("".equals(fqName)) {
      return "No destination class specified";
    }
    else if (!manager.getNameHelper().isQualifiedName(fqName)) {
      return "'" + fqName + "' is not a legal FQ-name";
    }
    else {
      final PsiClass[] targetClass = new PsiClass[]{null};
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          try {
            targetClass[0] = findOrCreateTargetClass(manager, fqName);
          }
          catch (IncorrectOperationException e) {
            RefactoringMessageUtil.showErrorMessage(
              MoveMembersImpl.REFACTORING_NAME,
              e.getMessage(),
              HelpID.MOVE_MEMBERS,
              myProject);
          }
        }

      }, "Create class " + fqName, null);

      if (targetClass[0] == null) {
        return "";
      }

      if (mySourceClass.equals(targetClass[0])) {
        return "Source and destination classes should be different";
      }
      else {
        for (int i = 0; i < myMemberInfos.length; i++) {
          MemberInfo info = myMemberInfos[i];
          if (!info.isChecked()) continue;
          if (PsiTreeUtil.isAncestor(info.getMember(), targetClass[0], false)) {
            return "Cannot move inner class " + info.getDisplayName() + " into itself.";
          }
        }

        if (!targetClass[0].isWritable()) {
          RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(myProject, targetClass[0]);
          return "";
//          return "Cannot perform the refactoring.\nDestination class " + targetClass[0].getQualifiedName() + " is read-only.";
        }

        return null;
      }
    }
  }

  private PsiClass findOrCreateTargetClass(final PsiManager manager, final String fqName) throws IncorrectOperationException {
    final String className;
    final String packageName;
    int dotIndex = fqName.lastIndexOf('.');
    if (dotIndex >= 0) {
      packageName = fqName.substring(0, dotIndex);
      className = (dotIndex + 1 < fqName.length())? fqName.substring(dotIndex + 1) : "";
    }
    else {
      packageName = "";
      className = fqName;
    }


    PsiClass aClass = manager.findClass(fqName, GlobalSearchScope.projectScope(myProject));
    if (aClass != null) return aClass;

    final PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(
                myProject,
                packageName,
                mySourceClass.getContainingFile().getContainingDirectory(),
                true);

    if (directory == null) {
      return null;
    }

    int answer = Messages.showYesNoDialog(
            myProject,
            "Class " + fqName + " does not exist.\nDo you want to create it?",
            MoveMembersImpl.REFACTORING_NAME,
            Messages.getQuestionIcon()
    );
    if (answer != 0) return null;
    final Ref<IncorrectOperationException> eRef = new Ref<IncorrectOperationException>();
    final PsiClass newClass = ApplicationManager.getApplication().runWriteAction(new Computable<PsiClass>() {
          public PsiClass compute() {
            try {
              return directory.createClass(className);
            }
            catch (IncorrectOperationException e) {
              eRef.set(e);
              return null;
            }
          }
        });
    if (!eRef.isNull()) throw eRef.get();
    return newClass;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MOVE_MEMBERS);
  }

  private class ChooseClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooserDialog chooser = TreeClassChooserDialog.withInnerClasses("Choose Destination Class", myProject, GlobalSearchScope.projectScope(myProject), new TreeClassChooserDialog.ClassFilter() {
        public boolean isAccepted(PsiClass aClass) {
          return aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC);
        }
      }, null);
      chooser.selectDirectory(mySourceClass.getContainingFile().getContainingDirectory());
      chooser.show();
      PsiClass aClass = chooser.getSelectedClass();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
        myMemberInfoModel.updateTargetClass();
      }
    }
  }

  private class MyMemberInfoModel extends UsesAndInterfacesDependencyMemberInfoModel {
    PsiClass myTargetClass = null;
    public MyMemberInfoModel() {
      super(mySourceClass, null, false, DEFAULT_CONTAINMENT_VERIFIER);
    }

    public Boolean isFixedAbstract(MemberInfo member) {
      return null;
    }

    public boolean isCheckedWhenDisabled(MemberInfo member) {
      return false;
    }

    public boolean isMemberEnabled(MemberInfo member) {
      if(myTargetClass != null && myTargetClass.isInterface()) {
        return !(member.getMember() instanceof PsiMethod);
      }
      return super.isMemberEnabled(member);
    }

    public void updateTargetClass() {
      final PsiManager manager = PsiManager.getInstance(myProject);
      myTargetClass = manager.findClass(getTargetClassName(), GlobalSearchScope.projectScope(myProject));
      myTable.fireExternalDataChange();
    }
  }
}