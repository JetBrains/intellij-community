package com.intellij.openapi.vcs.impl;

import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.localVcs.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.Annotater;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vcs.merge.AbstractMergeAction;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.merge.MultipleFileMergeDialog;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.*;
import com.intellij.util.ContentsUtil;
import com.intellij.util.ui.ConfirmationDialog;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

public class AbstractVcsHelperImpl extends AbstractVcsHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl");

  private Project myProject;

  public AbstractVcsHelperImpl(Project project) {
    myProject = project;
  }

  private static final Key<NewErrorTreeViewPanel> KEY = Key.create("AbstractVcsHelper.KEY");

  public void showCodeSmellErrors(final List<CodeSmellInfo> smellList) {

    Collections.sort(smellList, new Comparator<CodeSmellInfo>() {
      public int compare(final CodeSmellInfo o1, final CodeSmellInfo o2) {
        return o1.getTextRange().getStartOffset() - o2.getTextRange().getStartOffset();
      }
    });

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        if (smellList.isEmpty()) {
          return;
        }

        final NewErrorTreeViewPanel errorTreeView = new NewErrorTreeViewPanel(myProject, null) {
          protected boolean canHideWarnings() {
            return false;
          }
        };
        CommandProcessor commandProcessor = CommandProcessor.getInstance();
        commandProcessor.executeCommand(myProject, new Runnable() {
          public void run() {
            final MessageView messageView = myProject.getComponent(MessageView.class);
            final String tabDisplayName = VcsBundle.message("code.smells.error.messages.tab.name");
            final Content content =
              PeerFactory.getInstance().getContentFactory().createContent(errorTreeView.getComponent(), tabDisplayName, true);
            content.putUserData(KEY, errorTreeView);
            messageView.addContent(content);
            messageView.setSelectedContent(content);
            removeContents(content, tabDisplayName);
            messageView.addContentManagerListener(new MyContentDisposer(content, messageView));
          }
        }, VcsBundle.message("command.name.open.error.message.view"), null);

        FileDocumentManager fileManager = FileDocumentManager.getInstance();


        for (CodeSmellInfo smellInfo : smellList) {
          VirtualFile file = fileManager.getFile(smellInfo.getDocument());
          if (smellInfo.getSeverity() == HighlightSeverity.ERROR) {
            errorTreeView.addMessage(MessageCategory.ERROR, new String[]{smellInfo.getDescription()}, file, smellInfo.getStartLine(),
                                     smellInfo.getStartColumn(), null);
          }
          else {//if (smellInfo.getSeverity() == HighlightSeverity.WARNING) {
            errorTreeView.addMessage(MessageCategory.WARNING, new String[]{smellInfo.getDescription()}, file, smellInfo.getStartLine(),
                                     smellInfo.getStartColumn(), null);
          }

        }

        ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
      }

    });

  }

    public void showFileHistory(VcsHistoryProvider vcsHistoryProvider, FilePath path) {
        try {
          VcsHistorySession session = vcsHistoryProvider.createSessionFor(path);
          List<VcsFileRevision> revisionsList = session.getRevisionList();
          if (revisionsList.isEmpty()) return;
    
          String actionName = VcsBundle.message("action.name.file.history", path.getName());
    
          ContentManager contentManager = ProjectLevelVcsManagerEx.getInstanceEx(myProject).getContentManager();
    
          FileHistoryPanelImpl fileHistoryPanel = new FileHistoryPanelImpl(myProject,
                                                                           path, session, vcsHistoryProvider, contentManager);
          Content content = PeerFactory.getInstance().getContentFactory().createContent(fileHistoryPanel, actionName, true);
          ContentsUtil.addOrReplaceContent(contentManager, content, true);
    
          ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.VCS);
          toolWindow.activate(null);
        }
        catch (Exception exception) {
          reportError(exception);
        }
        
    }

  public void showRollbackChangesDialog(List<Change> changes) {
    RollbackChangesDialog.rollbackChanges(myProject, changes);
  }

  @Nullable
  public Collection<VirtualFile> selectFilesToProcess(final List<VirtualFile> files, final String title, @Nullable final String prompt,
                                                      final String singleFileTitle, final String singleFilePromptTemplate,
                                                      final VcsShowConfirmationOption confirmationOption) {
    if (files.size() == 1 && singleFilePromptTemplate != null) {
      String filePrompt = MessageFormat.format(singleFilePromptTemplate, files.get(0).getPresentableUrl());
      if (ConfirmationDialog.requestForConfirmation(confirmationOption, myProject, filePrompt, singleFileTitle, Messages.getQuestionIcon())) {
        return files;
      }
      return null;
    }

    SelectFilesDialog dlg = new SelectFilesDialog(myProject, files, prompt, confirmationOption);
    dlg.setTitle(title);
    dlg.show();
    return dlg.isOK() ? dlg.getSelectedFiles() : null;
  }

  @Nullable
  public Collection<FilePath> selectFilePathsToProcess(final List<FilePath> files, final String title, @Nullable final String prompt,
                                                       final String singleFileTitle, final String singleFilePromptTemplate,
                                                       final VcsShowConfirmationOption confirmationOption) {
    if (files.size() == 1 && singleFilePromptTemplate != null) {
      String filePrompt = MessageFormat.format(singleFilePromptTemplate, files.get(0).getPresentableUrl());
      if (ConfirmationDialog.requestForConfirmation(confirmationOption, myProject, filePrompt, singleFileTitle, Messages.getQuestionIcon())) {
        return files;
      }
      return null;
    }

    SelectFilePathsDialog dlg = new SelectFilePathsDialog(myProject, files, prompt, confirmationOption);
    dlg.setTitle(title);
    dlg.show();
    return dlg.isOK() ? dlg.getSelectedFiles() : null;
  }

  protected void reportError(Exception exception) {
        exception.printStackTrace();
        Messages.showMessageDialog(exception.getLocalizedMessage(), VcsBundle.message("message.title.could.not.load.file.history"), Messages.getErrorIcon());
    }

  public void showErrors(final List<VcsException> abstractVcsExceptions, final String tabDisplayName) {
    LOG.assertTrue(tabDisplayName != null, "tabDisplayName should not be null");
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        if (abstractVcsExceptions.isEmpty()) {
          removeContents(null, tabDisplayName);
          return;
        }

        final NewErrorTreeViewPanel errorTreeView = new NewErrorTreeViewPanel(myProject, null) {
          protected boolean canHideWarnings() {
            return false;
          }
        };
        CommandProcessor commandProcessor = CommandProcessor.getInstance();
        commandProcessor.executeCommand(myProject, new Runnable() {
          public void run() {
            final MessageView messageView = myProject.getComponent(MessageView.class);
            final Content content =
              PeerFactory.getInstance().getContentFactory().createContent(errorTreeView.getComponent(), tabDisplayName, true);
            content.putUserData(KEY, errorTreeView);
            messageView.addContent(content);
            messageView.setSelectedContent(content);
            removeContents(content, tabDisplayName);
            messageView.addContentManagerListener(new MyContentDisposer(content, messageView));
          }
        }, VcsBundle.message("command.name.open.error.message.view"), null);

        for (final VcsException exception : abstractVcsExceptions) {
          String[] messages = exception.getMessages();
          if (messages.length == 0) messages = new String[]{VcsBundle.message("exception.text.unknown.error")};
          errorTreeView.addMessage(getErrorCategory(exception), messages, exception.getVirtualFile(), -1, -1, null);
        }
        
        ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
      }
    });
  }

  private static int getErrorCategory(VcsException exception) {
    if (exception.isWarning()) {
      return MessageCategory.WARNING;
    }
    else {
      return MessageCategory.ERROR;
    }
  }

  protected void removeContents(Content notToRemove, final String tabDisplayName) {
    MessageView messageView = myProject.getComponent(MessageView.class);
    Content[] contents = messageView.getContents();
    for (Content content : contents) {
      LOG.assertTrue(content != null);
      if (content.isPinned()) continue;
      if (tabDisplayName.equals(content.getDisplayName()) && content != notToRemove) {
        ErrorTreeView listErrorView = (ErrorTreeView)content.getComponent();
        if (listErrorView != null) {
          if (messageView.removeContent(content)) {
            content.release();
          }
        }
      }
    }
  }

  public void markFileAsUpToDate(final VirtualFile file) {
    LvcsObject object;
    if (file.isDirectory()) {
      object = LocalVcs.getInstance(myProject).findDirectory(file.getPath());
    }
    else {
      object = LocalVcs.getInstance(myProject).findFile(file.getPath());
    }

    if (object != null) {
      object.getRevision().setUpToDate(true);
    }
  }

  public LvcsAction startVcsAction(String actionName) {
    return LocalVcs.getInstance(myProject).startAction(actionName, "", false);
  }

  public void finishVcsAction(LvcsAction action) {
    action.finish();
  }

  public List<VcsException> runTransactionRunnable(AbstractVcs vcs, TransactionRunnable runnable, Object vcsParameters) {
    List<VcsException> exceptions = new ArrayList<VcsException>();

    TransactionProvider transactionProvider = vcs.getTransactionProvider();
    boolean transactionSupported = transactionProvider != null;

    if (transactionSupported) {
      try {
        transactionProvider.startTransaction(vcsParameters);
      }
      catch (VcsException e) {
        return Collections.singletonList(e);
      }
    }

    runnable.run(exceptions);

    if (transactionSupported) {
      if (exceptions.isEmpty()) {
        try {
          transactionProvider.commitTransaction(vcsParameters);
        }
        catch (VcsException e) {
          exceptions.add(e);
          transactionProvider.rollbackTransaction(vcsParameters);
        }
      }
      else {
        transactionProvider.rollbackTransaction(vcsParameters);
      }
    }

    return exceptions;
  }

  public String getUpToDateFilePath(VirtualFile file) {
    LvcsFile lvcsFile = LocalVcs.getInstance(myProject).findFile(file.getPath());
    if (lvcsFile == null) return null;
    LvcsRevision lastUpToDateRevision = StatusUtil.findLastUpToDateRevision(lvcsFile);
    return lastUpToDateRevision.getAbsolutePath().replace('/', File.separatorChar);
  }

  public void optimizeImportsAndReformatCode(final Collection<VirtualFile> files,
                                             final VcsConfiguration configuration,
                                             final Runnable finishAction,
                                             final boolean checkinProject) {

    final Runnable performCheckoutAction = new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        finishAction.run();
      }
    };

    final Runnable reformatCodeAndPerformCheckout = new Runnable() {
      public void run() {
        if (reformat(configuration, checkinProject)) {
          new ReformatCodeProcessor(myProject, getPsiFiles(files), performCheckoutAction).run();
        }
        else {
          performCheckoutAction.run();
        }
      }
    };

    if (configuration.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT) {
      new OptimizeImportsProcessor(myProject, getPsiFiles(files), reformatCodeAndPerformCheckout).run();
    }
    else {
      reformatCodeAndPerformCheckout.run();
    }

  }

  public void showAnnotation(FileAnnotation annotation, VirtualFile file) {
    new Annotater(annotation, myProject, file).showAnnotation();
  }

  public void showDifferences(final VcsFileRevision version1, final VcsFileRevision version2, final File file) {
    try {
      version1.loadContent();
      version2.loadContent();

      if (Comparing.equal(version1.getContent(), version2.getContent())) {
        Messages.showInfoMessage(VcsBundle.message("message.text.versions.are.identical"), VcsBundle.message("message.title.diff"));
      }

      final SimpleDiffRequest request = new SimpleDiffRequest(myProject, file.getAbsolutePath());

      final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());
      if (fileType.isBinary()) {
        Messages.showInfoMessage(VcsBundle.message("message.text.binary.versions.differ"), VcsBundle.message("message.title.diff"));

        return;
      }

      final DiffContent content1 = getContentForVersion(version1, file);
      final DiffContent content2 = getContentForVersion(version2, file);

      if (version2.getRevisionNumber().compareTo(version1.getRevisionNumber()) > 0) {
        request.setContents(content2, content1);
        request.setContentTitles(version2.getRevisionNumber().asString(), version1.getRevisionNumber().asString());
      }
      else {
        request.setContents(content1, content2);
        request.setContentTitles(version1.getRevisionNumber().asString(), version2.getRevisionNumber().asString());
      }

      DiffManager.getInstance().getDiffTool().show(request);
    }
    catch (VcsException e) {
      showError(e, VcsBundle.message("message.title.diff"));
    }
    catch (IOException e) {
      showError(new VcsException(e), VcsBundle.message("message.title.diff"));
    }

  }

  public void showChangesBrowser(List<CommittedChangeList> changelists) {
    showChangesBrowser(changelists, null);
  }

  public void showChangesBrowser(List<CommittedChangeList> changelists, @Nls String title) {
    showChangesBrowser(new CommittedChangesTableModel(changelists), title, false);
  }

  private void showChangesBrowser(CommittedChangesTableModel changelists, String title, boolean showSearchAgain) {
    final ChangesBrowserDialog dlg = new ChangesBrowserDialog(myProject, changelists, showSearchAgain ? ChangesBrowserDialog.Mode.Browse : ChangesBrowserDialog.Mode.Simple);
    if (title != null) {
      dlg.setTitle(title);
    }
    dlg.show();
  }

  public void showChangesBrowser(CommittedChangeList changelist, @Nls String title) {
    final ChangeListViewerDialog dlg = new ChangeListViewerDialog(myProject, changelist);
    if (title != null) {
      dlg.setTitle(title);
    }
    dlg.show();
  }

  public void showChangesBrowser(final CommittedChangesProvider provider, final VirtualFile root, @Nls String title) {
    final ChangesBrowserSettingsEditor filterUI = provider.createFilterUI(true);
    ChangeBrowserSettings settings = provider.createDefaultSettings();
    boolean ok = true;
    if (filterUI != null) {
      final CommittedChangesFilterDialog dlg = new CommittedChangesFilterDialog(myProject, filterUI, settings);
      dlg.show();
      ok = dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE;
      settings = dlg.getSettings();
    }
    else {
      ok = true;
    }

    if (ok) {
      final List<CommittedChangeList> versions = new ArrayList<CommittedChangeList>();
      final List<VcsException> exceptions = new ArrayList<VcsException>();
      final Ref<CommittedChangesTableModel> tableModelRef = new Ref<CommittedChangesTableModel>();

      final ChangeBrowserSettings settings1 = settings;
      final boolean done = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          try {
            versions.addAll(provider.getCommittedChanges(settings1, root, 0));
          }
          catch (VcsException e) {
            exceptions.add(e);
          }
          tableModelRef.set(new CommittedChangesTableModel(versions, provider.getColumns()));
        }
      }, VcsBundle.message("browse.changes.progress.title"), true, myProject);

      if (!done) return;

      if (!exceptions.isEmpty()) {
        Messages.showErrorDialog(myProject, VcsBundle.message("browse.changes.error.message", exceptions.get(0).getMessage()),
                                 VcsBundle.message("browse.changes.error.title"));
        return;
      }

      if (versions.isEmpty()) {
        Messages.showInfoMessage(myProject, VcsBundle.message("browse.changes.nothing.found"),
                                 VcsBundle.message("browse.changes.nothing.found.title"));
        return;
      }

      showChangesBrowser(tableModelRef.get(), title, filterUI != null);
    }
  }

  @Nullable
  public <T extends CommittedChangeList, U extends ChangeBrowserSettings> T chooseCommittedChangeList(CommittedChangesProvider<T, U> provider) {
    final List<T> changes;
    try {
      changes = provider.getCommittedChanges(provider.createDefaultSettings(), myProject.getBaseDir(), 0);
    }
    catch (VcsException e) {
      return null;
    }
    final ChangesBrowserDialog dlg = new ChangesBrowserDialog(myProject,
                                                              new CommittedChangesTableModel((List<CommittedChangeList>)changes, provider.getColumns()),
                                                              ChangesBrowserDialog.Mode.Choose);
    dlg.show();
    if (dlg.isOK()) {
      return (T) dlg.getSelectedChangeList();
    }
    else {
      return null;
    }
  }

  public void showMergeDialog(List<VirtualFile> files, MergeProvider provider, final AnActionEvent e) {
    if (files.isEmpty()) return;
    new MultipleFileMergeDialog(myProject, files, provider).show();
    //new AbstractMergeAction(myProject, files, provider).actionPerformed(e);
  }

  public List<CodeSmellInfo> findCodeSmells(final List<VirtualFile> filesToCheck) throws ProcessCanceledException {
    final List<CodeSmellInfo> result = new ArrayList<CodeSmellInfo>();
    final PsiManager manager = PsiManager.getInstance(myProject);
    final FileDocumentManager fileManager = FileDocumentManager.getInstance();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        @Nullable final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        for (int i = 0; i < filesToCheck.size(); i++) {

          if (progress != null && progress.isCanceled()) throw new ProcessCanceledException();

          VirtualFile file = filesToCheck.get(i);

          if (progress != null) {
            progress.setText(VcsBundle.message("searching.for.code.smells.processing.file.progress.text", file.getPresentableUrl()));
            progress.setFraction((double)i / (double)filesToCheck.size());
          }

          final PsiFile psiFile = manager.findFile(file);
          if (psiFile != null) {
            final Document document = fileManager.getDocument(file);
            if (document != null) {
              final List<CodeSmellInfo> codeSmells = findCodeSmells(psiFile, progress, document);
              result.addAll(codeSmells);
            }
          }
        }
      }
    }, VcsBundle.message("checking.code.smells.progress.title"), true, myProject);

    if (!completed) throw new ProcessCanceledException();

    return result;
  }

  private List<CodeSmellInfo> findCodeSmells(final PsiFile psiFile, final ProgressIndicator progress, final Document document) {

    final List<CodeSmellInfo> result = new ArrayList<CodeSmellInfo>();

    GeneralHighlightingPass action1 = new GeneralHighlightingPass(myProject, psiFile, document, 0, psiFile.getTextLength(), true);
    action1.doCollectInformation(progress);

    collectErrorsAndWarnings(action1.getHighlights(), result, document);

    PostHighlightingPass action2 = new PostHighlightingPass(myProject, psiFile, document, 0, psiFile.getTextLength());
    action2.doCollectInformation(progress);

    collectErrorsAndWarnings(action2.getHighlights(), result, document);

    LocalInspectionsPass action3 = new LocalInspectionsPass(psiFile, document, 0, psiFile.getTextLength(),null);
    action3.doCollectInformation(progress);

    collectErrorsAndWarnings(action3.getHighlights(), result, document);

    return result;

  }

  private static void collectErrorsAndWarnings(final Collection<HighlightInfo> highlights,
                                               final List<CodeSmellInfo> result,
                                               final Document document) {
    for (HighlightInfo highlightInfo : highlights) {
      final HighlightSeverity severity = highlightInfo.getSeverity();
      if (severity.compareTo(HighlightSeverity.WARNING) >= 0) {
        result.add(new CodeSmellInfo(document, getDescription(highlightInfo),
                                     new TextRange(highlightInfo.startOffset, highlightInfo.endOffset), severity));
      }
    }
  }

  private static String getDescription(final HighlightInfo highlightInfo) {
    final String description = highlightInfo.description;
    final HighlightInfoType type = highlightInfo.type;
    if (type instanceof HighlightInfoType.HighlightInfoTypeSeverityByKey) {
      final HighlightDisplayKey severityKey = ((HighlightInfoType.HighlightInfoTypeSeverityByKey)type).getSeverityKey();
      final String id = severityKey.getID();
      if (id != null) {
        return "[" + id + "] " + description;
      }
    }
    return description;
  }

  private static DiffContent getContentForVersion(final VcsFileRevision version, final File file) throws IOException {
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (vFile != null && (version instanceof CurrentRevision) && !vFile.getFileType().isBinary()) {
      return new DocumentContent(FileDocumentManager.getInstance().getDocument(vFile), vFile.getFileType());
    }
    else {
      return new SimpleContent(new String(version.getContent()), FileTypeManager.getInstance().getFileTypeByFileName(file.getName()));
    }
  }

  private static boolean reformat(final VcsConfiguration configuration, boolean checkinProject) {
    return checkinProject ? configuration.REFORMAT_BEFORE_PROJECT_COMMIT : configuration.REFORMAT_BEFORE_FILE_COMMIT;
  }

  private PsiFile[] getPsiFiles(Collection<VirtualFile> selectedFiles) {
    ArrayList<PsiFile> result = new ArrayList<PsiFile>();
    PsiManager psiManager = PsiManager.getInstance(myProject);

    for (VirtualFile file : selectedFiles) {
      if (file.isValid()) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) result.add(psiFile);
      }
    }
    return result.toArray(new PsiFile[result.size()]);
  }

  private static class MyContentDisposer implements ContentManagerListener {
    private final Content myContent;
    private final MessageView myMessageView;

    public MyContentDisposer(final Content content, final MessageView messageView) {
      myContent = content;
      myMessageView = messageView;
    }

    public void contentRemoved(ContentManagerEvent event) {
      final Content eventContent = event.getContent();
      if (!eventContent.equals(myContent)) {
        return;
      }
      myMessageView.removeContentManagerListener(this);
      NewErrorTreeViewPanel errorTreeView = eventContent.getUserData(KEY);
      if (errorTreeView != null) {
        errorTreeView.dispose();
      }
      eventContent.putUserData(KEY, null);
    }

    public void contentAdded(ContentManagerEvent event) {
    }

    public void contentRemoveQuery(ContentManagerEvent event) {
    }

    public void selectionChanged(ContentManagerEvent event) {
    }
  }
}
