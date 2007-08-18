package com.intellij.find.findInProject;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usages.*;
import com.intellij.util.Processor;

import java.util.ArrayList;
import java.util.Iterator;

public class FindInProjectManager {
  private Project myProject;
  private ArrayList<Content> myUsagesContents = new ArrayList<Content>();
  private boolean myToOpenInNewTab = false;
  private volatile boolean myIsFindInProgress = false;

  public static FindInProjectManager getInstance(Project project) {
    return ServiceManager.getService(project, FindInProjectManager.class);
  }

  public FindInProjectManager(Project project) {
    myProject = project;
  }

  public void findInProject(DataContext dataContext) {
    Iterator<Content> iterator = myUsagesContents.iterator();
    while (iterator.hasNext()) {
      Content content = iterator.next();
      if (content.getComponent().getParent() == null) {
        iterator.remove();
      }
    }

    boolean isOpenInNewTabEnabled;
    final boolean[] toOpenInNewTab = new boolean[1];
    Content selectedContent = UsageViewManager.getInstance(myProject).getSelectedContent(true);
    if (selectedContent != null && selectedContent.isPinned()) {
      toOpenInNewTab[0] = true;
      isOpenInNewTabEnabled = false;
    }
    else {
      toOpenInNewTab[0] = myToOpenInNewTab;
      isOpenInNewTabEnabled = UsageViewManager.getInstance(myProject).getReusableContentsCount() > 0;
    }

    final FindManager findManager = FindManager.getInstance(myProject);
    final FindModel findModel = (FindModel) findManager.getFindInProjectModel().clone();
    findModel.setReplaceState(false);
    findModel.setOpenInNewTabVisible(true);
    findModel.setOpenInNewTabEnabled(isOpenInNewTabEnabled);
    findModel.setOpenInNewTab(toOpenInNewTab[0]);
    FindInProjectUtil.setDirectoryName(findModel, dataContext);

    Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (editor != null){
      String s = editor.getSelectionModel().getSelectedText();
      if (s != null && !s.contains("\r") && !s.contains("\n")){
        findModel.setStringToFind(s);
      }
    }

    if (!findManager.showFindDialog(findModel)){
      findModel.setOpenInNewTabVisible(false);
      return;
    }
    findModel.setOpenInNewTabVisible(false);
    final PsiDirectory psiDirectory = FindInProjectUtil.getPsiDirectory(findModel, myProject);
    if (findModel.getDirectoryName() != null && psiDirectory == null){
      return;
    }
    if (isOpenInNewTabEnabled) {
      myToOpenInNewTab = toOpenInNewTab[0] = findModel.isOpenInNewTab();
    }

    com.intellij.usages.UsageViewManager manager = com.intellij.usages.UsageViewManager.getInstance(myProject);

    if (manager == null) return;
    findManager.getFindInProjectModel().copyFrom(findModel);
    final FindModel findModelCopy = (FindModel)findModel.clone();
    final UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(myToOpenInNewTab, findModelCopy);
    final boolean showPanelIfOnlyOneUsage = !FindSettings.getInstance().isSkipResultsWithOneUsage();

    FindUsagesProcessPresentation processPresentation = FindInProjectUtil.setupProcessPresentation(myProject, showPanelIfOnlyOneUsage, presentation);

    manager.searchAndShowUsages(
        new UsageTarget[] { new FindInProjectUtil.StringUsageTarget(findModel.getStringToFind())},
      new Factory<UsageSearcher>() {
        public UsageSearcher create() {
          return new UsageSearcher() {
            public void generate(final Processor<Usage> processor) {
              myIsFindInProgress = true;

              try {
                FindInProjectUtil.findUsages(findModelCopy, psiDirectory, myProject,
                                             new FindInProjectUtil.AsyncFindUsagesProcessListener2ProcessorAdapter(processor));
              }
              finally {
                myIsFindInProgress = false;
              }
            }
          };
        }
      },
      processPresentation,
      presentation,
      null
    );
  }

  public boolean isWorkInProgress() {
    return myIsFindInProgress;
  }

  public boolean isEnabled () {
    return !myIsFindInProgress && !ReplaceInProjectManager.getInstance(myProject).isWorkInProgress();
  }
}
