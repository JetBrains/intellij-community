package com.intellij.codeInsight.daemon.impl;

import com.intellij.ant.AntConfiguration;
import com.intellij.ant.AntConfigurationListener;
import com.intellij.ant.BuildFile;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.j2ee.extResources.ExternalResourceListener;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileTypeSupportCapabilities;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import gnu.trove.THashSet;
import org.jdom.Element;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

/**
 * This class also controls the auto-reparse and auto-hints.
 */
public class DaemonCodeAnalyzerImpl extends DaemonCodeAnalyzer implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl");

  private static final Key<HighlightInfo[]> HIGHLIGHTS_IN_EDITOR_DOCUMENT_KEY = Key.create("DaemonCodeAnalyzerImpl.HIGHLIGHTS_IN_EDITOR_DOCUMENT_KEY");
  private static final Key<LineMarkerInfo[]> MARKERS_IN_EDITOR_DOCUMENT_KEY = Key.create("DaemonCodeAnalyzerImpl.MARKERS_IN_EDITOR_DOCUMENT_KEY");

  private final Project myProject;
  private DaemonCodeAnalyzerSettings mySettings;
  private EditorTracker myEditorTracker;

  private final DaemonProgress myUpdateProgress = new DaemonProgress();

  private Semaphore myUpdateThreadSemaphore = new Semaphore();

  private Runnable myUpdateRunnable = createUpdateRunnable();

  private Alarm myAlarm = new Alarm();
  private boolean myUpdateByTimerEnabled = true;
  private Set<VirtualFile> myDisabledHintsFiles = new THashSet<VirtualFile>();
  private Set<PsiFile> myDisabledHighlightingFiles = new THashSet<PsiFile>();

  private FileStatusMap myFileStatusMap;

  private StatusBarUpdater myStatusBarUpdater;

  private DaemonCodeAnalyzerSettings myLastSettings;

  private MyCommandListener myCommandListener = new MyCommandListener();
  private MyApplicationListener myApplicationListener = new MyApplicationListener();
  private EditorColorsListener myEditorColorsListener = new MyEditorColorsListener();
  private AnActionListener myAnActionListener = new MyAnActionListener();
  private PropertyChangeListener myTodoListener = new MyTodoListener();
  private ExternalResourceListener myExternalResourceListener = new MyExternalResourceListener();
  private AntConfigurationListener myAntConfigurationListener = new MyAntConfigurationListener();
  private EditorMouseMotionListener myEditorMouseMotionListener = new MyEditorMouseMotionListener();

  private DocumentListener myDocumentListener;
  private CaretListener myCaretListener;
  private ErrorStripeHandler myErrorStripeHandler;
  //private long myUpdateStartTime;

  private boolean myEscPressed;
  private EditorFactoryListener myEditorFactoryListener;

  private boolean myShowPostIntentions = true;
  private IntentionHintComponent myLastIntentionHint;

  private boolean myDisposed;
  private boolean myInitialized;

  protected DaemonCodeAnalyzerImpl(Project project, DaemonCodeAnalyzerSettings daemonCodeAnalyzerSettings) {
    myProject = project;

    mySettings = daemonCodeAnalyzerSettings;
    myLastSettings = (DaemonCodeAnalyzerSettings)mySettings.clone();

    myFileStatusMap = new FileStatusMap(myProject);
  }

  public String getComponentName() {
    return "DaemonCodeAnalyzer";
  }

  public void initComponent() { }

  public void disposeComponent() {
    myFileStatusMap.markAllFilesDirty();
  }

  public EditorTracker getEditorTracker() {
    return myEditorTracker;
  }

  public void projectOpened() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    myDocumentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        stopProcess(true);
        UpdateHighlightersUtil.updateHighlightersByTyping(myProject, e);
      }
    };
    eventMulticaster.addDocumentListener(myDocumentListener);

    myCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        stopProcess(true);
      }
    };
    eventMulticaster.addCaretListener(myCaretListener);

    eventMulticaster.addEditorMouseMotionListener(myEditorMouseMotionListener);

    myEditorTracker = createEditorTracker();
    myEditorTracker.addEditorTrackerListener(new EditorTrackerListener() {
      public void activeEditorsChanged() {
        stopProcess(true);
      }
    });

    myEditorFactoryListener = new EditorFactoryAdapter() {
      public void editorCreated(EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Document document = editor.getDocument();
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (file != null) {
          ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeRenderer(
            new RefreshStatusRenderer(myProject, DaemonCodeAnalyzerImpl.this, document, file));
        }
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);

    PsiManager.getInstance(myProject).addPsiTreeChangeListener(new PsiChangeHandler(myProject, this));

    CommandProcessor.getInstance().addCommandListener(myCommandListener);
    ApplicationManager.getApplication().addApplicationListener(myApplicationListener);
    EditorColorsManager.getInstance().addEditorColorsListener(myEditorColorsListener);
    TodoConfiguration.getInstance().addPropertyChangeListener(myTodoListener);
    ActionManagerEx.getInstanceEx().addAnActionListener(myAnActionListener);
    ExternalResourceManagerEx.getInstanceEx().addExteralResourceListener(myExternalResourceListener);

    if (myProject.hasComponent(AntConfiguration.class)) {
      AntConfiguration.getInstance(myProject).addAntConfigurationListener(myAntConfigurationListener);
    }

    myStatusBarUpdater = new StatusBarUpdater(myProject);

    myErrorStripeHandler = new ErrorStripeHandler(myProject);
    ((EditorEventMulticasterEx)eventMulticaster).addErrorStripeListener(myErrorStripeHandler);

    ProjectManager.getInstance().addProjectManagerListener(
      myProject,
      new ProjectManagerAdapter() {
        public void projectClosing(Project project) {
          dispose();
        }
      }
    );

    myInitialized = true;
  }

  public void projectClosed() {
  }

  private void dispose() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.removeDocumentListener(myDocumentListener);
    eventMulticaster.removeCaretListener(myCaretListener);
    eventMulticaster.removeEditorMouseMotionListener(myEditorMouseMotionListener);

    EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
    CommandProcessor.getInstance().removeCommandListener(myCommandListener);
    ApplicationManager.getApplication().removeApplicationListener(myApplicationListener);
    EditorColorsManager.getInstance().removeEditorColorsListener(myEditorColorsListener);
    TodoConfiguration.getInstance().removePropertyChangeListener(myTodoListener);
    ActionManagerEx.getInstanceEx().removeAnActionListener(myAnActionListener);
    ExternalResourceManagerEx.getInstanceEx().removeExternalResourceListener(myExternalResourceListener);

    if (myProject.hasComponent(AntConfiguration.class)) {
      AntConfiguration.getInstance(myProject).removeAntConfigurationListener(myAntConfigurationListener);
    }

    myStatusBarUpdater.dispose();
    myEditorTracker.dispose();

    ((EditorEventMulticasterEx)eventMulticaster).removeErrorStripeListener(myErrorStripeHandler);
    // clear dangling references to PsiFiles/Documents. SCR#10358
    myFileStatusMap.markAllFilesDirty();

    myDisposed = true;

    stopProcess(false);
    myUpdateThreadSemaphore.waitFor();
  }

  public void settingsChanged() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    if (settings.isCodeHighlightingChanged(myLastSettings)) {
      restart();
    }
    myLastSettings = (DaemonCodeAnalyzerSettings)settings.clone();
  }

  public void updateVisibleHighlighters(Editor editor) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    setShowPostIntentions(false);

    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    BackgroundEditorHighlighter highlighter = textEditor.getBackgroundHighlighter();
    if (highlighter == null) return;
    updateHighlighters(textEditor, new LinkedHashSet<HighlightingPass>(Arrays.asList(highlighter.createPassesForVisibleArea())), null);
  }

  private void updateAll(FileEditor editor, Runnable postRunnable) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    if (LOG.isDebugEnabled()) {
      /* TODO:
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      LOG.debug("updateAll for " + file);
      */
    }
    //myUpdateStartTime = System.currentTimeMillis();
    //Statistics.clear();

    boolean editorHiddenByModelDialog = ModalityState.current().dominates(ModalityState.stateForComponent(editor.getComponent()));
    if (editorHiddenByModelDialog) {
      stopProcess(true);
      return;
    }

    BackgroundEditorHighlighter highlighter = editor.getBackgroundHighlighter();
    if (highlighter == null) return;
    updateHighlighters(editor, new LinkedHashSet<HighlightingPass>(Arrays.asList(highlighter.createPassesForEditor())), postRunnable);
  }

  public void setUpdateByTimerEnabled(boolean value) {
    myUpdateByTimerEnabled = value;
    stopProcess(true);
  }

  public void setImportHintsEnabled(PsiFile file, boolean value) {
    VirtualFile vFile = file.getVirtualFile();
    if (value) {
      myDisabledHintsFiles.remove(vFile);
      stopProcess(true);
    }
    else {
      myDisabledHintsFiles.add(vFile);
      HintManager.getInstance().hideAllHints();
    }
  }

  public void setHighlightingEnabled(PsiFile file, boolean value) {
    if (value) {
      myDisabledHighlightingFiles.remove(file);
    }
    else {
      myDisabledHighlightingFiles.add(file);
    }
  }

  public boolean isHighlightingAvailable(PsiFile file) {
    if (myDisabledHighlightingFiles.contains(file)) return false;

    boolean isHighlightingAvailable = file.canContainJavaCode() || file instanceof XmlFile;

    if (!isHighlightingAvailable) {
      final FileTypeSupportCapabilities supportCapabilities = file.getFileType().getSupportCapabilities();
      if (supportCapabilities!=null) isHighlightingAvailable = supportCapabilities.hasValidation();
    }

    return isHighlightingAvailable;
  }

  public boolean isImportHintsEnabled(PsiFile file) {
    if (!isAutohintsAvailable(file)) return false;
    return !myDisabledHintsFiles.contains(file.getVirtualFile());
  }

  public boolean isAutohintsAvailable(PsiFile file) {
    if (!isHighlightingAvailable(file)) return false;
    if (file instanceof PsiCompiledElement) return false;
    return true;
  }

  public void restart() {
    myFileStatusMap.markAllFilesDirty();
    stopProcess(true);
  }

  public boolean isErrorAnalyzingFinished(PsiFile file) {
    if (myDisposed) return false;
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    if (document == null) return false;
    if (document.getModificationStamp() != file.getModificationStamp()) return false;
    return myFileStatusMap.getFileDirtyScope(document, FileStatusMap.NORMAL_HIGHLIGHTERS) == null;
  }

  public boolean isInspectionCompleted(PsiFile file) {
    if (file instanceof PsiCompiledElement) return true;
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    if (document.getModificationStamp() != file.getModificationStamp()) return false;
    return myFileStatusMap.getFileDirtyScope(document, FileStatusMap.LOCAL_INSPECTIONS) == null;
  }

  public FileStatusMap getFileStatusMap() {
    return myFileStatusMap;
  }

  public DaemonProgress getUpdateProgress() {
    return myUpdateProgress;
  }

  public void stopProcess(boolean toRestartAlarm) {
    myAlarm.cancelAllRequests();
    if (toRestartAlarm && !myDisposed && myInitialized) {
      LOG.assertTrue(!ApplicationManager.getApplication().isUnitTestMode());
      myAlarm.addRequest(myUpdateRunnable, mySettings.AUTOREPARSE_DELAY);
    }
    myUpdateProgress.cancel();
  }

  public static HighlightInfo[] getHighlights(Document document, Project project) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    MarkupModel markup = document.getMarkupModel(project);
    if (markup == null) return null;
    return markup.getUserData(HIGHLIGHTS_IN_EDITOR_DOCUMENT_KEY);
  }

  public static HighlightInfo[] getHighlights(Document document, HighlightInfo.Severity minSeverity, Project project) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    HighlightInfo[] highlights = getHighlights(document, project);
    if (highlights == null) return null;
    ArrayList<HighlightInfo> array = new ArrayList<HighlightInfo>();
    for (int i = 0; i < highlights.length; i++) {
      HighlightInfo info = highlights[i];
      if (info.getSeverity().isGreaterOrEqual(minSeverity)) {
        array.add(info);
      }
    }
    return array.toArray(new HighlightInfo[array.size()]);
  }

  public HighlightInfo findHighlightByOffset(Document document, int offset, boolean includeFixRange) {
    HighlightInfo[] highlights = getHighlights(document, myProject);
    if (highlights == null) return null;

    List<HighlightInfo> foundInfoList = new SmartList<HighlightInfo>();
    for (int i = 0; i < highlights.length; i++) {
      HighlightInfo info = highlights[i];
      if (info.highlighter == null || !info.highlighter.isValid()) continue;
      int startOffset = info.highlighter.getStartOffset();
      int endOffset = info.highlighter.getEndOffset();
      if (info.isAfterEndOfLine) {
        startOffset += 1;
        endOffset += 1;
      }
      if (startOffset > offset || offset > endOffset) {
        if (!includeFixRange) continue;
        if (info.fixMarker == null || !info.fixMarker.isValid()) continue;
        startOffset = info.fixMarker.getStartOffset();
        endOffset = info.fixMarker.getEndOffset();
        if (info.isAfterEndOfLine) {
          startOffset += 1;
          endOffset += 1;
        }
        if (startOffset > offset || offset > endOffset) continue;
      }

      if (foundInfoList.size() != 0) {
        final HighlightInfo foundInfo = foundInfoList.get(0);
        if (foundInfo.getSeverity().isLess(info.getSeverity())) {
          foundInfoList.clear();
        }
        else if (info.getSeverity().isLess(foundInfo.getSeverity())) {
          continue;
        }
      }
      foundInfoList.add(info);
    }

    if (foundInfoList.size() == 0) return null;
    if (foundInfoList.size() == 1) return foundInfoList.get(0);
    return new HighlightInfoComposite(foundInfoList);
  }

  public static void setHighlights(Document document, HighlightInfo[] highlights, Project project) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    MarkupModel markup = document.getMarkupModel(project);
    if (markup != null) {
      highlights = stripWarningsCoveredByErrors(highlights, markup);
      markup.putUserData(HIGHLIGHTS_IN_EDITOR_DOCUMENT_KEY, highlights);

      DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
      if (codeAnalyzer != null && codeAnalyzer instanceof DaemonCodeAnalyzerImpl) {
        ((DaemonCodeAnalyzerImpl)codeAnalyzer).myStatusBarUpdater.updateStatus();
      }
    }
  }

  private static HighlightInfo[] stripWarningsCoveredByErrors(final HighlightInfo[] highlights, MarkupModel markup) {
    List<HighlightInfo> all = new ArrayList<HighlightInfo>(Arrays.asList(highlights));
    List<HighlightInfo> errors = new ArrayList<HighlightInfo>();
    for (int i = 0; i < highlights.length; i++) {
      HighlightInfo highlight = highlights[i];
      if (highlight.getSeverity() == HighlightInfo.ERROR) {
        errors.add(highlight);
      }
    }

    for (int i = 0; i < highlights.length; i++) {
      HighlightInfo highlight = highlights[i];
      if (highlight.getSeverity() == HighlightInfo.WARNING) {
        for (int j = 0; j < errors.size(); j++) {
          HighlightInfo errorInfo = errors.get(j);
          if (isCoveredBy(highlight, errorInfo)) {
            all.remove(highlight);
            final RangeHighlighter highlighter = highlight.highlighter;
            if (highlighter != null && highlighter.isValid()) {
              markup.removeHighlighter(highlighter);
            }
            break;
          }
        }
      }
    }

    return all.size() < highlights.length ? all.toArray(new HighlightInfo[all.size()]) : highlights;
  }

  private static boolean isCoveredBy(HighlightInfo testInfo, HighlightInfo coveringCandidate) {
    return testInfo.startOffset <= coveringCandidate.endOffset && testInfo.endOffset >= coveringCandidate.startOffset;
  }

  public static LineMarkerInfo[] getLineMarkers(Document document, Project project) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    MarkupModel markup = document.getMarkupModel(project);
    if (markup == null) return null;
    return markup.getUserData(MARKERS_IN_EDITOR_DOCUMENT_KEY);
  }

  public static void setLineMarkers(Document document, LineMarkerInfo[] lineMarkers, Project project) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    MarkupModel markup = document.getMarkupModel(project);
    if (markup == null) return;
    markup.putUserData(MARKERS_IN_EDITOR_DOCUMENT_KEY, lineMarkers);
  }

  public void setShowPostIntentions (boolean status) {
    myShowPostIntentions = status;
  }

  public boolean showPostIntentions () {
    return myShowPostIntentions;
  }

  public void setLastIntentionHint (IntentionHintComponent hintComponent) {
    myLastIntentionHint = hintComponent;
  }

  public IntentionHintComponent getLastIntentionHint () {
    return myLastIntentionHint;
  }

  private void updateHighlighters(final FileEditor editor, final Set<HighlightingPass> passesToPerform, final Runnable postRunnable) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    if (myUpdateProgress.isRunning()) return;
    if (passesToPerform.isEmpty()) {
      if (postRunnable != null) postRunnable.run();
      return;
    }

    final HighlightingPass daemonPass = passesToPerform.iterator().next();

    Runnable postRunnable1 = new Runnable() {
      public void run() {
        final boolean wasCanceled = myUpdateProgress.isCanceled();
        final boolean wasRunning = myUpdateProgress.isRunning();

        myUpdateThreadSemaphore.up();

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myDisposed) return;

            if (!wasCanceled || wasRunning) {
              if (daemonPass != null && editor.getComponent().isDisplayable()) {
                daemonPass.applyInformationToEditor();
              }
              passesToPerform.remove(daemonPass);
              updateHighlighters(editor, passesToPerform, postRunnable);
            }
          }
        }, ModalityState.stateForComponent(editor.getComponent()));
      }
    };

    final UpdateThread updateThread;
    synchronized (myUpdateProgress) {
      if (myUpdateProgress.isRunning()) return; //Last check to be sure we don't launch 2 threads
      updateThread = new UpdateThread(daemonPass, myProject, postRunnable1); //After the call myUpdateProgress.isRunning()
    }
    myUpdateThreadSemaphore.down();
    updateThread.start();
  }


  public void writeExternal(Element parentNode) throws WriteExternalException {
    Element disableHintsElement = new Element("disable_hints");
    parentNode.addContent(disableHintsElement);

    final ArrayList<String> array = new ArrayList<String>();
    for (Iterator<VirtualFile> iterator = myDisabledHintsFiles.iterator(); iterator.hasNext();) {
      VirtualFile file = iterator.next();
      if (file.isValid()) {
        array.add(file.getUrl());
      }
    }
    Collections.sort(array);

    for (int i = 0; i < array.size(); i++) {
      String url = array.get(i);
      Element fileElement = new Element("file");
      fileElement.setAttribute("url", url);
      disableHintsElement.addContent(fileElement);
    }
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    myDisabledHintsFiles.clear();

    Element element = parentNode.getChild("disable_hints");
    if (element != null) {
      for (Iterator iterator = element.getChildren("file").iterator(); iterator.hasNext();) {
        Element e = (Element)iterator.next();

        String url = e.getAttributeValue("url");
        if (url != null) {
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
          if (file != null) {
            myDisabledHintsFiles.add(file);
          }
        }
      }
    }
  }

  private class MyApplicationListener extends ApplicationAdapter {
    public void beforeWriteActionStart(Object action) {
      if (!myUpdateProgress.isRunning()) return;
      if (myUpdateProgress.isCanceled()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by write action:" + action);
      }
      stopProcess(false);
    }

    public void writeActionFinished(Object action) {
      stopProcess(true);
    }
  }

  private class MyCommandListener extends CommandAdapter {
    public void commandStarted(CommandEvent event) {
      if (!myUpdateProgress.isRunning()) return;
      if (myUpdateProgress.isCanceled()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by command:" + event.getCommand());
      }
      stopProcess(false);
    }

    public void commandFinished(CommandEvent event) {
      if (!myEscPressed) {
        stopProcess(true);
      }
      else {
        myEscPressed = false;
      }
    }
  }

  private class MyEditorColorsListener implements EditorColorsListener {
    public void globalSchemeChange(EditorColorsScheme scheme) {
      restart();
    }
  }

  private class MyTodoListener implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent evt) {
      if (TodoConfiguration.PROP_TODO_PATTERNS.equals(evt.getPropertyName())) {
        restart();
      }
    }
  }

  private class MyAnActionListener implements AnActionListener {
    public void beforeActionPerformed(AnAction action, DataContext dataContext) {
      AnAction escapeAction = ActionManagerEx.getInstanceEx().getAction(IdeActions.ACTION_EDITOR_ESCAPE);
      if (action != escapeAction) {
        stopProcess(true);
        myEscPressed = false;
      }
      else {
        myEscPressed = true;
      }
    }

    public void beforeEditorTyping(char c, DataContext dataContext) {
      stopProcess(true);
      myEscPressed = false;
    }
  }

  private class MyExternalResourceListener implements ExternalResourceListener {
    public void externalResourceChanged() {
      restart();
    }
  }

  private class MyAntConfigurationListener implements AntConfigurationListener {
    public void buildFileChanged(BuildFile buildFile) {
      restart();
    }

    public void buildFileAdded(BuildFile buildFile) {
      restart();
    }

    public void buildFileRemoved(BuildFile buildFile) {
      restart();
    }
  }

  private Runnable createUpdateRunnable() {
    return new Runnable() {
      public void run() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("update runnable (myUpdateByTimerEnabled = " + myUpdateByTimerEnabled + ")");
        }
        if (!myUpdateByTimerEnabled) return;

        final FileEditor[] activeEditors = getSelectedEditors();
        if (activeEditors.length == 0) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("no active editors");
          }
          return;
        }

        class UpdateEditorRunnable implements Runnable {
          private int editorIndex;

          UpdateEditorRunnable(int editorIndex) {
            this.editorIndex = editorIndex;
          }

          public void run() {
            UpdateEditorRunnable postRunnable = editorIndex < activeEditors.length - 1
                                                ? new UpdateEditorRunnable(editorIndex + 1)
                                                : null;
            updateAll(activeEditors[editorIndex], postRunnable);
          }
        }

        new UpdateEditorRunnable(0).run();
      }
    };
  }

  private FileEditor[] getSelectedEditors() {
    // Editors in modal context
    Editor[] editors = myEditorTracker.getActiveEditors();
    if (editors.length > 0) {
      FileEditor[] fileEditors = new FileEditor[editors.length];
      for (int i = 0; i < fileEditors.length; i++) {
        fileEditors[i] = TextEditorProvider.getInstance().getTextEditor(editors[i]);
      }
      return fileEditors;
    }

    // Editors in tabs.
    return FileEditorManager.getInstance(myProject).getSelectedEditors();
  }

  /**
   * @fabrique used in fabrique *
   */
  private EditorTracker createEditorTracker() {
    return new EditorTracker(myProject);
  }


  private class MyEditorMouseMotionListener implements EditorMouseMotionListener {
    public void mouseMoved(EditorMouseEvent e) {
      Editor editor = e.getEditor();
      if (myProject != editor.getProject()) return;

      boolean shown = false;
      try {
        LogicalPosition pos = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());
        if (e.getArea() == EditorMouseEventArea.EDITING_AREA) {
          int offset = editor.logicalPositionToOffset(pos);
          if (editor.offsetToLogicalPosition(offset).column != pos.column) return; // we are in virtual space
          HighlightInfo info = findHighlightByOffset(editor.getDocument(), offset, false);
          if (info == null || info.description == null) return;
          DaemonTooltipUtil.showInfoTooltip(info, editor, offset);
          shown = true;
        }
      }
      finally {
        if (!shown) {
          DaemonTooltipUtil.cancelTooltips();
        }
      }
    }

    public void mouseDragged(EditorMouseEvent e) {
      HintManager.getInstance().getTooltipController().cancelTooltips();
    }
  }
}