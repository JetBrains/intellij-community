package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.*;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class UndoManagerImpl extends UndoManager implements ProjectComponent, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl.UndoManagerImpl");

  public static final int GLOBAL_UNDO_LIMIT = 10;
  public static final int LOCAL_UNDO_LIMIT = 100;
  private static final int COMMANDS_TO_KEEP_LIVE_QUEUES = 100;
  private static final int COMMAND_TO_RUN_COMPACT = 20;
  private static final int FREE_QUEUES_LIMIT = 30;

  private ProjectEx myProject;

  private int myCommandLevel = 0;

  private static final int NONE = 0;
  private static final int UNDO = 1;
  private static final int REDO = 2;
  private int myCurrentOperationState = NONE;

  private CommandMerger myMerger;

  private CommandListener myCommandListener;

  private final UndoRedoStacksHolder myUndoStacksHolder = new UndoRedoStacksHolder(this);
  private final UndoRedoStacksHolder myRedoStacksHolder = new UndoRedoStacksHolder(this);

  private final Map<Document, List<Document>> myDocumentCopies = new HashMap<Document, List<Document>>();

  private DocumentEditingUndoProvider myDocumentEditingUndoProvider;
  private CommandMerger myCurrentMerger;
  private CurrentEditorProvider myCurrentEditorProvider;
  private FileOperationsUndoProvider myFileOperationUndoProvider;

  private Project myCurrentActionProject = DummyProject.getInstance();
  private int myCommandCounter = 1;
  private MyBeforeDeletionListener myBeforeFileDeletionListener;
  private final CommandProcessor myCommandProcessor;
  private final EditorFactory myEditorFactory;
  private final VirtualFileManager myVirtualFileManager;
  private final StartupManager myStartupManager;
  private final ProjectRootManager myRootManager;
  private ModuleRootListener myRootsChangesListener;


  public UndoManagerImpl(Project project,
                         Application application,
                         CommandProcessor commandProcessor,
                         EditorFactory editorFactory,
                         VirtualFileManager virtualFileManager,
                         StartupManager startupManager,
                         ProjectRootManager rm) {
    myProject = (ProjectEx)project;
    myCommandProcessor = commandProcessor;
    myEditorFactory = editorFactory;
    myVirtualFileManager = virtualFileManager;
    myStartupManager = startupManager;
    myRootManager = rm;

    init(application);
  }

  public UndoManagerImpl(Application application,
                         CommandProcessor commandProcessor,
                         EditorFactory editorFactory,
                         VirtualFileManager virtualFileManager) {
    this(null, application, commandProcessor, editorFactory, virtualFileManager, null, null);
  }


  private void init(Application application) {
    if (myProject == null || application.isUnitTestMode() && !myProject.isDefault() && !myProject.isDummy()) {
      initialize();
    }
  }

  @NotNull
  public String getComponentName() {
    return "UndoManager";
  }

  public Project getProject() {
    return myProject;
  }

  public void initComponent() {
  }

  private void initialize() {
    Runnable initAction = new Runnable() {
      public void run() {
        runStartupActivity();
      }
    };
    if (myProject != null) {
      myStartupManager.registerStartupActivity(initAction);
    }
    else {
      initAction.run();
    }

  }

  private void runStartupActivity() {
    myCurrentEditorProvider = new FocusBasedCurrentEditorProvider();
    myCommandListener = new CommandAdapter() {
      private boolean myFakeCommandStarted = false;

      public void commandStarted(CommandEvent event) {
        onCommandStarted(event.getProject(), event.getUndoConfirmationPolicy());
      }

      public void commandFinished(CommandEvent event) {
        onCommandFinished(event.getProject(), event.getCommandName(), event.getCommandGroupId());
      }

      public void undoTransparentActionStarted() {
        if (!isInsideCommand()) {
          myFakeCommandStarted = true;
          onCommandStarted(myProject, UndoConfirmationPolicy.DEFAULT);
        }
      }

      public void undoTransparentActionFinished() {
        if (myFakeCommandStarted) {
          myFakeCommandStarted = false;
          onCommandFinished(myProject, "", null);
        }
      }
    };
    myCommandProcessor.addCommandListener(myCommandListener);

    myDocumentEditingUndoProvider = new DocumentEditingUndoProvider(myProject, myEditorFactory);
    myMerger = new CommandMerger(this, myEditorFactory);

    myFileOperationUndoProvider = new FileOperationsUndoProvider(this, myProject);

    myBeforeFileDeletionListener = new MyBeforeDeletionListener();
    myVirtualFileManager.addVirtualFileListener(myBeforeFileDeletionListener);

    registerRootChangesListener();
  }

  private void registerRootChangesListener() {
    if (myProject == null) return;

    myRootsChangesListener = new ModuleRootListener() {
      public void rootsChanged(ModuleRootEvent e) {
      }

      public void beforeRootsChange(ModuleRootEvent e) {
        dropGlobalHistory();
      }
    };
    myRootManager.addModuleRootListener(myRootsChangesListener);
  }

  private void unregisterRootChangesListener() {
    if (myProject == null) return;

    myRootManager.removeModuleRootListener(myRootsChangesListener);
  }

  private void onCommandFinished(final Project project, final String commandName, final Object commandGroupId) {
    commandFinished(commandName, commandGroupId);
    if (myCommandLevel == 0) {
      myFileOperationUndoProvider.commandFinished(project);
      myCurrentActionProject = DummyProject.getInstance();
      if (myProject == null) myMerger.clearDocumentRefs(); //do not leak document refs at app level
    }
    LOG.assertTrue(myCommandLevel == 0 || !(myCurrentActionProject instanceof DummyProject));
  }

  private void onCommandStarted(final Project project, UndoConfirmationPolicy undoConfirmationPolicy) {
    if (myCommandLevel == 0) {
      myFileOperationUndoProvider.commandStarted(project);
      myCurrentActionProject = project;
    }

    commandStarted(undoConfirmationPolicy);

    LOG.assertTrue(myCommandLevel == 0 || !(myCurrentActionProject instanceof DummyProject));
  }

  public void dropHistory() {
    dropMergers();

    LOG.assertTrue(myCommandLevel == 0);

    myUndoStacksHolder.dropHistory();
    myRedoStacksHolder.dropHistory();
  }

  private void dropGlobalHistory() {
    dropMergers();

    myUndoStacksHolder.clearStacksWithComplexCommands();
    myRedoStacksHolder.clearStacksWithComplexCommands();
  }

  private void dropMergers() {
    // Run dummy command in order to drop all mergers...
    CommandProcessor.getInstance()
      .executeCommand(myProject, EmptyRunnable.getInstance(), CommonBundle.message("drop.undo.history.command.name"), null);
  }

  public void disposeComponent() {
    unregisterRootChangesListener();

    if (myCommandListener != null) {
      myCommandProcessor.removeCommandListener(myCommandListener);
      myDocumentEditingUndoProvider.dispose();
      myMerger.dispose();
      myFileOperationUndoProvider.dispose();
    }
    if (myBeforeFileDeletionListener != null) {
      myVirtualFileManager.removeVirtualFileListener(myBeforeFileDeletionListener);
    }
    myProject = null;
  }

  public void setCurrentEditorProvider(CurrentEditorProvider p) {
    myCurrentEditorProvider = p;
  }

  public CurrentEditorProvider getCurrentEditorProvider() {
    return myCurrentEditorProvider;
  }

  public void projectOpened() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      initialize();
    }
  }

  public void projectClosed() {
  }

  @TestOnly
  public void flushCurrentCommandMerger() {
    myMerger.flushCurrentCommand();
  }

  public void clearUndoRedoQueue(FileEditor editor) {
    LOG.assertTrue(myCommandLevel == 0);
    myMerger.flushCurrentCommand();
    disposeCurrentMerger();

    myUndoStacksHolder.clearEditorStack(editor);
    myRedoStacksHolder.clearEditorStack(editor);
  }

  public void clearUndoRedoQueue(Document document) {
    clearUndoRedoQueue(DocumentReferenceByDocument.createDocumentReference(document));
  }

  private void clearUndoRedoQueue(DocumentReference docRef) {
    myMerger.flushCurrentCommand();
    disposeCurrentMerger();

    myUndoStacksHolder.clearFileStack(docRef);
    myRedoStacksHolder.clearFileStack(docRef);
  }

  public void clearUndoRedoQueue(VirtualFile file) {
    clearUndoRedoQueue(new DocumentReferenceByVirtualFile(file));
  }

  public void compact() {
    if (myCurrentOperationState == NONE && myCommandCounter % COMMAND_TO_RUN_COMPACT == 0) {
      doCompact();
    }
  }

  private void doCompact() {
    Set<DocumentReference> docsOnHold = new HashSet<DocumentReference>(myUndoStacksHolder.getAffectedDocuments());
    docsOnHold.addAll(myRedoStacksHolder.getAffectedDocuments());

    docsOnHold.removeAll(myUndoStacksHolder.getDocsInGlobalQueue());
    docsOnHold.removeAll(myRedoStacksHolder.getDocsInGlobalQueue());

    Set<DocumentReference> openedDocs = new HashSet<DocumentReference>();
    for (DocumentReference docRef : docsOnHold) {
      final VirtualFile file = docRef.getFile();
      if (file != null) {
        if (myProject != null && FileEditorManager.getInstance(myProject).isFileOpen(file)) {
          openedDocs.add(docRef);
        }
      }
      else {
        Document document = docRef.getDocument();
        if (document != null && EditorFactory.getInstance().getEditors(document, myProject).length > 0) {
          openedDocs.add(docRef);
        }
      }
    }
    docsOnHold.removeAll(openedDocs);

    if (docsOnHold.size() <= FREE_QUEUES_LIMIT) return;

    final DocumentReference[] freeDocs = docsOnHold.toArray(new DocumentReference[docsOnHold.size()]);
    Arrays.sort(freeDocs, new Comparator<DocumentReference>() {
      public int compare(DocumentReference docRef1, DocumentReference docRef2) {
        return getRefAge(docRef1) - getRefAge(docRef2);
      }
    });

    for (int i = 0; i < freeDocs.length - FREE_QUEUES_LIMIT; i++) {
      DocumentReference doc = freeDocs[i];
      if (getRefAge(doc) + COMMANDS_TO_KEEP_LIVE_QUEUES > myCommandCounter) break;
      clearUndoRedoQueue(doc);
    }
  }

  private int getRefAge(DocumentReference ref) {
    return Math.max(myUndoStacksHolder.getYoungestCommandAge(ref), myRedoStacksHolder.getYoungestCommandAge(ref));
  }

  public void undoableActionPerformed(UndoableAction action) {
    if (myCurrentOperationState != NONE) return;

    if (myCommandLevel == 0) {
      LOG.assertTrue(action instanceof NonUndoableAction,
                     "Undoable actions allowed inside commands only (see com.intellij.openapi.command.CommandProcessor.executeCommand())");
      commandStarted(UndoConfirmationPolicy.DEFAULT);
      myCurrentMerger.add(action, false);
      commandFinished("", null);
      return;
    }

    myCurrentMerger.add(action, CommandProcessor.getInstance().isUndoTransparentActionInProgress());
  }

  public boolean isUndoInProgress() {
    return myCurrentOperationState == UNDO;
  }

  public boolean isRedoInProgress() {
    return myCurrentOperationState == REDO;
  }

  public void undo(@Nullable FileEditor editor) {
    LOG.assertTrue(isUndoAvailable(editor));
    myCurrentOperationState = UNDO;
    undoOrRedo(editor);
  }

  public void redo(@Nullable FileEditor editor) {
    LOG.assertTrue(isRedoAvailable(editor));
    myCurrentOperationState = REDO;
    undoOrRedo(editor);
  }

  private void undoOrRedo(final FileEditor editor) {
    final RuntimeException[] exception = new RuntimeException[1];
    Runnable executeUndoOrRedoAction = new Runnable() {
      public void run() {
        try {
          if (isUndoInProgress()) {
            myMerger.undoOrRedo(editor, true);
          }
          else {
            myMerger.undoOrRedo(editor, false);
          }
        }
        catch (RuntimeException ex) {
          exception[0] = ex;
        }
        finally {
          myCurrentOperationState = NONE;
        }
      }
    };

    CommandProcessor.getInstance()
      .executeCommand(myProject, executeUndoOrRedoAction, isUndoInProgress() ? CommonBundle.message("undo.command.name") : CommonBundle
        .message("redo.command.name"), null, myMerger.getUndoConfirmationPolicy());
    if (exception[0] != null) throw exception[0];
  }

  public boolean isUndoAvailable(@Nullable FileEditor editor) {
    return isUndoOrRedoAvailable(editor, myUndoStacksHolder, true);
  }

  public boolean isRedoAvailable(@Nullable FileEditor editor) {
    return isUndoOrRedoAvailable(editor, myRedoStacksHolder, false);
  }

  private boolean isUndoOrRedoAvailable(FileEditor editor, UndoRedoStacksHolder stackHolder, boolean shouldCheckMerger) {
    if (editor instanceof TextEditor) {
      Editor activeEditor = ((TextEditor)editor).getEditor();
      if (activeEditor.isViewer()) {
        return false;
      }
    }

    Document[] documents = editor == null ? null : TextEditorProvider.getDocuments(editor);

    if (documents != null && documents.length > 0) {
      for (Document document : documents) {
        Document original = getOriginal(document);

        if (shouldCheckMerger) {
          if (myMerger != null && !myMerger.isEmpty(DocumentReferenceByDocument.createDocumentReference(original))) {
            return true;
          }
        }

        LinkedList localStack = stackHolder.getStack(original);
        if (!localStack.isEmpty()) {
          return true;
        }
      }

      return false;
    }
    else {
      if (shouldCheckMerger) {
        if (myMerger != null && myMerger.isComplex() && !myMerger.isEmpty()) return true;
      }

      return !stackHolder.getGlobalStack().isEmpty();
    }
  }

  public boolean isActive() {
    return Comparing.equal(myProject, myCurrentActionProject);
  }

  private void commandStarted(UndoConfirmationPolicy undoConfirmationPolicy) {
    if (myCommandLevel == 0) {
      myCurrentMerger = new CommandMerger(this, EditorFactory.getInstance());
    }
    LOG.assertTrue(myCurrentMerger != null, String.valueOf(myCommandLevel));
    myCurrentMerger.setBeforeState(getCurrentState());
    myCurrentMerger.mergeUndoConfirmationPolicy(undoConfirmationPolicy);

    myCommandLevel++;
  }

  private EditorAndState getCurrentState() {
    FileEditor editor = myCurrentEditorProvider.getCurrentEditor();
    if (editor == null) {
      return null;
    }
    return new EditorAndState(editor, editor.getState(FileEditorStateLevel.UNDO));
  }

  private void commandFinished(String commandName, Object groupId) {
    if (myCommandLevel == 0) return; // possible if command listener was added within command
    myCommandLevel--;
    if (myCommandLevel > 0) return;
    myCurrentMerger.setAfterState(getCurrentState());
    myMerger.commandFinished(commandName, groupId, myCurrentMerger);

    disposeCurrentMerger();
  }

  private void disposeCurrentMerger() {
    LOG.assertTrue(myCommandLevel == 0);
    if (myCurrentMerger != null) {
      myCurrentMerger.dispose();
      myCurrentMerger = null;
    }
  }

  public UndoRedoStacksHolder getUndoStacksHolder() {
    return myUndoStacksHolder;
  }

  public UndoRedoStacksHolder getRedoStacksHolder() {
    return myRedoStacksHolder;
  }

  public boolean isInsideCommand() {
    return myCommandLevel > 0;
  }

  public boolean undoableActionsForDocumentAreEmpty(DocumentReference docRef) {
    if (myCurrentMerger != null && !myCurrentMerger.isEmpty(docRef)) return false;
    if (myMerger != null && !myMerger.isEmpty(docRef)) return false;
    if (!myUndoStacksHolder.getStack(docRef).isEmpty()) return false;
    LinkedList<UndoableGroup> globalStack = myUndoStacksHolder.getGlobalStack();
    for (final UndoableGroup group : globalStack) {
      Collection<DocumentReference> affectedDocuments = group.getAffectedDocuments();
      if (affectedDocuments.contains(docRef)) return false;
    }
    return true;
  }

  public int getCommandCounterAndInc() {
    return ++myCommandCounter;
  }

  public DocumentReference findInvalidatedReferenceByUrl(String url) {
    DocumentReference result = findInvalidatedReferenceByUrl(myUndoStacksHolder.getAffectedDocuments(), url);
    if (result != null) return result;
    result = findInvalidatedReferenceByUrl(myRedoStacksHolder.getAffectedDocuments(), url);
    if (result != null) return result;
    result = findInvalidatedReferenceByUrl(myRedoStacksHolder.getGlobalStackAffectedDocuments(), url);
    if (result != null) return result;
    if (myMerger != null) {
      result = findInvalidatedReferenceByUrl(myMerger.getAffectedDocuments(), url);
      if (result != null) return result;
    }
    if (myCurrentMerger != null) {
      result = findInvalidatedReferenceByUrl(myCurrentMerger.getAffectedDocuments(), url);
      if (result != null) return result;
    }
    return null;
  }

  private static DocumentReference findInvalidatedReferenceByUrl(Collection<DocumentReference> collection, String url) {
    for (final DocumentReference documentReference : collection) {
      if (documentReference.equalsByUrl(url)) return documentReference;
    }
    return null;

  }

  public void markDocumentForUndo(final PsiFile file) {
    Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;
    final DocumentReference ref = DocumentReferenceByDocument.createDocumentReference(document);
    undoableActionPerformed(new UndoableAction() {
      public void undo() {
      }

      public void redo() {
      }

      public DocumentReference[] getAffectedDocuments() {
        return new DocumentReference[]{ref};
      }

      public boolean isComplex() {
        return false;
      }

      @NonNls
      public String toString() {
        return "markDocumentForUndo: " + file;
      }
    });
  }

  @Override
  public void registerDocumentCopy(Document d, Document copy) {
    List<Document> copies = myDocumentCopies.get(d);
    if (copies == null) {
      copies = new ArrayList<Document>();
      myDocumentCopies.put(d, copies);
    }
    copies.add(copy);
  }

  @Override
  public void unregisterDocumentCopy(Document d, Document copy) {
    List<Document> copies = myDocumentCopies.get(d);
    copies.remove(copy);
    if (copies.isEmpty()) myDocumentCopies.remove(d);
  }

  public Document getOriginal(Document d) {
    for (Map.Entry<Document,List<Document>> copies: myDocumentCopies.entrySet()) {
      for (Document copy : copies.getValue()) {
        if (d == copy) return copies.getKey();
      }
    }
    return d;
  }

  public boolean isCopy(Document d) {
    for (List<Document> dd : myDocumentCopies.values()) {
      if(dd.contains(d)) return true;
    }
    return false;
  }

  private class MyBeforeDeletionListener extends VirtualFileAdapter {
    public void beforeFileDeletion(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      beforeFileDeletion(file, myUndoStacksHolder.getAffectedDocuments());
      beforeFileDeletion(file, myUndoStacksHolder.getGlobalStackAffectedDocuments());
      beforeFileDeletion(file, myRedoStacksHolder.getAffectedDocuments());
      beforeFileDeletion(file, myRedoStacksHolder.getGlobalStackAffectedDocuments());
      if (myMerger != null) beforeFileDeletion(file, myMerger.getAffectedDocuments());
      if (myCurrentMerger != null) beforeFileDeletion(file, myCurrentMerger.getAffectedDocuments());
    }

    private void beforeFileDeletion(VirtualFile file, Collection<DocumentReference> docs) {
      for (final DocumentReference documentReference : docs) {
        documentReference.beforeFileDeletion(file);
      }

    }
  }
}
