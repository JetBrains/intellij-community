package com.intellij.find.findInProject;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindProgressIndicator;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.content.Content;
import com.intellij.usageView.*;

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

    AsyncFindUsagesCommand command = new AsyncFindUsagesCommand() {
      FindProgressIndicator progress;

      public void findUsages(final AsyncFindUsagesProcessListener consumer) {
        myIsFindInProgress = true;
        findManager.getFindInProjectModel().copyFrom(findModel);

        // [jeka] should use a _copy_ of findModel, because of RefreshCommand, findModel is reused by later FindUsages commands!!!!
        final FindModel findModelCopy = (FindModel)findModel.clone();
        progress = new FindProgressIndicator(myProject, FindInProjectUtil.getTitleForScope(findModelCopy));
        final Runnable findUsagesRunnable = new Runnable() {
          public void run() {
            FindInProjectUtil.findUsages(findModelCopy, psiDirectory, myProject, consumer);
          }
        };

        Runnable showUsagesPanelRunnable = new Runnable() {
          public void run() {
            myIsFindInProgress = false;
            if (consumer.getCount()==0) {
              if (!progress.isCanceled()) {
                String title = "No occurrences of '" + findModel.getStringToFind() + "' found in " + FindInProjectUtil.getTitleForScope(findModelCopy);

                Messages.showMessageDialog(
                  myProject,
                  title,
                  "Find in Path",
                  Messages.getInformationIcon()
                );
              }
            }
          }
        };

        FindInProjectUtil.runProcessWithProgress(progress, findUsagesRunnable, showUsagesPanelRunnable, myProject);
      }

      public void stopAsyncSearch() {
        progress.cancel();
      }
    };

    showUsagesPanel(
      new FindInProjectViewDescriptor(findModel,command),
      toOpenInNewTab[0]
    );
  }

  private void showUsagesPanel(final FindInProjectViewDescriptor viewDescriptor, boolean toOpenInNewTab) {
    String stringToFind = viewDescriptor.getFindModel().getStringToFind();
    String name = "Search result";
    if (stringToFind != null) {
      if (stringToFind.length() > 15) {
        stringToFind = stringToFind.substring(0, 15) + "...";
      }
      name = "Occurrences of '"+stringToFind + "'";
    }

    if (viewDescriptor.getFindModel().isMultipleFiles() ) {
      name += " in " +FindInProjectUtil.getTitleForScope(viewDescriptor.getFindModel());
    }
    UsageViewManager.getInstance(myProject).addContent(name, viewDescriptor, true, toOpenInNewTab, true, new ProgressFactory () {
      public ProgressIndicator createProgress() {
        return new FindProgressIndicator(myProject, FindInProjectUtil.getTitleForScope(viewDescriptor.getFindModel()));
      }

      public boolean continueOnCancel() {
        return true;
      }
    });
  }

  public boolean isWorkInProgress() {
    return myIsFindInProgress;
  }

  public boolean isEnabled () {
    return !myIsFindInProgress && !ReplaceInProjectManager.getInstance(myProject).isWorkInProgress();
  }
}