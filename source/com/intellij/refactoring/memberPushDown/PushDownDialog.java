package com.intellij.refactoring.memberPushDown;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.RefactoringDialog;
import com.intellij.refactoring.memberPullUp.JavaDocPanel;
import com.intellij.refactoring.ui.MemberSelectionPanel;
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
  private final Callback myCallback;
  private MemberSelectionPanel myMemberSelectionPanel;
  private JavaDocPanel myJavaDocPanel;
  private MemberInfoModel myMemberInfoModel;

  public static interface Callback {
    void run(PushDownDialog dialog);
  }

  public PushDownDialog(Project project, MemberInfo[] memberInfos, PsiClass aClass, Callback callback) {
    super(project, true);
    myMemberInfos = memberInfos;
    myClass = aClass;
    myCallback = callback;

    setTitle(PushDownHandler.REFACTORING_NAME);

    init();
  }

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  public MemberInfo[] getSelectedMemberInfos() {
    ArrayList list = new ArrayList(myMemberInfos.length);
    for (int idx = 0; idx < myMemberInfos.length; idx++) {
      MemberInfo info = myMemberInfos[idx];
      if (info.isChecked() && myMemberInfoModel.isMemberEnabled(info)) {
        list.add(info);
      }
    }
    return (MemberInfo[]) list.toArray(new MemberInfo[list.size()]);
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
    panel.add(new JLabel("Push members from " + myClass.getQualifiedName() + " down"), gbConstraints);
    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    myMemberSelectionPanel = new MemberSelectionPanel("Members to be pushed down", myMemberInfos, "Keep abstract");
    panel.add(myMemberSelectionPanel, BorderLayout.CENTER);

    myMemberInfoModel = new MyMemberInfoModel();
    myMemberInfoModel.memberInfoChanged(new MemberInfoChange(myMemberInfos));
    myMemberSelectionPanel.getTable().setMemberInfoModel(myMemberInfoModel);
    myMemberSelectionPanel.getTable().addMemberInfoChangeListener(myMemberInfoModel);


    myJavaDocPanel = new JavaDocPanel("JavaDoc for abstracts");
    myJavaDocPanel.setPolicy(RefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    panel.add(myJavaDocPanel, BorderLayout.EAST);
    return panel;
  }

  protected void doAction() {
    if(!isOKActionEnabled()) return;

    RefactoringSettings.getInstance().PUSH_DOWN_PREVIEW_USAGES = isPreviewUsages();
    myCallback.run(this);
  }

  private class MyMemberInfoModel extends UsedByDependencyMemberInfoModel {
    public MyMemberInfoModel() {
      super(myClass);
    }
  }
}
