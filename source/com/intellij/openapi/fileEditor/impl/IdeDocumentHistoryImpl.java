package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.util.Pair;

import java.util.Iterator;
import java.util.LinkedList;

public class IdeDocumentHistoryImpl extends IdeDocumentHistory implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl");

  private static final int BACK_QUEUE_LIMIT = 25;
  private static final int CHANGE_QUEUE_LIMIT = 25;

  private final Project myProject;

  private final EditorFactory myEditorFactory;
  private FileDocumentManager myFileDocumentManager;
  private FileEditorManagerEx myEditorManager;
  private VirtualFileManager myVfManager;
  private CommandProcessor myCmdProcessor;
  private VirtualFileListener myFileListener;
  private ToolWindowManager myToolWindowManager;

  private final LinkedList<PlaceInfo> myBackPlaces = new LinkedList<PlaceInfo>(); // LinkedList of PlaceInfo's
  private final LinkedList<PlaceInfo> myForwardPlaces = new LinkedList<PlaceInfo>(); // LinkedList of PlaceInfo's
  private boolean myBackInProgress = false;
  private boolean myForwardInProgress = false;
  private Object myLastGroupId = null;

  // change's navigation
  private final LinkedList<PlaceInfo> myChangePlaces = new LinkedList<PlaceInfo>(); // LinkedList of PlaceInfo's
  private int myStartIndex = 0;
  private int myCurrentIndex = 0;
  private PlaceInfo myCurrentChangePlace = null;

  private PlaceInfo myCommandStartPlace = null;
  private boolean myCurrentCommandIsNavigation = false;
  private boolean myCurrentCommandHasChanges = false;
  private boolean myCurrentCommandHasMoves = false;

  private DocumentListener myDocumentListener;
  private CaretListener myCaretListener;
  private final CommandListener myCommandListener = new CommandAdapter() {
    public void commandStarted(CommandEvent event) {
      onCommandStarted();
    }

    public void commandFinished(CommandEvent event) {
      onCommandFinished(event.getCommandGroupId());
    }
  };


  public IdeDocumentHistoryImpl(Project project,
                                EditorFactory editorFactory,
                                FileEditorManager editorManager,
                                VirtualFileManager vfManager,
                                CommandProcessor cmdProcessor,
                                ToolWindowManager toolWindowManager) {
    myProject = project;
    myEditorFactory = editorFactory;
    myEditorManager = (FileEditorManagerEx)editorManager;
    myVfManager = vfManager;
    myCmdProcessor = cmdProcessor;
    myToolWindowManager = toolWindowManager;
  }
  public IdeDocumentHistoryImpl(Project project,
                                EditorFactory editorFactory,
                                FileEditorManager editorManager,
                                VirtualFileManager vfManager,
                                CommandProcessor cmdProcessor
                                ) {
    this(project, editorFactory, editorManager, vfManager, cmdProcessor, null);
  }

  public final void projectOpened() {
    EditorEventMulticaster eventMulticaster = myEditorFactory.getEventMulticaster();

    myDocumentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        onDocumentChanged(e);
      }
    };
    eventMulticaster.addDocumentListener(myDocumentListener);

    myCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        onCaretPositionChanged(e);
      }
    };
    eventMulticaster.addCaretListener(myCaretListener);

    FileEditorManager fileEditorManager = myEditorManager;
    fileEditorManager.addFileEditorManagerListener(new FileEditorManagerAdapter() {
      public void selectionChanged(FileEditorManagerEvent e) {
        onSelectionChanged();
      }
    });

    myFileListener = new VirtualFileAdapter() {
      public void fileDeleted(VirtualFileEvent event) {
        onFileDeleted();
      }
    };
    myVfManager.addVirtualFileListener(myFileListener);
    myCmdProcessor.addCommandListener(myCommandListener);
  }

  public final void onFileDeleted() {
    removeInvalidFilesFromStacks();
  }

  public final void onSelectionChanged() {
    myCurrentCommandIsNavigation = true;
    myCurrentCommandHasMoves = true;
  }

  public final void onCaretPositionChanged(CaretEvent e) {
    if (e.getOldPosition().line == e.getNewPosition().line) return;
    Document document = e.getEditor().getDocument();
    if (getFileDocumentManager().getFile(document) != null) {
      myCurrentCommandHasMoves = true;
    }
  }

  public final void onDocumentChanged(DocumentEvent e) {
    Document document = e.getDocument();
    if (getFileDocumentManager().getFile(document) != null) {
      myCurrentCommandHasChanges = true;
    }
  }

  public final void onCommandStarted() {
    myCommandStartPlace = getCurrentPlaceInfo();
    myCurrentCommandIsNavigation = false;
    myCurrentCommandHasChanges = false;
    myCurrentCommandHasMoves = false;
  }

  private PlaceInfo getCurrentPlaceInfo() {
    final Pair<FileEditor,FileEditorProvider> selectedEditorWithProvider = getSelectedEditor();
    if (selectedEditorWithProvider != null) {
      return createPlaceInfo(selectedEditorWithProvider.getFirst (), selectedEditorWithProvider.getSecond ());
    }
    else {
      return null;
    }
  }

  public final void onCommandFinished(Object commandGroupId) {
    if (myCommandStartPlace != null) {
      if (myCurrentCommandIsNavigation && myCurrentCommandHasMoves) {
        if (!myBackInProgress) {
          Object gropupId = commandGroupId;
          if (gropupId == null || !gropupId.equals(myLastGroupId)) {
            putLastOrMerge(myBackPlaces, myCommandStartPlace, BACK_QUEUE_LIMIT);
          }
          if (!myForwardInProgress) {
            for (Iterator<PlaceInfo> iterator = myForwardPlaces.iterator(); iterator.hasNext();) {
              iterator.next();
            }
            myForwardPlaces.clear();
          }
        }
        removeInvalidFilesFromStacks();
      }
    }
    myLastGroupId = commandGroupId;

    if (myCurrentCommandHasChanges) {
      setCurrentChangePlace();
    }
    else if (myCurrentCommandHasMoves) {
      pushCurrentChangePlace();
    }
  }


  public final void projectClosed() {
    EditorEventMulticaster eventMulticaster = myEditorFactory.getEventMulticaster();
    eventMulticaster.removeDocumentListener(myDocumentListener);
    eventMulticaster.removeCaretListener(myCaretListener);

    myVfManager.removeVirtualFileListener(myFileListener);
    myCmdProcessor.removeCommandListener(myCommandListener);
  }

  public final void includeCurrentCommandAsNavigation() {
    myCurrentCommandIsNavigation = true;
  }

  public final void includeCurrentPlaceAsChangePlace() {
    setCurrentChangePlace();
    pushCurrentChangePlace();
  }

  private void setCurrentChangePlace() {
    final Pair<FileEditor,FileEditorProvider> selectedEditorWithProvider = getSelectedEditor();
    if (selectedEditorWithProvider == null) {
      return;
    }
    final PlaceInfo placeInfo = createPlaceInfo(selectedEditorWithProvider.getFirst(), selectedEditorWithProvider.getSecond ());
    myCurrentChangePlace = placeInfo;
    if (myChangePlaces.size() > 0) {
      final PlaceInfo lastInfo = myChangePlaces.get(myChangePlaces.size() - 1);
      if (isSame(placeInfo, lastInfo)) {
        myChangePlaces.removeLast();
      }
    }
    myCurrentIndex = myStartIndex + myChangePlaces.size();
  }

  private void pushCurrentChangePlace() {
    if (myCurrentChangePlace != null) {
      myChangePlaces.add(myCurrentChangePlace);
      if (myChangePlaces.size() > CHANGE_QUEUE_LIMIT) {
        myChangePlaces.removeFirst();
        myStartIndex++;
      }
      myCurrentChangePlace = null;
    }
    myCurrentIndex = myStartIndex + myChangePlaces.size();
  }

  public final void clearHistory() {
    clearPlaceList(myBackPlaces);
    clearPlaceList(myForwardPlaces);
    clearPlaceList(myChangePlaces);

    myLastGroupId = null;

    myStartIndex = 0;
    myCurrentIndex = 0;
    if (myCurrentChangePlace != null) {
      myCurrentChangePlace = null;
    }

    if (myCommandStartPlace != null) {
      myCommandStartPlace = null;
    }
  }

  public final void back() {
    removeInvalidFilesFromStacks();
    if (myBackPlaces.isEmpty()) return;
    final PlaceInfo info = myBackPlaces.removeLast();

    PlaceInfo current = getCurrentPlaceInfo();
    if (current != null) {
      if (!isSame(current, info)) {
        putLastOrMerge(myForwardPlaces, current, Integer.MAX_VALUE);
      }
    }
    putLastOrMerge(myForwardPlaces, info, Integer.MAX_VALUE);

    myBackInProgress = true;

    executeCommand(new Runnable() {
      public void run() {
        gotoPlaceInfo(info);
      }
    }, "", null);

    myBackInProgress = false;
  }

  public final void forward() {
    removeInvalidFilesFromStacks();

    final PlaceInfo target = getTargetForwardInfo();
    if (target == null) return;

    myForwardInProgress = true;
    executeCommand(new Runnable() {
      public void run() {
        gotoPlaceInfo(target);
      }
    }, "", null);
    myForwardInProgress = false;
  }

  private PlaceInfo getTargetForwardInfo() {
    if (myForwardPlaces.isEmpty()) return null;

    PlaceInfo target = myForwardPlaces.removeLast();
    PlaceInfo current = getCurrentPlaceInfo();

    while (!myForwardPlaces.isEmpty()) {
      if (isSame(current, target)) {
        target = myForwardPlaces.removeLast();
      } else {
        break;
      }
    }
    return target;
  }

  public final boolean isBackAvailable() {
    return !myBackPlaces.isEmpty();
  }

  public final boolean isForwardAvailable() {
    return !myForwardPlaces.isEmpty();
  }

  public final void navigatePreviousChange() {
    removeInvalidFilesFromStacks();
    if (myCurrentIndex == myStartIndex) return;
    int index = myCurrentIndex - 1;
    final PlaceInfo info = myChangePlaces.get(index - myStartIndex);

    executeCommand(new Runnable() {
      public void run() {
        gotoPlaceInfo(info);
      }
    }, "", null);
    myCurrentIndex = index;
  }

  public final boolean isNavigatePreviousChangeAvailable() {
    return myCurrentIndex > myStartIndex;
  }

  private void removeInvalidFilesFromStacks() {
    for (Iterator<PlaceInfo> iterator = myBackPlaces.iterator(); iterator.hasNext();) {
      PlaceInfo info = iterator.next();
      if (info.myFile != null) {
        if (!info.myFile.isValid()) {
          iterator.remove();
        }
      }
      else {
        if (!info.getFile().isValid()) {
          iterator.remove();
        }
      }
    }

    for (Iterator<PlaceInfo> iterator = myForwardPlaces.iterator(); iterator.hasNext();) {
      PlaceInfo info = iterator.next();
      if (info.myFile != null) {
        if (!info.myFile.isValid()) {
          iterator.remove();
        }
      }
      else {
        if (!info.getFile().isValid()) {
          iterator.remove();
        }
      }
    }

    for (Iterator<PlaceInfo> iterator = myChangePlaces.iterator(); iterator.hasNext();) {
      PlaceInfo info = iterator.next();
      if (info.myFile != null) {
        if (!info.myFile.isValid()) {
          iterator.remove();
        }
      }
      else {
        if (!info.getFile().isValid()) {
          iterator.remove();
          myCurrentIndex = myStartIndex + myChangePlaces.size();
        }
      }
    }
  }

  private void gotoPlaceInfo(PlaceInfo info) { // TODO: Msk
    LOG.assertTrue(info != null);
    final boolean wasActive = myToolWindowManager.isEditorComponentActive();
    final Pair<FileEditor[],FileEditorProvider[]> editorsWithProviders = myEditorManager.openFileWithProviders(info.getFile(), wasActive);
    final FileEditor        [] editors   = editorsWithProviders.getFirst();
    final FileEditorProvider[] providers = editorsWithProviders.getSecond();
    for (int i = 0; i < editors.length; i++) {
      String typeId = providers [i].getEditorTypeId();
      if (typeId.equals(info.getEditorTypeId())) {
        editors[i].setState(info.getNavigationState());
      }
    }
  }

  /**
   * @return currently selected FileEditor or null.
   */
  protected Pair<FileEditor,FileEditorProvider> getSelectedEditor() {
    final FileEditorManagerEx fileEditorManager = myEditorManager;
    final VirtualFile[] selectedFiles = fileEditorManager.getSelectedFiles();
    if (selectedFiles.length == 0) {
      return null;
    }
    return fileEditorManager.getSelectedEditorWithProvider(selectedFiles[0]);
  }

  private PlaceInfo createPlaceInfo(final FileEditor fileEditor, final FileEditorProvider fileProvider) {
    LOG.assertTrue(fileEditor != null);
    final FileEditorManagerEx fileEditorManager = myEditorManager;
    final VirtualFile file = fileEditorManager.getFile(fileEditor);
    LOG.assertTrue(file != null);

    final FileEditorState state = fileEditor.getState(FileEditorStateLevel.NAVIGATION);

    return new PlaceInfo(file, state, fileProvider.getEditorTypeId());
  }

  private static void clearPlaceList(LinkedList<PlaceInfo> list) {
    list.clear();
  }


  public final String getComponentName() {
    return "IdeDocumentHistory";
  }

  private void putLastOrMerge(LinkedList list, PlaceInfo next, int limitSizeLimit) {
    if (list.size() > 0) {
      PlaceInfo prev = (PlaceInfo)list.get(list.size() - 1);
      if (isSame(prev, next)) {
        list.removeLast();
      }
    }

    list.add(next);
    if (list.size() > limitSizeLimit) {
      list.removeFirst();
    }
  }

  public FileDocumentManager getFileDocumentManager() {
    if (myFileDocumentManager == null) {
      myFileDocumentManager = FileDocumentManager.getInstance();
    }
    return myFileDocumentManager;
  }

  private static final class PlaceInfo {

    private final VirtualFile myFile;
    private final FileEditorState myNavigationState;
    private final String myEditorTypeId;

    public PlaceInfo(VirtualFile file, FileEditorState navigationState, String editorTypeId) {
      myNavigationState = navigationState;
      myFile = file;
      myEditorTypeId = editorTypeId;
    }

    public FileEditorState getNavigationState() {
      return myNavigationState;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public String getEditorTypeId() {
      return myEditorTypeId;
    }

    public String toString() {
      return getFile().getName() + " " + getNavigationState();
    }

  }

  public LinkedList<PlaceInfo> getBackPlaces() {
    return myBackPlaces;
  }

  public LinkedList<PlaceInfo> getForwardPlaces() {
    return myForwardPlaces;
  }

  public final void initComponent() { }

  public final void disposeComponent() {
  }

  protected void executeCommand(Runnable runnable, String name, Object groupId) {
    myCmdProcessor.executeCommand(myProject, runnable, name, groupId);
  }

  private boolean isSame(PlaceInfo first, PlaceInfo second) {
    if (first.getFile().equals(second.getFile())) {
      FileEditorState firstState = first.getNavigationState();
      FileEditorState secondState = second.getNavigationState();
      return firstState.equals(secondState) || firstState.canBeMergedWith(secondState, FileEditorStateLevel.NAVIGATION);
    }

    return false;
  }


}