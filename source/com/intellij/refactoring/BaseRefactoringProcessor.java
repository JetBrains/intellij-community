package com.intellij.refactoring;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.RefactoringListenerManagerImpl;
import com.intellij.refactoring.listeners.impl.RefactoringTransaction;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.RefactoringChangeMarker;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.Processor;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public abstract class BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.BaseRefactoringProcessor");
  public static final Runnable EMPTY_CALLBACK = EmptyRunnable.getInstance();
  protected final Project myProject;

  private Object myMarkerId;
  private RefactoringTransaction myTransaction;
  private boolean myIsPreviewUsages;
  protected Runnable myPrepareSuccessfulSwingThreadCallback = EMPTY_CALLBACK;


  public BaseRefactoringProcessor(Project project) {
    this(project, null);
  }

  public BaseRefactoringProcessor(Project project, Runnable prepareSuccessfulCallback) {
    myProject = project;
    myPrepareSuccessfulSwingThreadCallback = prepareSuccessfulCallback;
  }

  protected abstract UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand);

  /**
   * Is called inside atomic action.
   */
  protected abstract UsageInfo[] findUsages();

  /**
   * is called when usage search is re-run.
   * @param elements - refreshed elements that are returned by UsageViewDescriptor.getElements()
   */
  protected abstract void refreshElements(PsiElement[] elements);

  /**
   * Is called inside atomic action.
   */
  protected boolean preprocessUsages(UsageInfo[][] usages) {
    prepareSuccessful();
    return true;
  }

  /**
   * Is called inside atomic action.
   */
  protected boolean isPreviewUsages(UsageInfo[] usages) {
    if (UsageViewUtil.hasReadOnlyUsages(usages)) {
      WindowManager.getInstance().getStatusBar(myProject).setInfo("Occurrences found in read-only files");
      return true;
    }
    return myIsPreviewUsages;
  }

  boolean isPreviewUsages() {
    return myIsPreviewUsages;
  }


  public void setPreviewUsages(boolean isPreviewUsages) {
    myIsPreviewUsages = isPreviewUsages;
  }

  public void setPrepareSuccessfulSwingThreadCallback(Runnable prepareSuccessfulSwingThreadCallback) {
    myPrepareSuccessfulSwingThreadCallback = prepareSuccessfulSwingThreadCallback;
  }

  protected RefactoringTransaction getTransaction() {
    return myTransaction;
  }

  /**
   * Is called in a command and inside atomic action.
   */
  protected abstract void performRefactoring(UsageInfo[] usages);

  /**
   * If this method returns <code>true</code>, it means that element to rename is variable, and in the findUsages tree it
   * will be shown with read-access or write-access icons.
   * @return true if element to rename is variable(local, field, or parameter).
   */
  protected boolean isVariable() {
    return false;
  }

  protected abstract String getCommandName();

  public void run(Object markerId) {
    myMarkerId = markerId;

    final UsageInfo[][] usages = new UsageInfo[1][];

    final Runnable findUsagesRunnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            usages[0] = findUsages();
          }
        });
      }
    };

    if (!ApplicationManager.getApplication().runProcessWithProgressSynchronously(findUsagesRunnable, "Looking For Usages...", true, myProject)) return;

    LOG.assertTrue(usages[0] != null);
    if (!preprocessUsages(usages)) return;
    boolean toPreview = isPreviewUsages(usages[0]);
    if (toPreview) {
      FindUsagesCommand findUsagesCommand = new FindUsagesCommand() {
        public UsageInfo[] execute(PsiElement[] elementsToSearch) {
          refreshElements(elementsToSearch);
          findUsagesRunnable.run();
          return usages[0];
        }
      };
      UsageViewDescriptor descriptor = createUsageViewDescriptor(usages[0], findUsagesCommand);
      showUsageView(descriptor, isVariable(), isVariable());
    } else {
      final UsageInfo[] usages1 = usages[0];
      execute(usages1);
    }
  }

  void execute(final UsageInfo[] usages) {
    CommandProcessor.getInstance().executeCommand(
        myProject, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                doRefactoring(usages, null);
              }
            });
          }
        },
        getCommandName(),
        null
    );
  }

  private UsageViewPresentation createPresentation(UsageViewDescriptor descriptor) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText("Refactoring preview");
    presentation.setTargetsNodeText(descriptor.getProcessedElementsHeader());
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setCodeUsagesString(descriptor.getCodeReferencesText(1, 1));    //TODO
    presentation.setNonCodeUsagesString(descriptor.getCommentReferencesText(1, 1)); //TODO
    return presentation;
  }

  private void showUsageView(final UsageViewDescriptor viewDescriptor, boolean showReadAccessIcon, boolean showWriteAccessIcon) {
    UsageViewManager viewManager = myProject.getComponent(UsageViewManager.class);

    final UsageTarget[] targets = PsiElement2UsageTargetAdapter.convert(viewDescriptor.getElements());

    Factory<UsageSearcher> searcherFactory = new Factory<UsageSearcher>() {
      boolean myRequireRefresh = false;

      public UsageSearcher create() {
        UsageSearcher usageSearcher = new UsageSearcher() {
          public void generate(Processor<Usage> processor) {
            if (myRequireRefresh) {
              List<PsiElement> elements = new ArrayList<PsiElement>();
              for (int i = 0; i < targets.length; i++) {
                UsageTarget target = targets[i];
                if (target.isValid()) {
                  elements.add(((PsiElement2UsageTargetAdapter)target).getElement());
                }
              }
              viewDescriptor.refresh(elements.toArray(new PsiElement[elements.size()]));
            }
            else {
              myRequireRefresh = true;
            }

            UsageInfo[] usageInfos = viewDescriptor.getUsages();
            final Usage[] usages = UsageInfo2UsageAdapter.convert(usageInfos);

            for (int i = 0; i < usages.length; i++) {
              Usage usage = usages[i];
              if (!processor.process(usage)) return;
            }

            if (usages.length > 0) {
              ApplicationManager.getApplication().invokeLater(new Runnable() { // some people call processors in write action
                   public void run() {
                     RefactoringUtil.showInfoDialog(getInfo(), myProject);
                   }
                 }, ModalityState.NON_MMODAL);
            }
          }
        };

        return usageSearcher;
      }
    };

    final com.intellij.usages.UsageView usageView = viewManager.searchAndShowUsages(targets, searcherFactory, true, false, createPresentation(viewDescriptor));

    final Runnable refactoringRunnable = new Runnable() {
      public void run() {
        final Set<UsageInfo> excludedUsageInfos = getExcludedUsages(usageView);
        doRefactoring(viewDescriptor.getUsages(), excludedUsageInfos);
      }
    };

    String canNotMakeString = "Cannot perform the refactoring operation.\n" +
        "There were changes in code after the usages have been found.\n" +
        "Please, perform the usage search again.";

    usageView.addPerformOperationAction(refactoringRunnable, getCommandName(), canNotMakeString, "Do Refactor", SystemInfo.isMac ? 0 : 'D');
  }

  private Set<UsageInfo> getExcludedUsages(final UsageView usageView) {
    Set<Usage> usages = usageView.getExcludedUsages();

    Set<UsageInfo> excludedUsageInfos = new HashSet<UsageInfo>();
    for (Iterator<Usage> i = usages.iterator(); i.hasNext();) {
      Usage usage = i.next();
      if (usage instanceof UsageInfo2UsageAdapter) {
        excludedUsageInfos.add(((UsageInfo2UsageAdapter)usage).getUsageInfo());
      }
    }
    return excludedUsageInfos;
  }

  private static String getInfo() {
    return "Press the \"Do Refactor\" button at the bottom of the search results panel\n" +
        "to complete the refactoring operation.";
  }

  private void doRefactoring(UsageInfo[] usages, Set<UsageInfo> excludedUsages) {
    if (usages != null) {
      ArrayList<UsageInfo> array = new ArrayList<UsageInfo>();
      for (int i = 0; i < usages.length; i++) {
        UsageInfo usage = usages[i];
          if (excludedUsages != null && excludedUsages.contains(usage)) {
            continue;
          }
        //if (usage.isExcluded()) continue;
        final PsiElement element = usage.getElement();
        if (element == null) continue;
        if (!element.isWritable()) continue;
        array.add(usage);
      }
      usages = array.toArray(new UsageInfo[array.size()]);
    }

    com.intellij.openapi.localVcs.LvcsAction action = LvcsIntegration.checkinFilesBeforeRefactoring(myProject, getCommandName());

    try {
      final UsageInfo[] _usages = usages;
      ApplicationManager.getApplication().runWriteAction(new RefactoringChangeMarker() {
        public Object getId() {
          return myMarkerId;
        }

        public void run() {
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          RefactoringListenerManagerImpl listenerManager =
              (RefactoringListenerManagerImpl) RefactoringListenerManager.getInstance(myProject);
          myTransaction = listenerManager.startTransaction();
          performRefactoring(_usages);
          myTransaction.commit();
          performPsiSpoilingRefactoring();
        }
      });
    } finally {
        LvcsIntegration.checkinFilesAfterRefactoring(myProject, action);
    }

    if (usages != null) {
      int count = usages.length;
      if (count > 0) {
        WindowManager.getInstance().getStatusBar(myProject).setInfo(count + " occurrence" + (count > 1 ? "s" : "") + " changed");
      } else {
        if (!isPreviewUsages(usages)) {
          WindowManager.getInstance().getStatusBar(myProject).setInfo("No occurrences found");
        }
      }
    }
  }

  /**
   * Refactorings that spoil PSI (write something directly to documents etc.) should
   * do that in this method.<br>
   * This method is called immediately after
   * <code>{@link #performRefactoring(com.intellij.usageView.UsageInfo[])}</code>.
   */
  protected void performPsiSpoilingRefactoring() {

  }

  protected void prepareSuccessful() {
    if (myPrepareSuccessfulSwingThreadCallback != null) {
      // make sure that dialog is closed in swing thread
      if (!ApplicationManager.getApplication().isDispatchThread()) {
        try {
          SwingUtilities.invokeAndWait(myPrepareSuccessfulSwingThreadCallback);
        } catch (InterruptedException e) {
          LOG.error(e);
        } catch (InvocationTargetException e) {
          LOG.error(e);
        }
      } else {
        myPrepareSuccessfulSwingThreadCallback.run();
      }
//      ToolWindowManager.getInstance(myProject).invokeLater(myPrepareSuccessfulSwingThreadCallback);
    }
  }

  /**
   * Override in subclasses
   */
  protected void prepareTestRun() {

  }

  public final void  testRun() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    prepareTestRun();
    UsageInfo[] usages = findUsages();
    UsageInfo[][] u = new UsageInfo[][]{usages};
    preprocessUsages(u);
    RefactoringListenerManagerImpl listenerManager =
        (RefactoringListenerManagerImpl) RefactoringListenerManager.getInstance(myProject);
    myTransaction = listenerManager.startTransaction();
    performRefactoring(u[0]);
    myTransaction.commit();
    performPsiSpoilingRefactoring();
  }

  public boolean showConflicts(final ArrayList<String> conflicts, UsageInfo[][] usages) {
    boolean result;
    if (conflicts.size() > 0 && myPrepareSuccessfulSwingThreadCallback != null) {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(conflicts.toArray(new String[conflicts.size()]), myProject);
      conflictsDialog.show();
      result = conflictsDialog.isOK();
    }
    else {
      result = true;
    }
    if (result) {
      prepareSuccessful();
      return true;
    }
    else {
      return false;
    }
  }
}