package com.intellij.find.findInProject;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindProgressIndicator;
import com.intellij.find.FindSettings;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.content.Content;
import com.intellij.usageView.*;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usages.*;
import com.intellij.util.Processor;

import java.util.ArrayList;

public class FindInProjectManager implements ProjectComponent {
  private Project myProject;
  private ArrayList myUsagesContents = new ArrayList();
  private boolean myToOpenInNewTab = false;
  private boolean myIsFindInProgress = false;

  public void projectOpened() {}

  public void projectClosed() {}

  public String getComponentName() {
    return "FindInProjectManager";
  }

  public void initComponent() {}

  public void disposeComponent() {}

  public static FindInProjectManager getInstance(Project project) {
    return project.getComponent(FindInProjectManager.class);
  }

  public FindInProjectManager(Project project) {
    myProject = project;
  }

  public void findInProject(DataContext dataContext) {
    ArrayList contentsToDelete = new ArrayList();
    for(int i = 0; i < myUsagesContents.size(); i++){
      Content content = (Content)myUsagesContents.get(i);
      if (content.getComponent().getParent() == null){
        contentsToDelete.add(content);
      }
    }

    for(int i = 0; i < contentsToDelete.size(); i++){
      myUsagesContents.remove(contentsToDelete.get(i));
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
      isOpenInNewTabEnabled = (UsageViewManager.getInstance(myProject).getReusableContentsCount() > 0);
    }

    final FindManager findManager = FindManager.getInstance(myProject);
    final FindModel findModel = (FindModel) findManager.getFindInProjectModel().clone();
    findModel.setReplaceState(false);
    findModel.setOpenInNewTabVisible(true);
    findModel.setOpenInNewTabEnabled(isOpenInNewTabEnabled);
    findModel.setOpenInNewTab(toOpenInNewTab[0]);
    FindInProjectUtil.setDirectoryName(findModel, dataContext);

    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor != null){
      String s = editor.getSelectionModel().getSelectedText();
      if (s != null && (s.indexOf("\r") == -1) && (s.indexOf("\n") == -1)){
        findModel.setStringToFind(s);
      }
    }

    if (!findManager.showFindDialog(findModel)){
      findModel.setOpenInNewTabVisible(false);
      return;
    }
    findModel.setOpenInNewTabVisible(false);
    final PsiDirectory psiDirectory = FindInProjectUtil.getPsiDirectory(findModel, myProject);
    if (!findModel.isProjectScope() && findModel.getModuleName()==null && psiDirectory == null){
      return;
    }
    if (isOpenInNewTabEnabled) {
      myToOpenInNewTab = toOpenInNewTab[0] = findModel.isOpenInNewTab();
    }

    com.intellij.usages.UsageViewManager manager = myProject.getComponent(com.intellij.usages.UsageViewManager.class);

    if (manager!=null) {
      findManager.getFindInProjectModel().copyFrom(findModel);
      final FindModel findModelCopy = (FindModel)findModel.clone();
      final UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(myToOpenInNewTab, findModelCopy);

      manager.searchAndShowUsages(
        new UsageTarget[] { new FindInProjectUtil.StringUsageTarget(findModel.getStringToFind()) },
        new Factory<UsageSearcher>() {
          public UsageSearcher create() {
            return new UsageSearcher() {

            public void generate(final Processor<Usage> processor) {
              myIsFindInProgress = true;

              FindInProjectUtil.findUsages(findModelCopy, psiDirectory, myProject, new AsyncFindUsagesProcessListener() {
                int count;

                public void foundUsage(UsageInfo info) {
                  ++count;
                  processor.process(new UsageInfo2UsageAdapter(info));
                }

                public void findUsagesCompleted() {
                }

                public int getCount() {
                  return count;
                }
              });
              myIsFindInProgress = false;
            }
          };
          }
        },
        !FindSettings.getInstance().isSkipResultsWithOneUsage(),
        true,
        presentation,
        new Factory<ProgressIndicator>() {
          public ProgressIndicator create() {
            return new FindProgressIndicator(myProject, FindInProjectUtil.getTitleForScope(findModelCopy));
          }
        },
        null
      );
    }
  }

  public boolean isWorkInProgress() {
    return myIsFindInProgress;
  }

  public boolean isEnabled () {
    return !myIsFindInProgress && !ReplaceInProjectManager.getInstance(myProject).isWorkInProgress();
  }
}
