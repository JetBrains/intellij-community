
package com.intellij.find.replaceInProject;

import com.intellij.find.*;
import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.*;

import javax.swing.*;
import javax.swing.tree.TreePath;

public class ReplaceInProjectManager implements ProjectComponent {
  private Project myProject;
  private boolean myIsFindInProgress = false;

  public static ReplaceInProjectManager getInstance(Project project) {
    return project.getComponent(ReplaceInProjectManager.class);
  }

  public void projectOpened() {}

  public void projectClosed() {}

  public String getComponentName() {
    return "ReplaceInProjectManager";
  }

  public void initComponent() {}

  public void disposeComponent() {}

  public ReplaceInProjectManager(Project project) {
    myProject = project;
  }

  public void replaceInProject(DataContext dataContext) {
    final FindManager findManager = FindManager.getInstance(myProject);
    final FindModel findModel = (FindModel) findManager.getFindInProjectModel().clone();
    findModel.setReplaceState(true);
    FindInProjectUtil.setDirectoryName(findModel, dataContext);

    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor != null){
      String s = editor.getSelectionModel().getSelectedText();
      if (s != null && (s.indexOf("\r") == -1) && (s.indexOf("\n") == -1)){
        findModel.setStringToFind(s);
      }
    }
    if (!findManager.showFindDialog(findModel)){
      return;
    }
    final PsiDirectory psiDirectory = FindInProjectUtil.getPsiDirectory(findModel, myProject);
    if (!findModel.isProjectScope() && psiDirectory == null && findModel.getModuleName()==null){
      return;
    }

    class AsyncFindUsagesCommandImpl implements AsyncFindUsagesCommand {
      ReplaceInProjectViewDescriptor viewDescriptor;
      UsageView usagesPanel;
      ProgressIndicator progress;

      public void findUsages(final AsyncFindUsagesProcessListener consumer) {
        progress = new FindProgressIndicator (myProject, FindInProjectUtil.getTitleForScope(findModel));
        final Runnable findUsagesRunnable = new Runnable() {
          public void run() {
            FindInProjectUtil.findUsages(findManager.getFindInProjectModel(), psiDirectory, myProject, consumer);
          }
        };
        final Runnable showUsagesPanelRunnable = new Runnable() {
          public void run() {
            myIsFindInProgress = false;

            if (consumer.getCount()!=0 && findManager.getFindInProjectModel().isPromptOnReplace()){
              replaceWithPrompt(usagesPanel, viewDescriptor);
            }
          }
        };

        Runnable showUsagesPanelRunnable1 = new Runnable() {
          public void run() {
            // This is important since not all usages are flushed during async processing!
            SwingUtilities.invokeLater(showUsagesPanelRunnable);
          }
        };

        myIsFindInProgress = true;
        findManager.getFindInProjectModel().copyFrom(findModel);
        FindInProjectUtil.runProcessWithProgress(progress, findUsagesRunnable, showUsagesPanelRunnable1, myProject);
      }

      public void stopAsyncSearch() {
        progress.cancel();
      }
    };

    AsyncFindUsagesCommandImpl findUsagesCommand = new AsyncFindUsagesCommandImpl();
    ReplaceInProjectViewDescriptor viewDescriptor = new ReplaceInProjectViewDescriptor(findModel, findUsagesCommand);
    UsageView usagesPanel = showUsagesPanel(viewDescriptor);

    findUsagesCommand.viewDescriptor = viewDescriptor;
    findUsagesCommand.usagesPanel = usagesPanel;
  }

  private void replaceWithPrompt(final UsageView usageView, final ReplaceInProjectViewDescriptor viewDescriptor) {
    final UsageInfo[] usages = usageView.getUsages();

    if (UsageViewUtil.hasReadOnlyUsages(usages)){
      WindowManager.getInstance().getStatusBar(myProject).setInfo("Occurrences found in read-only files");
      return;
    }

    usageView.expandAll();
    for(int i = 0; i < usages.length; i++){
      final PsiFile psiFile = usages[i].getElement().getContainingFile();
      if (!psiFile.isWritable()) continue;
      final UsageInfo usageInfo = usages[i];

      Runnable selectOnEditorRunnable = new Runnable() {
        public void run() {
          final VirtualFile virtualFile = psiFile.getVirtualFile();
          if (virtualFile != null &&
              ApplicationManager.getApplication().runReadAction(
                new Computable<Boolean>() {
                  public Boolean compute() {
                    return virtualFile.isValid() ? Boolean.TRUE : Boolean.FALSE;
                  }
                }
              ).booleanValue()) {
            int startOffset = usageView.getTextOffset(usageInfo);
            int endOffset = usageView.getTextEndOffset(usageInfo);
            Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
            if (endOffset <= document.getTextLength()) {
              OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, virtualFile, startOffset);
              FileEditorManager.getInstance(myProject).openTextEditor(descriptor, false);
              descriptor = new OpenFileDescriptor(myProject, virtualFile, endOffset);
              Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(descriptor, false);
              if (editor != null) {
                editor.getSelectionModel().setSelection(startOffset, endOffset);
              }
              usageView.selectUsage(usageInfo);
            }
          }
        }
      };
      CommandProcessor.getInstance().executeCommand(myProject, selectOnEditorRunnable, "Select on Editor", null);

      String title = "Replace Usage " + (i + 1) + " of " + usages.length + " Found";

      int result = FindManager.getInstance(myProject).showPromptDialog(viewDescriptor.getFindModel(), title);

      if (result == PromptResult.CANCEL){
        return;
      }
      if (result == PromptResult.SKIP){
        continue;
      }

      final int currentNumber = i;
      if (result == PromptResult.OK){
        Runnable runnable = new Runnable() {
          public void run() {
            doReplace(usageView, usages[currentNumber], viewDescriptor);
            usageView.removeUsage(usages[currentNumber]);
          }
        };
        CommandProcessor.getInstance().executeCommand(myProject, runnable, "Replace", null);
        if (usageView.getUsagesNodeCount() == 0){
          UsageViewManager.getInstance(myProject).closeContent(usageView);
          return;
        }
      }

      if (result == PromptResult.ALL_IN_THIS_FILE){
        final int[] nextNumber = new int[1];
        Runnable runnable = new Runnable() {
          public void run() {
            int j = currentNumber;
            for(; j < usages.length; j++){
              PsiFile otherPsiFile = usages[j].getElement().getContainingFile();
              if (!otherPsiFile.equals(psiFile)){
                break;
              }
              doReplace(usageView, usages[j], viewDescriptor);
              usageView.removeUsage(usages[j]);
            }
            if (j >= usages.length){
              UsageViewManager.getInstance(myProject).closeContent(usageView);
            }
            nextNumber[0] = j;
          }
        };
        CommandProcessor.getInstance().executeCommand(myProject, runnable, "Replace", null);
        if (usageView.getUsagesNodeCount() == 0){
          UsageViewManager.getInstance(myProject).closeContent(usageView);
          return;
        }
        i = nextNumber[0] - 1;
      }

      if (result == PromptResult.ALL_FILES){
        /*
        Runnable process = new Runnable() {
          public void run() {
            for(int j = currentNumber; j < usages.length; j++){
              doReplace(usageView, usages[j], viewDescriptor);
            }
          }
        };
        Runnable finishProcess = new Runnable() {
          public void run() {
            ExternalUISupport.getInstance(myProject).closeContent(usageView);
          }
        };
        ProgressUtil.runPsiCommandWithProgress(myProject, process, finishProcess, "Replacing...", "Replace", false);
        */

        CommandProcessor.getInstance().executeCommand(
            myProject, new Runnable() {
            public void run() {
              for(int j = currentNumber; j < usages.length; j++){
                doReplace(usageView, usages[j], viewDescriptor);
              }
              UsageViewManager.getInstance(myProject).closeContent(usageView);
            }
          },
          "Replace",
          null
        );
        break;
      }
    }
  }

    private UsageView showUsagesPanel(final ReplaceInProjectViewDescriptor viewDescriptor) {
      String name = "Search result";
      String stringToFind = viewDescriptor.getFindModel().getStringToFind();
      if (stringToFind != null) {
        if (stringToFind.length() > 15) {
          stringToFind = stringToFind.substring(0, 15) + "...";
        }
        name = "Occurrences of '" + stringToFind + "'";
        if (viewDescriptor.getFindModel().isMultipleFiles() ) {
          name += " in " + FindInProjectUtil.getTitleForScope(viewDescriptor.getFindModel());
        }
      }
      UsageView usagesPanel = UsageViewManager.getInstance(myProject).addContent(name, viewDescriptor, false, false, false, new ProgressFactory() {
        public ProgressIndicator createProgress() {
          return new FindProgressIndicator(myProject, FindInProjectUtil.getTitleForScope(viewDescriptor.getFindModel()));
        }

        public boolean continueOnCancel() {
          return true;
        }
      });
      usagesPanel.addButton(0, new ReplaceSelectedAction(usagesPanel, viewDescriptor));
      addReplaceAllAction(usagesPanel, viewDescriptor);
      return usagesPanel;

    }

  private void addReplaceAllAction(final UsageView usageView, final ReplaceInProjectViewDescriptor viewDescriptor) {
    final Runnable replaceRunnable = new Runnable() {
      public void run() {
        doReplace(usageView, usageView.getUsages(), viewDescriptor);
      }
    };
    usageView.addDoProcessAction(replaceRunnable, "Replace All",null,"&Do Replace All");
  }

  private class ReplaceSelectedAction extends AnAction {
    private UsageView myUsagesPanel;
    private ReplaceInProjectViewDescriptor myTreeInfo;

    public ReplaceSelectedAction(UsageView usagesPanel, ReplaceInProjectViewDescriptor treeInfo){
      super(
        "Rep&lace Selected",
        "Replace selected occurrences of \"" + treeInfo.getFindModel().getStringToFind() + "\" to \"" + treeInfo.getFindModel().getStringToReplace() + "\"",
        null
      );
      myUsagesPanel=usagesPanel;
      myTreeInfo=treeInfo;
    }

    public void actionPerformed(AnActionEvent event) {
      doReplaceSelected(myUsagesPanel, myTreeInfo);
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(!myIsFindInProgress);
      super.update(e);
    }
  }

  private void doReplace(UsageView usageView, UsageInfo[] usages, ReplaceInProjectViewDescriptor treeInfo) {
    for(int i = 0; i < usages.length; i++){
      doReplace(usageView, usages[i], treeInfo);
    }
  }

  private void doReplace(final UsageView usageView, final UsageInfo usage, final ReplaceInProjectViewDescriptor treeInfo) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        if (usageView.isExcluded(usage)){
          return;
        }
        PsiFile file = (PsiFile)usage.getElement();
        Project project = file.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (!document.isWritable()) return;

        final int textOffset = usageView.getTextOffset(usage);
        if (textOffset < 0 || textOffset >= document.getTextLength()){
          return;
        }
        final int textEndOffset = usageView.getTextEndOffset(usage);
        if (textEndOffset < 0 || textOffset > document.getTextLength()){
          return;
        }
        FindManager findManager = FindManager.getInstance(myProject);
        final CharSequence foundString = document.getCharsSequence().subSequence(textOffset, textEndOffset);
        FindResult findResult = findManager.findString(foundString, 0, treeInfo.getFindModel());
        if (findResult == null || !findResult.isStringFound()){
          return;
        }
        String stringToReplace = findManager.getStringToReplace(foundString.toString(), treeInfo.getFindModel());
        document.replaceString(textOffset, textEndOffset, stringToReplace);
      }
    });
  }

  private void doReplaceSelected(final UsageView usageView, final ReplaceInProjectViewDescriptor treeInfo) {
    TreePath[] paths = usageView.getTree().getSelectionPaths();
    if (paths == null || paths.length == 0){
      return;
    }

    final UsageInfo[] usages = usageView.getSelectedUsages();
    if(usages == null){
      return;
    }
    if (UsageViewUtil.hasReadOnlyUsages(usages)){
      int result = Messages.showOkCancelDialog(
        usageView.getTree(),
        "Occurrences found in read-only files.\n" +
          "The operation will not affect them.\n" +
          "Continue anyway?",
        "Read-only Files Found",
        Messages.getWarningIcon()
      );
      if (result != 0){
        return;
      }
    }

    CommandProcessor.getInstance().executeCommand(
        myProject, new Runnable() {
        public void run() {
          doReplace(usageView, usages, treeInfo);
          for (int i = 0; i < usages.length; i++) {
            usageView.removeUsage(usages[i]);
          }

          if (usageView.getUsagesNodeCount() == 0){
            UsageViewManager.getInstance(myProject).closeContent(usageView);
            return;
          }
          usageView.setFocused();
        }
      },
      "Replace",
      null
    );
  }

  public boolean isWorkInProgress() {
    return myIsFindInProgress;
  }

  public boolean isEnabled () {
    return !myIsFindInProgress && !FindInProjectManager.getInstance(myProject).isWorkInProgress();
  }
}