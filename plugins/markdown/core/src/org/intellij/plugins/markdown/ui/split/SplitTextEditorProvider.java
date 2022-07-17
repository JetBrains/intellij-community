// Copyright 2000-2018 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.intellij.plugins.markdown.ui.split;

import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SplitTextEditorProvider implements AsyncFileEditorProvider, DumbAware {

  protected static final String FIRST_EDITOR = "first_editor";
  protected static final String SECOND_EDITOR = "second_editor";
  protected static final String SPLIT_LAYOUT = "split_layout";

  @NotNull
  protected final FileEditorProvider myFirstProvider;
  @NotNull
  protected final FileEditorProvider mySecondProvider;

  @NotNull
  private final String myEditorTypeId;

  public SplitTextEditorProvider(@NotNull FileEditorProvider firstProvider, @NotNull FileEditorProvider secondProvider) {
    myFirstProvider = firstProvider;
    mySecondProvider = secondProvider;

    myEditorTypeId = "split-provider[" + myFirstProvider.getEditorTypeId() + ";" + mySecondProvider.getEditorTypeId() + "]";
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return myFirstProvider.accept(project, file) && mySecondProvider.accept(project, file);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return createEditorAsync(project, file).build();
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return myEditorTypeId;
  }

  @NotNull
  @Override
  public Builder createEditorAsync(@NotNull final Project project, @NotNull final VirtualFile file) {
    final Builder firstBuilder = getBuilderFromEditorProvider(myFirstProvider, project, file);
    final Builder secondBuilder = getBuilderFromEditorProvider(mySecondProvider, project, file);

    return new Builder() {
      @Override
      public FileEditor build() {
        return createSplitEditor(firstBuilder.build(), secondBuilder.build());
      }
    };
  }

  @Nullable
  protected FileEditorState readFirstProviderState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    final var child = sourceElement.getChild(FIRST_EDITOR);
    if (child != null) {
      return myFirstProvider.readState(child, project, file);
    }
    return null;
  }

  @Nullable
  protected FileEditorState readSecondProviderState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    final var child = sourceElement.getChild(SECOND_EDITOR);
    if (child != null) {
      return mySecondProvider.readState(child, project, file);
    }
    return null;
  }

  @Nullable
  protected String readSplitLayoutState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    final var attribute = sourceElement.getAttribute(SPLIT_LAYOUT);
    String layoutName = null;
    if (attribute != null) {
      layoutName = attribute.getValue();
    }
    return layoutName;
  }

  @NotNull
  @Override
  public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    final var firstState = readFirstProviderState(sourceElement, project, file);
    final var secondState = readSecondProviderState(sourceElement, project, file);
    final var layoutName = readSplitLayoutState(sourceElement, project, file);
    return new SplitFileEditor.MyFileEditorState(layoutName, firstState, secondState);
  }

  protected void writeFirstProviderState(@Nullable FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
    final var child = new Element(FIRST_EDITOR);
    if (state != null) {
      myFirstProvider.writeState(state, project, child);
      targetElement.addContent(child);
    }
  }

  protected void writeSecondProviderState(@Nullable FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
    final var child = new Element(SECOND_EDITOR);
    if (state != null) {
      mySecondProvider.writeState(state, project, child);
      targetElement.addContent(child);
    }
  }

  protected void writeSplitLayoutState(@Nullable String splitLayout, @NotNull Project project, @NotNull Element targetElement) {
    if (splitLayout != null) {
      targetElement.setAttribute(SPLIT_LAYOUT, splitLayout);
    }
  }

  @Override
  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
    if (!(state instanceof SplitFileEditor.MyFileEditorState)) {
      return;
    }
    final var compositeState = (SplitFileEditor.MyFileEditorState)state;
    writeFirstProviderState(compositeState.getFirstState(), project, targetElement);
    writeSecondProviderState(compositeState.getSecondState(), project, targetElement);
    writeSplitLayoutState(compositeState.getSplitLayout(), project, targetElement);
  }

  protected abstract FileEditor createSplitEditor(@NotNull FileEditor firstEditor, @NotNull FileEditor secondEditor);

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }

  @NotNull
  public static Builder getBuilderFromEditorProvider(@NotNull final FileEditorProvider provider,
                                                     @NotNull final Project project,
                                                     @NotNull final VirtualFile file) {
    if (provider instanceof AsyncFileEditorProvider) {
      return ((AsyncFileEditorProvider)provider).createEditorAsync(project, file);
    }
    else {
      return new Builder() {
        @Override
        public FileEditor build() {
          return provider.createEditor(project, file);
        }
      };
    }
  }
}
