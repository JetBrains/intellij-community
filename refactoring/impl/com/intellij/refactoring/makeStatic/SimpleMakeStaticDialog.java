/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 04.07.2002
 * Time: 13:54:39
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.makeStatic;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.usageView.UsageViewUtil;

import javax.swing.*;
import java.awt.*;

public class SimpleMakeStaticDialog extends AbstractMakeStaticDialog {
  JCheckBox myCbReplaceUsages;

  public SimpleMakeStaticDialog(Project project, PsiTypeParameterListOwner member) {
    super(project, member);
    String type = UsageViewUtil.getType(myMember);
    setTitle(RefactoringBundle.message("make.0.static", StringUtil.capitalize(type)));
    init();
  }

  protected boolean validateData() {
    return true;
  }

  public boolean isMakeClassParameter() {
    return false;
  }

  public String getClassParameterName() {
    return null;
  }

  public ParameterTablePanel.VariableData[] getVariableData() {
    return null;
  }

  public boolean isReplaceUsages() {
    return myCbReplaceUsages.isSelected();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MAKE_METHOD_STATIC_SIMPLE);
  }

  protected JComponent createNorthPanel() {
    GridBagConstraints gbConstraints = new GridBagConstraints();

    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(IdeBorderFactory.createBorder());

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(createDescriptionLabel(), gbConstraints);

    gbConstraints.gridy++;
    myCbReplaceUsages = new JCheckBox(RefactoringBundle.message("replace.instance.qualifiers.with.class.references"));
    panel.add(myCbReplaceUsages, gbConstraints);
    myCbReplaceUsages.setSelected(true);
    return panel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }
}
