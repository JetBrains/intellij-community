package com.intellij.openapi.vcs.impl;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.impl.FrameWrapper;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.localVcs.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.Annotater;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.VcsOperation;
import com.intellij.openapi.vcs.history.CurrentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.impl.checkin.CheckinHandler;
import com.intellij.openapi.vcs.merge.AbstractMergeAction;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.impl.CheckinProjectPanelImpl;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.ShowRevisionChangesAction;
import com.intellij.openapi.vcs.versionBrowser.VersionsProvider;
import com.intellij.openapi.vcs.versions.AbstractRevisions;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.content.MessageView;
import com.intellij.util.ImageLoader;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import com.intellij.util.ui.treetable.TreeTable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class AbstractVcsHelperImpl extends AbstractVcsHelper implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl");

  private Project myProject;

  public AbstractVcsHelperImpl(Project project) {
    myProject = project;
  }

  private static final Key KEY = Key.create("AbstractVcsHelper.KEY");

  public void showCodeSmellErrors(final List<CodeSmellInfo> smellList) {

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        if (smellList.isEmpty()) {
          return;
        }

        final NewErrorTreeViewPanel errorTreeView = new NewErrorTreeViewPanel(myProject, null){
          protected boolean canHideWarnings() {
            return false;
          }
        };
        CommandProcessor commandProcessor = CommandProcessor.getInstance();
        commandProcessor.executeCommand(myProject, new Runnable() {
          public void run() {
            final MessageView messageView = myProject.getComponent(MessageView.class);
            final String tabDisplayName = VcsBundle.message("code.smells.error.messages.tab.name");
            final Content content = PeerFactory.getInstance().getContentFactory().createContent(errorTreeView.getComponent(), tabDisplayName, true);
            content.putUserData(KEY, errorTreeView);
            messageView.addContent(content);
            messageView.setSelectedContent(content);
            removeContents(content, tabDisplayName);
            messageView.addContentManagerListener(new MyContentDisposer(content, messageView));
          }
        },
                                        VcsBundle.message("command.name.open.error.message.view"),
                                        null);

        FileDocumentManager fileManager = FileDocumentManager.getInstance();


        for (Iterator<CodeSmellInfo> iterator = smellList.iterator(); iterator.hasNext();) {
          CodeSmellInfo smellInfo = iterator.next();
          VirtualFile file = fileManager.getFile(smellInfo.getDocument());
          if (smellInfo.getSeverity() == HighlightSeverity.ERROR) {
            errorTreeView.addMessage(MessageCategory.ERROR, new String[]{smellInfo.getDescription()}, file, smellInfo.getStartLine(),
                                     smellInfo.getStartColumn(), null);
          } else if (smellInfo.getSeverity() == HighlightSeverity.WARNING) {
            errorTreeView.addMessage(MessageCategory.WARNING, new String[]{smellInfo.getDescription()}, file, smellInfo.getStartLine(),
                                     smellInfo.getStartColumn(), null);
          }

        }

        ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
      }

    });

  }

  public void showErrors(final List abstractVcsExceptions, final String tabDisplayName) {
    LOG.assertTrue(tabDisplayName != null, "tabDisplayName should not be null");
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        if (abstractVcsExceptions.isEmpty()) {
          removeContents(null, tabDisplayName);
          return;
        }

        final NewErrorTreeViewPanel errorTreeView = new NewErrorTreeViewPanel(myProject, null){
          protected boolean canHideWarnings() {
            return false;
          }
        };
        CommandProcessor commandProcessor = CommandProcessor.getInstance();
        commandProcessor.executeCommand(myProject, new Runnable() {
          public void run() {
            final MessageView messageView = myProject.getComponent(MessageView.class);
            final Content content = PeerFactory.getInstance().getContentFactory().createContent(errorTreeView.getComponent(), tabDisplayName, true);
            content.putUserData(KEY, errorTreeView);
            messageView.addContent(content);
            messageView.setSelectedContent(content);
            removeContents(content, tabDisplayName);
            messageView.addContentManagerListener(new MyContentDisposer(content, messageView));
          }
        },
                                        VcsBundle.message("command.name.open.error.message.view"),
                                        null);

        for (Iterator i = abstractVcsExceptions.iterator(); i.hasNext();) {
          VcsException exception = (VcsException)i.next();
          String[] messages = exception.getMessages();
          if (messages.length == 0) messages = new String[]{VcsBundle.message("exception.text.unknown.error")};
          errorTreeView.addMessage(getErrorCategory(exception), messages, exception.getVirtualFile(), -1, -1, null);
        }

        ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
      }
    });
  }

  private int getErrorCategory(VcsException exception) {
    if (exception.isWarning()) {
      return MessageCategory.WARNING;
    } else {
      return MessageCategory.ERROR;
    }
  }

  protected void removeContents(Content notToRemove, final String tabDisplayName) {
    MessageView messageView = myProject.getComponent(MessageView.class);
    Content[] contents = messageView.getContents();
    for (int i = 0; i < contents.length; i++) {
      Content content = contents[i];
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
    List exceptions = new ArrayList();

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

  public Refreshable createCheckinProjectPanel(Project project) {
    return new CheckinProjectPanelImpl(project,
                                       Arrays.asList(LocalVcs.getInstance(myProject).getRootPaths()), new JPanel());
  }

  public void prepareFileForCheckin(final VirtualFile file) {
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      final PsiImportList[] resultList = new PsiImportList[1];
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      final boolean[] writable = new boolean[1];
      writable[0] = true;
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          if (!file.isWritable()) {
            writable[0] = false;
            return;
          }
          final PsiFile psiFile = psiManager.findFile(file);
          if (psiFile instanceof PsiJavaFile) {
            resultList[0] = CodeStyleManager.getInstance(myProject).prepareOptimizeImportsResult(psiFile);
          }
        }
      });

      if (!writable[0]) return;

      if (resultList[0] != null) {
        Runnable runnable = new Runnable() {
          public void run() {
            CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  public void run() {
                    try {
                      final PsiFile psiFile = psiManager.findFile(file);
                      ((PsiJavaFile)psiFile).getImportList().replace(resultList[0]);
                      final Document document = FileDocumentManager.getInstance().getDocument(file);
                      if (document != null) {
                        FileDocumentManager.getInstance().saveDocument(document);
                      }
                    }
                    catch (IncorrectOperationException e) {
                      LOG.error(e);
                    }
                  }
                });
              }
            },
                                                          VcsBundle.message("process.title.optimize.imports"),
                                                          null);
          }
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
          runnable.run();
        }
        else {
          ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.NON_MMODAL);
        }
      }
    }
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "AbstractVcsHelper";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public List<VcsException> doCheckinProject(final CheckinProjectPanel checkinProjectPanel,
                                             final Object checkinParameters, final AbstractVcs abstractVcs) {

    final ArrayList<VcsException> exceptions = new ArrayList<VcsException>();

    performCheckingIn(checkinProjectPanel, abstractVcs, checkinParameters, exceptions);

    return exceptions;
  }

  private void performCheckingIn(final CheckinProjectPanel checkinProjectPanel,
                                 final AbstractVcs abstractVcs,
                                 final Object checkinParameters,
                                 final ArrayList<VcsException> exceptions) {
    Collection<VirtualFile> roots = (checkinProjectPanel).getRoots();
    Collection<VirtualFile> correspondingRoots = new ArrayList<VirtualFile>();
    for (Iterator<VirtualFile> iterator = roots.iterator(); iterator.hasNext();) {
      VirtualFile virtualFile = iterator.next();
      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
      CheckinEnvironment env = vcs.getCheckinEnvironment();
      if (env == abstractVcs.getCheckinEnvironment()) correspondingRoots.add(virtualFile);
    }

    final Map<CheckinEnvironment, List<VcsOperation>> checkinOperations = ((CheckinProjectPanelImpl)checkinProjectPanel).getCheckinOperations();

    final CheckinHandler checkinHandler = new CheckinHandler(myProject,
                                                             abstractVcs);
    Collection<VcsOperation> vcsOperations = checkinOperations.get(abstractVcs.getCheckinEnvironment());
    if (vcsOperations == null) vcsOperations = new ArrayList<VcsOperation>();
    final List<VcsException> abstractVcsExceptions =
      checkinHandler.checkin(vcsOperations.toArray(new VcsOperation[vcsOperations.size()]),
                             checkinParameters);
    exceptions.addAll(abstractVcsExceptions);
  }

  public void doCheckinFiles(VirtualFile[] files, Object checkinParameters) {

    Map<AbstractVcs, List<VirtualFile>> vcsToFileMap = new HashMap<AbstractVcs, List<VirtualFile>>();

    for (int i = 0; i < files.length; i++) {
      VirtualFile file = files[i];
      AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
      if (activeVcs == null) continue;
      if (!vcsToFileMap.containsKey(activeVcs)) vcsToFileMap.put(activeVcs, new ArrayList<VirtualFile>());
      vcsToFileMap.get(activeVcs).add(file);
    }

    for (Iterator<AbstractVcs> iterator = vcsToFileMap.keySet().iterator(); iterator.hasNext();) {
      AbstractVcs abstractVcs = iterator.next();
      doCheckinFiles(abstractVcs, vcsToFileMap.get(abstractVcs), checkinParameters);
    }
  }

  private void doCheckinFiles(AbstractVcs abstractVcs, List<VirtualFile> virtualFiles, Object checkinParameters) {
    final LocalVcs lvcs = LocalVcs.getInstance(myProject);

    List objects = new ArrayList();
    for (Iterator<VirtualFile> iterator = virtualFiles.iterator(); iterator.hasNext();) {
      VirtualFile file = iterator.next();
      LvcsFile lvcsFile = lvcs.findFile(file.getPath());
      if (lvcsFile != null) {
        objects.add(lvcsFile);
      }
    }

    if (objects.isEmpty()) return;

    final CheckinHandler checkinHandler = new CheckinHandler(myProject, abstractVcs);
    List exceptions = checkinHandler.checkin((LvcsObject[])objects.toArray(new LvcsObject[objects.size()]),
                                             checkinParameters);
    showErrors(exceptions, VcsBundle.message("message.title.check.in"));


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

    if (optimizeImports(configuration, checkinProject)) {
      new OptimizeImportsProcessor(myProject, getPsiFiles(files), reformatCodeAndPerformCheckout).run();
    }
    else {
      reformatCodeAndPerformCheckout.run();
    }

  }

  public CheckinProjectDialogImplementer createCheckinProjectDialog(String title,
                                                                    boolean requestComments,
                                                                    Collection<String> roots) {
    return new CheckinProjectDialogImplementerImpl(myProject, title, requestComments, roots);
  }

  public void showAnnotation(FileAnnotation annotation, VirtualFile file) {
    new Annotater(annotation, myProject, file).showAnnotation();
  }

  public void showDifferences(final VcsFileRevision version1,
                              final VcsFileRevision version2,
                              final File file) {
    try {
      version1.loadContent();
      version2.loadContent();

      if (Comparing.equal(version1.getContent(), version2.getContent())) {
        Messages.showInfoMessage(VcsBundle.message("message.text.versions.are.identical"), VcsBundle.message("message.title.diff"));
      }

      final SimpleDiffRequest request =
        new SimpleDiffRequest(myProject, file.getAbsolutePath());

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
      } else {
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

  public void showChangesBrowser(VersionsProvider versionsProvider) {
    new ChangesBrowser(myProject, versionsProvider).show();
  }

  public void showRevisions(List<AbstractRevisions> revisions, final String title) {
    showRevisions(revisions, title, null, null);
  }

  public void showRevisions(List<AbstractRevisions> revisions, final String title, String commitMessage, String commitMessageTitle) {
    final TreeTable directoryDiffTree = PeerFactory.getInstance().getUIHelper()
        .createDirectoryDiffTree(myProject, revisions.toArray(new AbstractRevisions[revisions.size()]));
    new ShowRevisionChangesAction(myProject).registerCustomShortcutSet(CommonShortcuts.DOUBLE_CLICK_1, directoryDiffTree);

    FrameWrapper frameWrapper = new FrameWrapper("vcs.showRevisions");
    frameWrapper.setTitle(title);
    frameWrapper.setComponent(createChangeBrowsePanel(directoryDiffTree, commitMessage, commitMessageTitle));
    frameWrapper.setData(DataConstants.PROJECT, myProject);
    frameWrapper.setImage(ImageLoader.loadFromResource("/diff/Diff.png"));
    frameWrapper.closeOnEsc();
    frameWrapper.show();
  }

  private JComponent createChangeBrowsePanel(final TreeTable directoryDiffTree,
                                             final String commitMessage,
                                             final String commitMessageTitle) {
    if (commitMessage == null || commitMessageTitle == null) {
      return new JScrollPane(directoryDiffTree);
    } else {
      final JPanel result = new JPanel(new BorderLayout());
      result.add(new JScrollPane(directoryDiffTree), BorderLayout.CENTER);
      final JTextArea textArea = new JTextArea(commitMessage);
      textArea.setEditable(false);
      textArea.setLineWrap(true);
      textArea.setWrapStyleWord(true);
      textArea.setColumns(5);
      final JScrollPane textAreaScrollPane = new JScrollPane(textArea);
      textAreaScrollPane.setBorder(BorderFactory.createTitledBorder(commitMessageTitle));
      result.add(textAreaScrollPane, BorderLayout.SOUTH);
      return result;
    }
  }

  public void showMergeDialog(List<VirtualFile> files, MergeProvider provider, final AnActionEvent e) {
    if (files.isEmpty()) return;
    new AbstractMergeAction(myProject,
                            files,
                            provider).actionPerformed(e);
  }

  public List<CodeSmellInfo> findCodeSmells(final List<VirtualFile> filesToCheck) throws ProcessCanceledException {
    final List<CodeSmellInfo> result = new ArrayList<CodeSmellInfo>();
    final PsiManager manager = PsiManager.getInstance(myProject);
    final FileDocumentManager fileManager = FileDocumentManager.getInstance();

    boolean completed = ApplicationManager.getApplication().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        for (int i  = 0; i < filesToCheck.size(); i++) {

          if (progress.isCanceled()) throw new ProcessCanceledException();

          VirtualFile file = filesToCheck.get(i);

          progress.setText(VcsBundle.message("searching.for.code.smells.processing.file.progress.text", file.getPresentableUrl()));
          progress.setFraction((double)i/(double)filesToCheck.size());

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

    GeneralHighlightingPass action1 = new GeneralHighlightingPass(myProject, psiFile, document, 0, psiFile.getTextLength(), false, true);
    action1.doCollectInformation(progress);

    collectErrorsAndWarnings(action1.getHighlights(), result, document);

    PostHighlightingPass action2 = new PostHighlightingPass(myProject, psiFile, document, 0, psiFile.getTextLength(), false);
    action2.doCollectInformation(progress);

    collectErrorsAndWarnings(action2.getHighlights(), result, document);

    LocalInspectionsPass action3 = new LocalInspectionsPass(myProject, psiFile, document, 0, psiFile.getTextLength());
    action3.doCollectInformation(progress);

    collectErrorsAndWarnings(action3.getHighlights(), result, document);

    return result;

  }

  private void collectErrorsAndWarnings(final Collection<HighlightInfo> highlights,
                                        final List<CodeSmellInfo> result,
                                        final Document document) {
    for (Iterator<HighlightInfo> iterator = highlights.iterator(); iterator.hasNext();) {
      HighlightInfo highlightInfo = iterator.next();
      final HighlightSeverity severity = highlightInfo.getSeverity();
      String description = highlightInfo.description;
      if (severity == HighlightSeverity.ERROR || severity == HighlightSeverity.WARNING) {
        final HighlightInfoType type = highlightInfo.type;
        if (type instanceof HighlightInfoType.HighlightInfoTypeSeverityByKey) {
          final HighlightDisplayKey severityKey = ((HighlightInfoType.HighlightInfoTypeSeverityByKey)type).getSeverityKey();
          final String id = severityKey.getID();
          if (id != null) {
            description = "[" + id + "] " + description;
          }
        } else {
        }
        result.add(new CodeSmellInfo(document, description, new TextRange(highlightInfo.startOffset, highlightInfo.endOffset),
                                     severity));
      }
    }
  }

  private DiffContent getContentForVersion(final VcsFileRevision version, final File file) throws IOException {
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (vFile != null && (version instanceof CurrentRevision) && !vFile.getFileType().isBinary()) {
      return new DocumentContent(FileDocumentManager.getInstance().getDocument(vFile), vFile.getFileType());
    } else {
      return new SimpleContent(new String(version.getContent()), FileTypeManager.getInstance().getFileTypeByFileName(file.getName()));
    }
  }

  private boolean reformat(final VcsConfiguration configuration, boolean checkinProject) {
    return checkinProject ? configuration.REFORMAT_BEFORE_PROJECT_COMMIT :
           configuration.REFORMAT_BEFORE_FILE_COMMIT;
  }

  private boolean optimizeImports(final VcsConfiguration configuration, boolean checkinProject) {
    return checkinProject ? configuration.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT :
           configuration.OPTIMIZE_IMPORTS_BEFORE_FILE_COMMIT;
  }

  private PsiFile[] getPsiFiles(Collection<VirtualFile> selectedFiles) {
    ArrayList<PsiFile> result = new ArrayList<PsiFile>();
    PsiManager psiManager = PsiManager.getInstance(myProject);

    for (Iterator<VirtualFile> each = selectedFiles.iterator(); each.hasNext();) {
      VirtualFile file = each.next();
      if (file.isValid()) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) result.add(psiFile);
      }
    }
    return result.toArray(new PsiFile[result.size()]);
  }

  public List<VcsException> doCheckinFiles(AbstractVcs vcs, FilePath[] roots, String preparedComment) {
    final LocalVcs lvcs = LocalVcs.getInstance(myProject);

    List objects = new ArrayList();

    for (int i = 0; i < roots.length; i++) {
      FilePath file = roots[i];
      LvcsFile lvcsFile = lvcs.findFile(file.getPath());
      if (lvcsFile != null) {
        objects.add(lvcsFile);
      }

    }

    if (objects.isEmpty()) return new ArrayList<VcsException>();

    final CheckinHandler checkinHandler = new CheckinHandler(myProject, vcs);
    return checkinHandler.checkin((LvcsObject[])objects.toArray(new LvcsObject[objects.size()]),
                                  preparedComment);

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
      NewErrorTreeViewPanel errorTreeView = (NewErrorTreeViewPanel)eventContent.getUserData(KEY);
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
