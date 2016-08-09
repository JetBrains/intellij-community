/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Dmitry Batkovich
 */
class ResourceBundleEditorFileListener extends VirtualFileAdapter {
  private final ResourceBundleEditor myEditor;
  private final MyVfsEventsProcessor myEventsProcessor;
  private final Project myProject;

  public ResourceBundleEditorFileListener(ResourceBundleEditor editor) {
    myEditor = editor;
    myEventsProcessor = new MyVfsEventsProcessor();
    myProject = myEditor.getResourceBundle().getProject();
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
    private final MergingUpdateQueue myMergeQueue =
      new MergingUpdateQueue("rbe.vfs.listener.queue", 200, true, myEditor.getComponent(), myEditor, myEditor.getComponent(), false) {
        @Override
        protected void execute(@NotNull Update[] updates) {
          final ReadTask task = new ReadTask() {
            private final EventWithType[] myEvents =
              Arrays.stream(updates).map(u -> (EventWithType)u.getEqualityObjects()[0]).toArray(EventWithType[]::new);

            @Nullable
            @Override
            public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
              if (!myEditor.isValid()) return null;
              Runnable toDo = null;
              for (EventWithType e : myEvents) {
                NotNullLazyValue<Set<VirtualFile>> resourceBundleAsSet = new NotNullLazyValue<Set<VirtualFile>>() {
                  @NotNull
                  @Override
                  protected Set<VirtualFile> compute() {
                    return myEditor.getResourceBundle().getPropertiesFiles().stream().map(PropertiesFile::getVirtualFile)
                      .collect(Collectors.toSet());
                  }
                };
                if (e.getType() == EventType.FILE_DELETED || (e.getType() == EventType.PROPERTY_CHANGED &&
                                                              ((VirtualFilePropertyEvent)e.getEvent()).getPropertyName().equals(VirtualFile.PROP_NAME))) {
                  if (myEditor.getTranslationEditors().containsKey(e.getEvent().getFile())) {
                    int validFilesCount = 0;
                    for (PropertiesFile file : myEditor.getResourceBundle().getPropertiesFiles()) {
                      if (file.getContainingFile().isValid()) {
                        validFilesCount ++;
                      }
                      if (validFilesCount == 2) {
                        break;
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
                }
                else if (e.getType() == EventType.FILE_CREATED) {
                  if (resourceBundleAsSet.getValue().contains(e.getEvent().getFile())) {
                    toDo = myEditor::recreateEditorsPanel;
                    break;
                  }
                }
                else if (e.getType() == EventType.PROPERTY_CHANGED &&
                    ((VirtualFilePropertyEvent)e.getEvent()).getPropertyName().equals(VirtualFile.PROP_WRITABLE)) {
                  if (myEditor.getTranslationEditors().containsKey(e.getEvent().getFile())) {
                    if (toDo == null) {
                      toDo = new SetViewerPropertyRunnable();
                    }
                    if (toDo instanceof SetViewerPropertyRunnable) {
                      ((SetViewerPropertyRunnable)toDo)
                        .addFile(e.getEvent().getFile(), !(Boolean)((VirtualFilePropertyEvent)e.getEvent()).getNewValue());
                    } else {
                      toDo = myEditor::recreateEditorsPanel;
                      break;
                    }
                  }
                }
                else {
                  if (myEditor.getTranslationEditors().containsKey(e.getEvent().getFile())) {
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
              for (EventWithType event : myEvents) {
                queue(new MyUpdate(event));
              }
            }
          };
          ProgressIndicatorUtils.scheduleWithWriteActionPriority(task);
        }
      };

    public void queue(VirtualFileEvent event, EventType type) {
      myMergeQueue.queue(new MyUpdate(new EventWithType(type, event)));
    }

    private class MyUpdate extends Update {
      public MyUpdate(EventWithType identity) {
        super(identity);
      }

      @Override
      public void run() {
        throw new IllegalStateException();
      }
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
    private final EventType myType;
    private final VirtualFileEvent myEvent;

    private EventWithType(EventType type, VirtualFileEvent event) {
      myType = type;
      myEvent = event;
    }

    public EventType getType() {
      return myType;
    }

    public VirtualFileEvent getEvent() {
      return myEvent;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      EventWithType type = (EventWithType)o;

      if (myType != type.myType) return false;
      if (!myEvent.equals(type.myEvent)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myType.hashCode();
      result = 31 * result + myEvent.hashCode();
      return result;
    }
  }
}
