package com.intellij.find.impl;

import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class ShowRecentFindUsagesAction extends AnAction {
  public void update(final AnActionEvent e) {
    UsageView usageView = e.getData(UsageView.USAGE_VIEW_KEY);
    Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(usageView != null && project != null);
  }

  public void actionPerformed(AnActionEvent e) {
    UsageView usageView = e.getData(UsageView.USAGE_VIEW_KEY);
    Project project = e.getData(PlatformDataKeys.PROJECT);
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    List<FindUsagesManager.SearchData> history = new ArrayList<FindUsagesManager.SearchData>(findUsagesManager.getFindUsageHistory());

    if (!history.isEmpty()) {
      // skip most recent find usage, it's under your nose
      history.remove(history.size() - 1);
      Collections.reverse(history);
    }
    if (history.isEmpty()) {
      history.add(new FindUsagesManager.SearchData()); // to fill the popup
    }

    BaseListPopupStep<FindUsagesManager.SearchData> step =
      new BaseListPopupStep<FindUsagesManager.SearchData>(FindBundle.message("recent.find.usages.action.title"), history) {
        public Icon getIconFor(final FindUsagesManager.SearchData data) {
          if (data.myElements == null) {
            return null;
          }
          PsiElement psiElement = data.myElements[0].getElement();
          if (psiElement == null) return null;
          return psiElement.getIcon(0);
        }

        @NotNull
        public String getTextFor(final FindUsagesManager.SearchData data) {
          if (data.myElements == null) {
            return FindBundle.message("recent.find.usages.action.nothing");
          }
          PsiElement psiElement = data.myElements[0].getElement();
          if (psiElement == null) return UsageViewBundle.message("node.invalid");
          String scopeString = data.myOptions.searchScope == null ? null : data.myOptions.searchScope.getDisplayName();
          return FindBundle.message("recent.find.usages.action.description",
                                    StringUtil.capitalize(UsageViewUtil.getType(psiElement)),
                                    UsageViewUtil.getDescriptiveName(psiElement),
                                    scopeString == null ? psiElement.getProject().getAllScope().getDisplayName() : scopeString);
        }

        public PopupStep onChosen(final FindUsagesManager.SearchData selectedValue, final boolean finalChoice) {
          if (selectedValue.myElements != null) {
            // later here is for closing the popup first, then rerunning the search
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                findUsagesManager.rerunAndRecallFromHistory(selectedValue);
              }
            });
          }
          return FINAL_CHOICE;
        }
      };
    JBPopupFactory.getInstance().createListPopup(step).show(usageView.getComponent());

  }
}
