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
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.content.Content;
import com.intellij.usageView.*;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usages.*;
import com.intellij.navigation.ItemPresentation;
import com.intellij.util.Processor;

import javax.swing.*;
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
    class StringUsageTarget implements UsageTarget {
      private String myStringToFind;
      private ItemPresentation myItemPresentation = new ItemPresentation() {
        public String getPresentableText() {
          return "String '" + myStringToFind + "'";
        }

        public String getLocationString() {
          return myStringToFind + "!!";
        }

        public TextAttributesKey getTextAttributesKey() {
          return null;
        }

        public Icon getIcon(boolean open) {
          return null;
        }
      };

      public StringUsageTarget(String _stringToFind) {
        myStringToFind = _stringToFind;
      }

      public void findUsages() {
        throw new UnsupportedOperationException();
      }

      public void findUsagesInEditor(FileEditor editor) {
        throw new UnsupportedOperationException();
      }

      public boolean isValid() {
        return true;
      }

      public boolean isReadOnly() {
        return true;
      }

      public VirtualFile[] getFiles() {
        throw new UnsupportedOperationException();
      }

      public String getName() {
        return myStringToFind;
      }

      public ItemPresentation getPresentation() {
        return myItemPresentation;
      }

      public FileStatus getFileStatus() {
        return null;
      }

      public void navigate(boolean requestFocus) {
        throw new UnsupportedOperationException();
      }

      public boolean canNavigate() {
        return false;
      }

      public boolean canNavigateToSource() {
        return false;
      }
    }

    if (manager!=null) {
      findManager.getFindInProjectModel().copyFrom(findModel);
      final FindModel findModelCopy = (FindModel)findModel.clone();

      final UsageViewPresentation presentation = new UsageViewPresentation();

      presentation.setScopeText(FindInProjectUtil.getTitleForScope(findModelCopy));
      presentation.setUsagesString("occurrences of '" + findModelCopy.getStringToFind() + "'");
      presentation.setOpenInNewTab(myToOpenInNewTab);
      presentation.setCodeUsages(false);

      manager.searchAndShowUsages(
        new UsageTarget[] { new StringUsageTarget(findModel.getStringToFind()) },
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
        false,
        true,
        presentation,
        new Factory<ProgressIndicator>() {
        public ProgressIndicator create() {
          return new FindProgressIndicator(myProject, FindInProjectUtil.getTitleForScope(findModelCopy));
        }
      }
      );
    }

    //AsyncFindUsagesCommand command = new AsyncFindUsagesCommand() {
    //  FindProgressIndicator progress;
    //
    //  public void findUsages(final AsyncFindUsagesProcessListener consumer) {
    //    myIsFindInProgress = true;
    //    findManager.getFindInProjectModel().copyFrom(findModel);
    //
    //    // [jeka] should use a _copy_ of findModel, because of RefreshCommand, findModel is reused by later FindUsages commands!!!!
    //    final FindModel findModelCopy = (FindModel)findModel.clone();
    //    progress = new FindProgressIndicator(myProject, FindInProjectUtil.getTitleForScope(findModelCopy));
    //    final Runnable findUsagesRunnable = new Runnable() {
    //      public void run() {
    //        FindInProjectUtil.findUsages(findModelCopy, psiDirectory, myProject, consumer);
    //      }
    //    };
    //
    //    Runnable showUsagesPanelRunnable = new Runnable() {
    //      public void run() {
    //        myIsFindInProgress = false;
    //        if (consumer.getCount()==0) {
    //          if (!progress.isCanceled()) {
    //            String title = "No occurrences of '" + findModel.getStringToFind() + "' found in " + FindInProjectUtil.getTitleForScope(findModelCopy);
    //
    //            Messages.showMessageDialog(
    //              myProject,
    //              title,
    //              "Find in Path",
    //              Messages.getInformationIcon()
    //            );
    //          }
    //        }
    //      }
    //    };
    //
    //    FindInProjectUtil.runProcessWithProgress(progress, findUsagesRunnable, showUsagesPanelRunnable, myProject);
    //  }
    //
    //  public void stopAsyncSearch() {
    //    progress.cancel();
    //  }
    //};
    //
    //showUsagesPanel(
    //  new FindInProjectViewDescriptor(findModel,command),
    //  toOpenInNewTab[0]
    //);
  }

  //private void showUsagesPanel(final FindInProjectViewDescriptor viewDescriptor, boolean toOpenInNewTab) {
  //  String stringToFind = viewDescriptor.getFindModel().getStringToFind();
  //  String name = "Search result";
  //  if (stringToFind != null) {
  //    if (stringToFind.length() > 15) {
  //      stringToFind = stringToFind.substring(0, 15) + "...";
  //    }
  //    name = "Occurrences of '"+stringToFind + "'";
  //  }
  //
  //  if (viewDescriptor.getFindModel().isMultipleFiles() ) {
  //    name += " in " +FindInProjectUtil.getTitleForScope(viewDescriptor.getFindModel());
  //  }
  //  UsageViewManager.getInstance(myProject).addContent(name, viewDescriptor, true, toOpenInNewTab, true, new ProgressFactory () {
  //    public ProgressIndicator createProgress() {
  //      return new FindProgressIndicator(myProject, FindInProjectUtil.getTitleForScope(viewDescriptor.getFindModel()));
  //    }
  //
  //    public boolean continueOnCancel() {
  //      return true;
  //    }
  //  });
  //}

  public boolean isWorkInProgress() {
    return myIsFindInProgress;
  }

  public boolean isEnabled () {
    return !myIsFindInProgress && !ReplaceInProjectManager.getInstance(myProject).isWorkInProgress();
  }
}