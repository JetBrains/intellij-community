package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 14, 2005
 * Time: 5:40:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class CommonFindUsagesDialog extends FindUsagesDialog {
  public CommonFindUsagesDialog(PsiElement element,
                 Project project,
                 FindUsagesOptions findUsagesOptions,
                 boolean toShowInNewTab,
                                     boolean mustOpenInNewTab,
                 boolean isSingleFile) {
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile);
  }

  protected void update() {
  }

  protected JPanel createFindWhatPanel() {
    return null;
  }

  protected JComponent getPreferredFocusedControl() {
    return null;
  }
}
