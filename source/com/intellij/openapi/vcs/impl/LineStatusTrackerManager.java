/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.07.2006
 * Time: 13:24:17
 */
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vfs.*;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public class LineStatusTrackerManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.LineStatusTrackerManager");

  public static LineStatusTrackerManager getInstance(Project project) {
    return project.getComponent(LineStatusTrackerManager.class);
  }

  private final Project myProject;

  private HashMap<Document, LineStatusTracker> myLineStatusTrackers =
    new HashMap<Document, LineStatusTracker>();

  private HashMap<Document, Alarm> myLineStatusUpdateAlarms =
    new HashMap<Document, Alarm>();

  private final Object TRACKERS_LOCK = new Object();
  private boolean myIsDisposed = false;
  @NonNls protected static final String IGNORE_CHANGEMARKERS_KEY = "idea.ignore.changemarkers";
  private ProjectLevelVcsManagerImpl myVcsManager;
  private final MyFileStatusListener myFileStatusListener = new MyFileStatusListener();
  private EditorFactoryListener myEditorFactoryListener = new MyEditorFactoryListener();
  private final MyVirtualFileListener myVirtualFileListener = new MyVirtualFileListener();
  private final EditorColorsListener myEditorColorsListener = new EditorColorsListener() {
    public void globalSchemeChange(EditorColorsScheme scheme) {
      resetTrackersForOpenFiles();
    }
  };

  public LineStatusTrackerManager(final Project project, final ProjectLevelVcsManagerImpl vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
  }

  public void projectOpened() {
    myLineStatusTrackers = new HashMap<Document, LineStatusTracker>();
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);
    EditorColorsManager.getInstance().addEditorColorsListener(myEditorColorsListener);
  }

  public void projectClosed() {
    try {
      dispose();
    }
    finally {
      myIsDisposed = true;
    }
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "LineStatusTrackerManager";
  }

  public void initComponent() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void disposeComponent() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  private void dispose() {
    final Collection<LineStatusTracker> trackers = myLineStatusTrackers.values();
    final LineStatusTracker[] lineStatusTrackers = trackers.toArray(new LineStatusTracker[trackers.size()]);
    for (LineStatusTracker tracker : lineStatusTrackers) {
      releaseTracker(tracker.getDocument());
    }

    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
    VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
    EditorColorsManager.getInstance().removeEditorColorsListener(myEditorColorsListener);

    myLineStatusTrackers = null;
}

  public void setLineStatusTracker(Document document, LineStatusTracker tracker) {
    synchronized (TRACKERS_LOCK) {
      myLineStatusTrackers.put(document, tracker);
    }
  }

  public LineStatusTracker getLineStatusTracker(Document document) {
    if (myLineStatusTrackers == null) return null;
    return myLineStatusTrackers.get(document);
  }


  public LineStatusTracker setUpToDateContent(Document document, String lastUpToDateContent) {
    LineStatusTracker result = myLineStatusTrackers.get(document);
    if (result == null) {
      result = LineStatusTracker.createOn(document, lastUpToDateContent, myProject);
      myLineStatusTrackers.put(document, result);
    }
    return result;
  }

  public LineStatusTracker createTrackerForDocument(Document document) {
    LOG.assertTrue(!myLineStatusTrackers.containsKey(document));
    LineStatusTracker result = LineStatusTracker.createOn(document, myProject);
    myLineStatusTrackers.put(document, result);
    return result;
  }

  private void resetTracker(final VirtualFile virtualFile) {
    synchronized (TRACKERS_LOCK) {
      if (System.getProperty(IGNORE_CHANGEMARKERS_KEY) != null) return;

      final Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
      if (document == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Skipping resetTracker() because no cached document for " + virtualFile.getPath());
        }
        return;
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("resetting tracker for file " + virtualFile.getPath());
      }
      final LineStatusTracker tracker = myLineStatusTrackers.get(document);
      if (tracker != null) {
        resetTracker(tracker);
      }
      else {
        if (Arrays.asList(FileEditorManager.getInstance(myProject).getOpenFiles()).contains(virtualFile)) {
          installTracker(virtualFile, document);
        }
      }
    }
  }

  private boolean releaseTracker(Document document) {
    synchronized (TRACKERS_LOCK) {
      releaseUpdateAlarms(document);
      if (myLineStatusTrackers == null) return false;
      if (!myLineStatusTrackers.containsKey(document)) return false;
      LineStatusTracker tracker = myLineStatusTrackers.remove(document);
      tracker.release();
      return true;
    }
  }

  private void releaseUpdateAlarms(final Document document) {
    if (myLineStatusUpdateAlarms.containsKey(document)) {
      final Alarm alarm = myLineStatusUpdateAlarms.get(document);
      if (alarm != null) {
        alarm.cancelAllRequests();
      }
      myLineStatusUpdateAlarms.remove(document);
    }
  }

  public void resetTracker(final LineStatusTracker tracker) {
    synchronized (TRACKERS_LOCK) {
      if (tracker != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myIsDisposed) return;
            if (releaseTracker(tracker.getDocument())) {
              installTracker(tracker.getVirtualFile(), tracker.getDocument());
            }
          }
        });
      }
    }
  }

  private void installTracker(final VirtualFile virtualFile, final Document document) {
    synchronized (TRACKERS_LOCK) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      if (virtualFile == null) return;

      if (myLineStatusTrackers.containsKey(document)) return;

      final FileStatus status = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
      if (status == FileStatus.NOT_CHANGED ||
          status == FileStatus.ADDED ||
          status == FileStatus.UNKNOWN) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("installTracker() for file " + virtualFile.getPath() + " failed: status=" + status);
        }
        return;
      }

      AbstractVcs activeVcs = myVcsManager.getVcsFor(virtualFile);

      if (activeVcs == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("installTracker() for file " + virtualFile.getPath() + " failed: no active VCS");
        }
        return;
      }

      if (!(virtualFile.getFileSystem() instanceof LocalFileSystem)) return;

      final UpToDateRevisionProvider upToDateRevisionProvider = activeVcs.getUpToDateRevisionProvider();
      if (upToDateRevisionProvider == null) return;
      if (System.getProperty(IGNORE_CHANGEMARKERS_KEY) != null) return;

      final Alarm alarm;

      if (myLineStatusUpdateAlarms.containsKey(document)) {
        alarm = myLineStatusUpdateAlarms.get(document);
        alarm.cancelAllRequests();
      }
      else {
        alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
        myLineStatusUpdateAlarms.put(document, alarm);
      }

      final LineStatusTracker tracker = createTrackerForDocument(document);

      alarm.addRequest(new Runnable() {
        public void run() {
          try {
            alarm.cancelAllRequests();
            if (!virtualFile.isValid()) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("installTracker() for file " + virtualFile.getPath() + " failed: virtual file not valid");
              }
              return;
            }
            final String lastUpToDateContent = myVcsManager.getBaseVersionContent(virtualFile);
            if (lastUpToDateContent == null) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("installTracker() for file " + virtualFile.getPath() + " failed: no up to date content");
              }
              return;
            }
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (!myProject.isDisposed()) {
                  synchronized (TRACKERS_LOCK) {
                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                      public void run() {
                        if (LOG.isDebugEnabled()) {
                          LOG.debug("initializing tracker for file " + virtualFile.getPath());
                        }
                        tracker.initialize(lastUpToDateContent);
                      }
                    });
                  }
                }
              }
            });
          }
          finally {
            myLineStatusUpdateAlarms.remove(document);
          }
        }
      }, 10);
    }

  }

  private void resetTrackersForOpenFiles() {
    synchronized (TRACKERS_LOCK) {
      final VirtualFile[] openFiles = FileEditorManager.getInstance(myProject).getOpenFiles();
      for(VirtualFile openFile: openFiles) {
        resetTracker(openFile);
      }
    }
  }

  private class MyFileStatusListener implements FileStatusListener {
    public void fileStatusesChanged() {
      if (myProject.isDisposed()) return;
      LOG.debug("LineStatusTrackerManager: fileStatusesChanged");
      resetTrackersForOpenFiles();
    }

    public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
      resetTracker(virtualFile);
    }
  }

  private class MyEditorFactoryListener extends EditorFactoryAdapter {
    public void editorCreated(EditorFactoryEvent event) {
      Editor editor = event.getEditor();
      Document document = editor.getDocument();
      VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      installTracker(virtualFile, document);
    }

    public void editorReleased(EditorFactoryEvent event) {
      final Editor editor = event.getEditor();
      final Document doc = editor.getDocument();
      final Editor[] editors = event.getFactory().getEditors(doc, myProject);
      if (editors.length == 0) {
        releaseTracker(doc);
      }
    }
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    public void beforeContentsChange(VirtualFileEvent event) {
      if (event.isFromRefresh()) {
        resetTracker(event.getFile());
      }
    }
  }
}