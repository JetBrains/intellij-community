package com.intellij.refactoring.memberPushDown;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.memberPullUp.JavaDocPanel;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoChange;
import com.intellij.refactoring.util.classMembers.MemberInfoModel;
import com.intellij.refactoring.util.classMembers.UsedByDependencyMemberInfoModel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class PushDownDialog extends RefactoringDialog {
  private MemberInfo[] myMemberInfos;
  private PsiClass myClass;
  private JavaDocPanel myJavaDocPanel;
  private MemberInfoModel myMemberInfoModel;

  public PushDownDialog(Project project, MemberInfo[] memberInfos, PsiClass aClass) {
    super(project, true);
    myMemberInfos = memberInfos;
    myClass = aClass;

    setTitle(PushDownHandler.REFACTORING_NAME);

    init();
  }

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  public MemberInfo[] getSelectedMemberInfos() {
    ArrayList<MemberInfo> list = new ArrayList<MemberInfo>(myMemberInfos.length);
    for (MemberInfo info : myMemberInfos) {
      if (info.isChecked() && myMemberInfoModel.isMemberEnabled(info)) {
        list.add(info);
      }
    }
    return list.toArray(new MemberInfo[list.size()]);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MEMBERS_PUSH_DOWN);
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.memberPushDown.PushDownDialog";
  }

  protected JComponent createNorthPanel() {
    GridBagConstraints gbConstraints = new GridBagConstraints();

    JPanel panel = new JPanel(new GridBagLayout());

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel(RefactoringBundle.message("push.members.from.0.down.label",
                                                   myClass.getQualifiedName())), gbConstraints);
    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(
      RefactoringBundle.message("members.to.be.pushed.down.panel.title"),
      myMemberInfos,
      RefactoringBundle.message("keep.abstract.column.header"));
    panel.add(memberSelectionPanel, BorderLayout.CENTER);

    myMemberInfoModel = new MyMemberInfoModel();
    myMemberInfoModel.memberInfoChanged(new MemberInfoChange(myMemberInfos));
    memberSelectionPanel.getTable().setMemberInfoModel(myMemberInfoModel);
    memberSelectionPanel.getTable().addMemberInfoChangeListener(myMemberInfoModel);


    myJavaDocPanel = new JavaDocPanel(RefactoringBundle.message("push.down.javadoc.panel.title"));
    myJavaDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    panel.add(myJavaDocPanel, BorderLayout.EAST);
    return panel;
  }

  protected void doAction() {
    if(!isOKActionEnabled()) return;

    JavaRefactoringSettings.getInstance().PUSH_DOWN_PREVIEW_USAGES = isPreviewUsages();

    invokeRefactoring (new PushDownProcessor(
            getProject(), getSelectedMemberInfos(), myClass,
            new JavaDocPolicy(getJavaDocPolicy())));
  }

  private class MyMemberInfoModel extends UsedByDependencyMemberInfoModel {
    public MyMemberInfoModel() {
      super(myClass);
    }
  }
}
