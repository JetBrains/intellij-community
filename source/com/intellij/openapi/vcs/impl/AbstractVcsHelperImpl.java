package com.intellij.openapi.vcs.impl;

import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.Annotater;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.ChangesBrowserDialog;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesFilterDialog;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesPanel;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTableModel;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.history.*;
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
import com.intellij.openapi.wm.WindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.MessageView;
import com.intellij.util.ContentsUtil;
import com.intellij.util.ui.ConfirmationDialog;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

public class AbstractVcsHelperImpl extends AbstractVcsHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl");

  private Project myProject;

  public AbstractVcsHelperImpl(Project project) {
    myProject = project;
  }

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

        final VcsErrorViewPanel errorTreeView = new VcsErrorViewPanel(myProject);
        openMessagesView(errorTreeView, VcsBundle.message("code.smells.error.messages.tab.name"));

        FileDocumentManager fileManager = FileDocumentManager.getInstance();

        for (CodeSmellInfo smellInfo : smellList) {
          final VirtualFile file = fileManager.getFile(smellInfo.getDocument());
          final OpenFileDescriptor navigatable =
            new OpenFileDescriptor(myProject, file, smellInfo.getStartLine(), smellInfo.getStartColumn());
          final String exportPrefix = NewErrorTreeViewPanel.createExportPrefix(smellInfo.getStartLine() + 1);
          final String rendererPrefix =
            NewErrorTreeViewPanel.createRendererPrefix(smellInfo.getStartLine() + 1, smellInfo.getStartColumn() + 1);
          if (smellInfo.getSeverity() == HighlightSeverity.ERROR) {
            errorTreeView.addMessage(MessageCategory.ERROR, new String[]{smellInfo.getDescription()}, file.getPresentableUrl(), navigatable,
                                     exportPrefix, rendererPrefix, null);
          }
          else {//if (smellInfo.getSeverity() == HighlightSeverity.WARNING) {
            errorTreeView.addMessage(MessageCategory.WARNING, new String[]{smellInfo.getDescription()}, file.getPresentableUrl(),
                                     navigatable, exportPrefix, rendererPrefix, null);
          }

        }

        ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
      }

    });

  }

  private void openMessagesView(final VcsErrorViewPanel errorTreeView, final String tabDisplayName) {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, new Runnable() {
      public void run() {
        final MessageView messageView = myProject.getComponent(MessageView.class);
        final Content content =
          PeerFactory.getInstance().getContentFactory().createContent(errorTreeView, tabDisplayName, true);
        messageView.getContentManager().addContent(content);
        Disposer.register(content, errorTreeView);
        messageView.getContentManager().setSelectedContent(content);
        removeContents(content, tabDisplayName);
      }
    }, VcsBundle.message("command.name.open.error.message.view"), null);
  }

  public void showFileHistory(final VcsHistoryProvider vcsHistoryProvider, final FilePath path) {
    showFileHistory(vcsHistoryProvider, null, path);
  }

  public void showFileHistory(VcsHistoryProvider vcsHistoryProvider, AnnotationProvider annotationProvider, FilePath path) {
    try {
      VcsHistorySession session = vcsHistoryProvider.createSessionFor(path);
      if (session == null) return;
      List<VcsFileRevision> revisionsList = session.getRevisionList();
      if (revisionsList.isEmpty()) return;

      String actionName = VcsBundle.message("action.name.file.history", path.getName());

      ContentManager contentManager = ProjectLevelVcsManagerEx.getInstanceEx(myProject).getContentManager();

      FileHistoryPanelImpl fileHistoryPanel =
        new FileHistoryPanelImpl(myProject, path, session, vcsHistoryProvider, annotationProvider, contentManager);
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
  public Collection<VirtualFile> selectFilesToProcess(final List<VirtualFile> files,
                                                      final String title,
                                                      @Nullable final String prompt,
                                                      final String singleFileTitle,
                                                      final String singleFilePromptTemplate,
                                                      final VcsShowConfirmationOption confirmationOption) {
    if (files.size() == 1 && singleFilePromptTemplate != null) {
      String filePrompt = MessageFormat.format(singleFilePromptTemplate, files.get(0).getPresentableUrl());
      if (ConfirmationDialog
        .requestForConfirmation(confirmationOption, myProject, filePrompt, singleFileTitle, Messages.getQuestionIcon())) {
        return files;
      }
      return null;
    }

    SelectFilesDialog dlg = new SelectFilesDialog(myProject, files, prompt, confirmationOption);
    dlg.setTitle(title);
    dlg.show();
    if (dlg.isOK()) {
      final Collection<VirtualFile> selection = dlg.getSelectedFiles();
      // return items in the same order as they were passed to us
      final List<VirtualFile> result = new ArrayList<VirtualFile>();
      for(VirtualFile file: files) {
        if (selection.contains(file)) {
          result.add(file);
        }
      }
      return result;
    }
    return null;
  }

  @Nullable
  public Collection<FilePath> selectFilePathsToProcess(final List<FilePath> files,
                                                       final String title,
                                                       @Nullable final String prompt,
                                                       final String singleFileTitle,
                                                       final String singleFilePromptTemplate,
                                                       final VcsShowConfirmationOption confirmationOption) {
    if (files.size() == 1 && singleFilePromptTemplate != null) {
      String filePrompt = MessageFormat.format(singleFilePromptTemplate, files.get(0).getPresentableUrl());
      if (ConfirmationDialog
        .requestForConfirmation(confirmationOption, myProject, filePrompt, singleFileTitle, Messages.getQuestionIcon())) {
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
    Messages.showMessageDialog(exception.getLocalizedMessage(), VcsBundle.message("message.title.could.not.load.file.history"),
                               Messages.getErrorIcon());
  }

  public void showErrors(final List<VcsException> abstractVcsExceptions, @NotNull final String tabDisplayName) {
    if (ApplicationManager.getApplication().isUnitTestMode() && !abstractVcsExceptions.isEmpty()) {
      throw new RuntimeException(abstractVcsExceptions.get(0));
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        if (abstractVcsExceptions.isEmpty()) {
          removeContents(null, tabDisplayName);
          return;
        }

        final VcsErrorViewPanel errorTreeView = new VcsErrorViewPanel(myProject);
        openMessagesView(errorTreeView, tabDisplayName);

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
    Content[] contents = messageView.getContentManager().getContents();
    for (Content content : contents) {
      LOG.assertTrue(content != null);
      if (content.isPinned()) continue;
      if (tabDisplayName.equals(content.getDisplayName()) && content != notToRemove) {
        ErrorTreeView listErrorView = (ErrorTreeView)content.getComponent();
        if (listErrorView != null) {
          if (messageView.getContentManager().removeContent(content, true)) {
            content.release();
          }
        }
      }
    }
  }

  public LocalHistoryAction startLocalHistoryAction(String actionName) {
    return LocalHistory.startAction(myProject, actionName);
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
    showChangesBrowser(new CommittedChangesTableModel(changelists), title, false, null);
  }

  private void showChangesBrowser(CommittedChangesTableModel changelists,
                                  String title,
                                  boolean showSearchAgain,
                                  @Nullable final Component parent) {
    final ChangesBrowserDialog.Mode mode = showSearchAgain ? ChangesBrowserDialog.Mode.Browse : ChangesBrowserDialog.Mode.Simple;
    final ChangesBrowserDialog dlg = parent != null
                                     ? new ChangesBrowserDialog(myProject, parent, changelists, mode)
                                     : new ChangesBrowserDialog(myProject, changelists, mode);
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

  public void showChangesBrowser(final Component parent, final Collection<Change> changes, @Nls final String title) {
    final ChangeListViewerDialog dlg;
    if (parent != null) {
      dlg = new ChangeListViewerDialog(parent, myProject, changes);
    }
    else {
      dlg = new ChangeListViewerDialog(myProject, changes);
    }
    if (title != null) {
      dlg.setTitle(title);
    }
    dlg.show();
  }

  public void showChangesBrowser(final CommittedChangesProvider provider,
                                 final RepositoryLocation location,
                                 @Nls String title,
                                 Component parent) {
    final ChangesBrowserSettingsEditor filterUI = provider.createFilterUI(true);
    ChangeBrowserSettings settings = provider.createDefaultSettings();
    boolean ok;
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
      if (myProject.isDefault() || ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss().length == 0) {
        final List<CommittedChangeList> versions = new ArrayList<CommittedChangeList>();
        final List<VcsException> exceptions = new ArrayList<VcsException>();
        final Ref<CommittedChangesTableModel> tableModelRef = new Ref<CommittedChangesTableModel>();

        final ChangeBrowserSettings settings1 = settings;
        final boolean done = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
          public void run() {
            try {
              versions.addAll(provider.getCommittedChanges(settings1, location, 0));
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

        if (parent == null || !parent.isValid()) {
          parent = WindowManager.getInstance().suggestParentWindow(myProject);
        }
        showChangesBrowser(tableModelRef.get(), title, filterUI != null, parent);
      }
      else {
        openCommittedChangesTab(provider, location, settings, 0, title);
      }
    }
  }

  @Nullable
  public <T extends CommittedChangeList, U extends ChangeBrowserSettings> T chooseCommittedChangeList(CommittedChangesProvider<T, U> provider,
                                                                                                      RepositoryLocation location) {
    final List<T> changes;
    try {
      changes = provider.getCommittedChanges(provider.createDefaultSettings(), location, 0);
    }
    catch (VcsException e) {
      return null;
    }
    final ChangesBrowserDialog dlg = new ChangesBrowserDialog(myProject, new CommittedChangesTableModel((List<CommittedChangeList>)changes,
                                                                                                        provider.getColumns()),
                                                                         ChangesBrowserDialog.Mode.Choose);
    dlg.show();
    if (dlg.isOK()) {
      return (T)dlg.getSelectedChangeList();
    }
    else {
      return null;
    }
  }

  @NotNull
  public List<VirtualFile> showMergeDialog(List<VirtualFile> files, MergeProvider provider) {
    if (files.isEmpty()) return Collections.emptyList();
    final MultipleFileMergeDialog fileMergeDialog = new MultipleFileMergeDialog(myProject, files, provider);
    fileMergeDialog.show();
    return fileMergeDialog.getProcessedFiles();
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

    LocalInspectionsPass action3 = new LocalInspectionsPass(psiFile, document, 0, psiFile.getTextLength());
    action3.doCollectInformation(progress);

    collectErrorsAndWarnings(action3.getHighlights(), result, document);

    return result;

  }

  private void collectErrorsAndWarnings(final Collection<HighlightInfo> highlights,
                                               final List<CodeSmellInfo> result,
                                               final Document document) {
    if (highlights == null) return;
    for (HighlightInfo highlightInfo : highlights) {
      final HighlightSeverity severity = highlightInfo.getSeverity();
      if (SeverityRegistrar.getInstance(myProject).compare(severity, HighlightSeverity.WARNING) >= 0) {
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

  public void openCommittedChangesTab(final CommittedChangesProvider provider,
                                      final VirtualFile root,
                                      final ChangeBrowserSettings settings,
                                      final int maxCount,
                                      String title) {
    final RepositoryLocation location = provider.getLocationFor(new FilePathImpl(root));
    openCommittedChangesTab(provider, location, settings, maxCount, title);
  }

  public void openCommittedChangesTab(final CommittedChangesProvider provider,
                                      final RepositoryLocation location,
                                      final ChangeBrowserSettings settings,
                                      final int maxCount,
                                      String title) {
    DefaultActionGroup extraActions = new DefaultActionGroup();
    CommittedChangesPanel panel = new CommittedChangesPanel(myProject, provider, settings, location, extraActions);
    panel.setMaxCount(maxCount);
    panel.refreshChanges(false);
    final ContentFactory factory = PeerFactory.getInstance().getContentFactory();
    if (title == null && location != null) {
      title = VcsBundle.message("browse.changes.content.title", location.toPresentableString());
    }
    final Content content = factory.createContent(panel, title, false);
    final ChangesViewContentManager contentManager = ChangesViewContentManager.getInstance(myProject);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);

    extraActions.add(new CloseTabToolbarAction() {
      public void actionPerformed(final AnActionEvent e) {
        contentManager.removeContent(content);
      }
    });

    ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    if (!window.isVisible()) {
      window.activate(null);
    }
  }

  private static class VcsErrorViewPanel extends NewErrorTreeViewPanel {
    public VcsErrorViewPanel(Project project) {
      super(project, null);
    }

    protected boolean canHideWarnings() {
      return false;
    }
  }
}
