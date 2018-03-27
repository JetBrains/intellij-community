// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Dmitry Batkovich
 */
class ResourceBundleEditorFileListener implements VirtualFileListener {
  private static final Logger LOG = Logger.getInstance(ResourceBundleEditorFileListener.class);
  private static final Update FORCE_UPDATE = new Update("FORCE_UPDATE") {
    @Override
    public void run() {
      throw new IllegalStateException();
    }
  };

  private final ResourceBundleEditor myEditor;
  private final MyVfsEventsProcessor myEventsProcessor;
  private final Project myProject;

  public ResourceBundleEditorFileListener(ResourceBundleEditor editor) {
    myEditor = editor;
    myEventsProcessor = new MyVfsEventsProcessor();
    myProject = myEditor.getResourceBundle().getProject();
  }

  public void flush() {
    FileDocumentManager.getInstance().saveAllDocuments();
    myEventsProcessor.flush();
  }

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event) {
    myEventsProcessor.queue(event, EventType.FILE_CREATED);
  }

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent event) {
    myEventsProcessor.queue(event, EventType.FILE_DELETED);
  }

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    myEventsProcessor.queue(event, EventType.PROPERTY_CHANGED);
  }

  @Override
  public void contentsChanged(@NotNull VirtualFileEvent event) {
    myEventsProcessor.queue(event, EventType.CONTENT_CHANGED);
  }

  private class MyVfsEventsProcessor {
    private final AtomicReference<Set<EventWithType>> myEventQueue = new AtomicReference<>(ContainerUtil.newConcurrentSet());

    private final MergingUpdateQueue myUpdateQueue =
      new MergingUpdateQueue("rbe.vfs.listener.queue", 200, true, myEditor.getComponent(), myEditor, myEditor.getComponent(), false) {
        @Override
        protected void execute(@NotNull Update[] updates) {
          final ReadTask task = new ReadTask() {
            final Set<EventWithType> myEvents = myEventQueue.getAndSet(ContainerUtil.newConcurrentSet());

            @Nullable
            @Override
            public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
              if (!myEditor.isValid()) return null;
              Runnable toDo = null;
              NotNullLazyValue<Set<VirtualFile>> resourceBundleAsSet = new NotNullLazyValue<Set<VirtualFile>>() {
                @NotNull
                @Override
                protected Set<VirtualFile> compute() {
                  return myEditor.getResourceBundle().getPropertiesFiles().stream().map(PropertiesFile::getVirtualFile)
                    .collect(Collectors.toSet());
                }
              };
              for (EventWithType e : myEvents) {
                if (e.getType() == EventType.FILE_DELETED || (e.getType() == EventType.PROPERTY_CHANGED && e.getPropertyName().equals(VirtualFile.PROP_NAME))) {
                  if (myEditor.getTranslationEditors().containsKey(e.getFile())) {
                    int validFilesCount = 0;
                    ResourceBundle bundle = myEditor.getResourceBundle();
                    if (bundle.isValid()) {
                      for (PropertiesFile file : bundle.getPropertiesFiles()) {
                        if (file.getContainingFile().isValid()) {
                          validFilesCount ++;
                        }
                        if (validFilesCount == 2) {
                          break;
                        }
                      }
                    }
                    if (validFilesCount > 1) {
                      toDo = myEditor::recreateEditorsPanel;
                    } else {
                      toDo = () -> {
                        final FileEditorManagerEx fileEditorManager = (FileEditorManagerEx)FileEditorManager.getInstance(myProject);
                        final VirtualFile file = fileEditorManager.getFile(myEditor);
                        if (file != null) {
                          fileEditorManager.closeFile(file);
                        }
                      };
                    }
                    break;
                  }
                  else if (resourceBundleAsSet.getValue().contains(e.getFile())) {
                    //new file in bundle
                    toDo = myEditor::recreateEditorsPanel;
                    break;
                  }
                }
                else if (e.getType() == EventType.FILE_CREATED) {
                  if (resourceBundleAsSet.getValue().contains(e.getFile())) {
                    toDo = myEditor::recreateEditorsPanel;
                    break;
                  }
                }
                else if (e.getType() == EventType.PROPERTY_CHANGED && e.getPropertyName().equals(VirtualFile.PROP_WRITABLE)) {
                  if (myEditor.getTranslationEditors().containsKey(e.getFile())) {
                    if (toDo == null) {
                      toDo = new SetViewerPropertyRunnable();
                    }
                    if (toDo instanceof SetViewerPropertyRunnable) {
                      ((SetViewerPropertyRunnable)toDo).addFile(e.getFile(), !(boolean)e.getPropertyNewValue());
                    } else {
                      toDo = myEditor::recreateEditorsPanel;
                      break;
                    }
                  }
                }
                else {
                  if (myEditor.getTranslationEditors().containsKey(e.getFile())) {
                    if ((toDo instanceof SetViewerPropertyRunnable)) {
                      toDo = myEditor::recreateEditorsPanel;
                      break;
                    }
                    else if (toDo == null) {
                      toDo = () -> myEditor.updateEditorsFromProperties(true);
                    }
                  }
                }
              }

              if (toDo == null) {
                return null;
              }
              else {
                Runnable toDoCopy = toDo;
                return new Continuation(() -> {
                  if (myEditor.isValid()) {
                    toDoCopy.run();
                  }
                }, ModalityState.NON_MODAL);
              }
            }

            @Override
            public void onCanceled(@NotNull ProgressIndicator indicator) {
              myEventQueue.updateAndGet(s -> {
                s.addAll(myEvents);
                return s;
              });
              myUpdateQueue.queue(FORCE_UPDATE);
            }
          };
          ProgressIndicatorUtils.scheduleWithWriteActionPriority(task);
        }
      };

    public void queue(VirtualFileEvent event, EventType type) {
      myEventQueue.updateAndGet(s -> {
        s.add(new EventWithType(type, event));
        return s;
      });
      myUpdateQueue.queue(FORCE_UPDATE);
    }

    public void flush() {
      myUpdateQueue.flush();
    }
  }

  private class SetViewerPropertyRunnable implements Runnable {
    private final List<VirtualFile> myFiles = new ArrayList<>();
    private final List<Boolean> myIsViewer = new ArrayList<>();

    public void addFile(VirtualFile virtualFile, boolean isViewer) {
      myFiles.add(virtualFile);
      myIsViewer.add(isViewer);
    }

    @Override
    public void run() {
      for (int i = 0; i < myFiles.size(); i++) {
        VirtualFile file = myFiles.get(i);
        final Boolean viewer = myIsViewer.get(i);
        final EditorEx editor = myEditor.getTranslationEditors().get(file);
        if (editor != null) {
          editor.setViewer(viewer);
        }
      }
    }
  }

  private enum EventType {
    FILE_CREATED,
    FILE_DELETED,
    CONTENT_CHANGED, PROPERTY_CHANGED
  }

  private static class EventWithType {
    @NotNull
    private final EventType myType;
    @NotNull
    private final VirtualFile myFile;
    private final String myPropertyName;
    private final Object myPropertyNewValue;

    private EventWithType(@NotNull EventType type, @NotNull VirtualFileEvent event) {
      myType = type;
      myFile = event.getFile();
      if (type == EventType.PROPERTY_CHANGED) {
        myPropertyName = ((VirtualFilePropertyEvent)event).getPropertyName();
        myPropertyNewValue = ((VirtualFilePropertyEvent)event).getNewValue();
      } else {
        myPropertyName = null;
        myPropertyNewValue = null;
      }
    }

    @NotNull
    public EventType getType() {
      return myType;
    }

    @NotNull
    public VirtualFile getFile() {
      return myFile;
    }

    @NotNull
    public String getPropertyName() {
      LOG.assertTrue(myType == EventType.PROPERTY_CHANGED, "Unexpected event type: " + myType);
      return ObjectUtils.notNull(myPropertyName);
    }

    @NotNull
    public Object getPropertyNewValue() {
      LOG.assertTrue(myType == EventType.PROPERTY_CHANGED, "Unexpected event type: " + myType);
      return ObjectUtils.notNull(myPropertyNewValue);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      EventWithType type = (EventWithType)o;

      if (myType != type.myType) return false;
      if (!myFile.equals(type.myFile)) return false;
      if (!Objects.equals(myPropertyName, type.myPropertyName)) return false;
      if (!Objects.equals(myPropertyNewValue, type.myPropertyNewValue)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myType.hashCode();
      result = 31 * result + myFile.hashCode();
      result = 31 * result + (myPropertyName != null ? myPropertyName.hashCode() : 0);
      result = 31 * result + (myPropertyNewValue != null ? myPropertyNewValue.hashCode() : 0);
      return result;
    }
  }
}
