package com.intellij.refactoring;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.localvcs.integration.LocalHistoryAction;
import com.intellij.localvcs.integration.LocalHistory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.RefactoringListenerManagerImpl;
import com.intellij.refactoring.listeners.impl.RefactoringTransaction;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public abstract class BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.BaseRefactoringProcessor");
  public static final Runnable EMPTY_CALLBACK = EmptyRunnable.getInstance();
  protected final Project myProject;

  private RefactoringTransaction myTransaction;
  private boolean myIsPreviewUsages;
  protected Runnable myPrepareSuccessfulSwingThreadCallback = EMPTY_CALLBACK;


  protected BaseRefactoringProcessor(Project project) {
    this(project, null);
  }

  protected BaseRefactoringProcessor(Project project, @Nullable Runnable prepareSuccessfulCallback) {
    myProject = project;
    myPrepareSuccessfulSwingThreadCallback = prepareSuccessfulCallback;
  }

  protected abstract UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages);

  /**
   * Is called inside atomic action.
   */
  @NotNull
  protected abstract UsageInfo[] findUsages();

  /**
   * is called when usage search is re-run.
   *
   * @param elements - refreshed elements that are returned by UsageViewDescriptor.getElements()
   */
  protected abstract void refreshElements(PsiElement[] elements);

  /**
   * Is called inside atomic action.
   *
   * @param refUsages usages to be filtered
   * @return true if preprocessed successfully
   */
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    prepareSuccessful();
    return true;
  }

  /**
   * Is called inside atomic action.
   */
  protected boolean isPreviewUsages(UsageInfo[] usages) {
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
   *
   * @return true if element to rename is variable(local, field, or parameter).
   */
  protected boolean isVariable() {
    return false;
  }

  protected abstract String getCommandName();

  protected void doRun() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    final Ref<UsageInfo[]> refUsages = new Ref<UsageInfo[]>();

    final Runnable findUsagesRunnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            refUsages.set(findUsages());
          }
        });
      }
    };

    if (!ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(findUsagesRunnable, RefactoringBundle.message("progress.text"), true, myProject)) {
      return;
    }

    LOG.assertTrue(!refUsages.isNull());
    if (!preprocessUsages(refUsages)) return;
    final UsageInfo[] usages = refUsages.get();
    UsageViewDescriptor descriptor = createUsageViewDescriptor(usages);

    boolean isPreview = isPreviewUsages(usages);
    if (!isPreview) {
      isPreview = !ensureElementsWritable(usages, descriptor) || UsageViewUtil.hasReadOnlyUsages(usages);
      if (isPreview) {
        WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("readonly.occurences.found"));
      }
    }
    if (isPreview) {
      final PsiElement[] elements = descriptor.getElements();
      final PsiElement2UsageTargetAdapter[] targets = PsiElement2UsageTargetAdapter.convert(elements);
      Factory<UsageSearcher> factory = new Factory<UsageSearcher>() {
        public UsageSearcher create() {
          return new UsageSearcher() {
            public void generate(final Processor<Usage> processor) {
              for (int i = 0; i < elements.length; i++) {
                elements[i] = targets[i].getElement();
              }

              refreshElements(elements);
              findUsagesRunnable.run();
              final Usage[] usages = UsageInfo2UsageAdapter.convert(refUsages.get());

              for (Usage usage : usages) {
                processor.process(usage);
              }
            }
          };
        }
      };

      showUsageView(descriptor, factory, usages);
    }
    else {
      execute(usages);
    }
  }

  private boolean ensureElementsWritable(final UsageInfo[] usages, final UsageViewDescriptor descriptor) {
    Set<PsiElement> elements = new THashSet<PsiElement>();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element != null) elements.add(element);
    }
    elements.addAll(getElementsToWrite(descriptor));
    return ensureFilesWritable(myProject, elements);
  }

  private static boolean ensureFilesWritable(final Project project, Collection<? extends PsiElement> elements) {
    PsiElement[] psiElements = elements.toArray(new PsiElement[elements.size()]);
    return CommonRefactoringUtil.checkReadOnlyStatus(project, psiElements);
  }

  void execute(final UsageInfo[] usages) {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Collection<Usage> usagesSet = Arrays.<Usage>asList(UsageInfo2UsageAdapter.convert(usages));
            doRefactoring(usagesSet, new HashSet<Usage>());
          }
        });
      }
    }, getCommandName(), null);
  }

  private static UsageViewPresentation createPresentation(UsageViewDescriptor descriptor, final Usage[] usages) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText(RefactoringBundle.message("usageView.tabText"));
    presentation.setTargetsNodeText(descriptor.getProcessedElementsHeader());
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));
    int codeUsageCount = 0;
    int nonCodeUsageCount = 0;
    Set<PsiFile> codeFiles = new HashSet<PsiFile>();
    Set<PsiFile> nonCodeFiles = new HashSet<PsiFile>();

    for (Usage usage : usages) {
      if (usage instanceof PsiElementUsage) {
        final PsiElementUsage elementUsage = (PsiElementUsage)usage;
        if (elementUsage.isNonCodeUsage()) {
          nonCodeUsageCount++;
          nonCodeFiles.add(elementUsage.getElement().getContainingFile());
        }
        else {
          codeUsageCount++;
          codeFiles.add(elementUsage.getElement().getContainingFile());
        }
      }
    }
    codeFiles.remove(null);
    nonCodeFiles.remove(null);

    presentation.setCodeUsagesString(descriptor.getCodeReferencesText(codeUsageCount, codeFiles.size()));
    presentation.setNonCodeUsagesString(descriptor.getCommentReferencesText(nonCodeUsageCount, nonCodeFiles.size()));
    return presentation;
  }

  private void showUsageView(final UsageViewDescriptor viewDescriptor, final Factory<UsageSearcher> factory, final UsageInfo[] usageInfos) {
    UsageViewManager viewManager = UsageViewManager.getInstance(myProject);

    final PsiElement[] initialElements = viewDescriptor.getElements();
    final UsageTarget[] targets = PsiElement2UsageTargetAdapter.convert(initialElements);
    final Usage[] usages = UsageInfo2UsageAdapter.convert(usageInfos);

    final UsageViewPresentation presentation = createPresentation(viewDescriptor, usages);

    final UsageView usageView = viewManager.showUsages(targets, usages, presentation, factory);

    final Runnable refactoringRunnable = new Runnable() {
      public void run() {
        final Set<Usage> excludedUsageInfos = getExcludedUsages(usageView);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            doRefactoring(usageView.getUsages(), excludedUsageInfos);
          }
        });
      }
    };

    String canNotMakeString = RefactoringBundle.message("usageView.need.reRun");

    usageView.addPerformOperationAction(refactoringRunnable, getCommandName(), canNotMakeString, RefactoringBundle.message("usageView.doAction"));
  }

  private static Set<Usage> getExcludedUsages(final UsageView usageView) {
    Set<Usage> usages = usageView.getExcludedUsages();

    Set<Usage> excludedUsageInfos = new HashSet<Usage>();
    for (Usage usage : usages) {
      if (usage instanceof UsageInfo2UsageAdapter) {
        excludedUsageInfos.add(usage);
      }
    }
    return excludedUsageInfos;
  }

  private void doRefactoring(Collection<Usage> usagesSet, Set<Usage> excludedUsages) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final Set<UsageInfo> usageInfoSet = new HashSet<UsageInfo>();
    if (usagesSet != null) {
      usagesSet.removeAll(excludedUsages);

      for (final Usage usage : usagesSet) {
        if (usage instanceof PsiElementUsage) {
          final PsiElementUsage elementUsage = (PsiElementUsage)usage;
          final PsiElement element = elementUsage.getElement();
          if (element != null && element.isWritable()) {
            usageInfoSet.add(((UsageInfo2UsageAdapter)elementUsage).getUsageInfo());
          }
        }
        else {
          LOG.error("Unknown usage!");
        }
      }
    }

    LocalHistoryAction a = LocalHistory.startAction(myProject, getCommandName());

    final UsageInfo[] usages = usageInfoSet.toArray(new UsageInfo[usageInfoSet.size()]);
    try {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      RefactoringListenerManagerImpl listenerManager = (RefactoringListenerManagerImpl)RefactoringListenerManager.getInstance(myProject);
      myTransaction = listenerManager.startTransaction();
      Set<PsiJavaFile> touchedJavaFiles = getTouchedJavaFiles(usages);
      performRefactoring(usages);
      removeRedundantImports(touchedJavaFiles);
      myTransaction.commit();
      performPsiSpoilingRefactoring();
    }
    finally {
      a.finish();
    }

    if (usages != null) {
      int count = usages.length;
      if (count > 0) {
        WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("statusBar.refactoring.result", count));
      }
      else {
        if (!isPreviewUsages(usages)) {
          WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("statusBar.noUsages"));
        }
      }
    }
  }

  private void removeRedundantImports(final Set<PsiJavaFile> javaFiles) {
    final CodeStyleManager styleManager = PsiManager.getInstance(myProject).getCodeStyleManager();
    for (PsiJavaFile file : javaFiles) {
      try {
        if (file.isValid() && file.getVirtualFile() != null) {
          styleManager.removeRedundantImports(file);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private static Set<PsiJavaFile> getTouchedJavaFiles(final UsageInfo[] usages) {
    Set<PsiJavaFile> javaFiles = new HashSet<PsiJavaFile>();
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element != null) {
        final PsiFile file = element.getContainingFile();
        if (file instanceof PsiJavaFile) {
          javaFiles.add((PsiJavaFile)file);
        }
      }
    }
    return javaFiles;
  }

  /**
   * Refactorings that spoil PSI (write something directly to documents etc.) should
   * do that in this method.<br>
   * This method is called immediately after
   * <code>{@link #performRefactoring(UsageInfo[])}</code>.
   */
  protected void performPsiSpoilingRefactoring() {

  }

  protected void prepareSuccessful() {
    if (myPrepareSuccessfulSwingThreadCallback != null) {
      // make sure that dialog is closed in swing thread
      if (!ApplicationManager.getApplication().isDispatchThread()) {
        try {
          SwingUtilities.invokeAndWait(myPrepareSuccessfulSwingThreadCallback);
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        catch (InvocationTargetException e) {
          LOG.error(e);
        }
      }
      else {
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

  public final void run() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      testRun();
    }
    else {
      doRun();
    }
  }

  private void testRun() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    prepareTestRun();
    Ref<UsageInfo[]> refUsages = new Ref<UsageInfo[]>(findUsages());
    preprocessUsages(refUsages);

    final UsageInfo[] usages = refUsages.get();
    UsageViewDescriptor descriptor = createUsageViewDescriptor(usages);
    if (!ensureElementsWritable(usages, descriptor)) return;

    RefactoringListenerManagerImpl listenerManager = (RefactoringListenerManagerImpl)RefactoringListenerManager.getInstance(myProject);
    myTransaction = listenerManager.startTransaction();
    Set<PsiJavaFile> touchedJavaFiles = getTouchedJavaFiles(usages);
    performRefactoring(usages);
    removeRedundantImports(touchedJavaFiles);
    myTransaction.commit();
    performPsiSpoilingRefactoring();
  }

  protected boolean showConflicts(final List<String> conflicts) {
    if (!conflicts.isEmpty() && myPrepareSuccessfulSwingThreadCallback != null) {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
      conflictsDialog.show();
      if (!conflictsDialog.isOK()) return false;
    }

    prepareSuccessful();
    return true;
  }

  @NotNull
  protected Collection<? extends PsiElement> getElementsToWrite(UsageViewDescriptor descriptor) {
    return Arrays.asList(descriptor.getElements());
  }
}