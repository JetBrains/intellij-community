/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.editor;

import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

final class MyEditorState implements FileEditorState{
  private final transient long myDocumentModificationStamp; // should not be serialized
  private final String[] mySelectedComponentIds;

  public MyEditorState(final long modificationStamp, @NotNull final String[] selectedComponents){
    myDocumentModificationStamp = modificationStamp;
    mySelectedComponentIds = selectedComponents;
  }

  public String[] getSelectedComponentIds(){
    return mySelectedComponentIds;
  }

  public boolean equals(final Object o){
    if (this == o) return true;
    if (!(o instanceof MyEditorState)) return false;

    final MyEditorState state = (MyEditorState)o;

    if (myDocumentModificationStamp != state.myDocumentModificationStamp) return false;
    if (!Arrays.equals(mySelectedComponentIds, state.mySelectedComponentIds)) return false;

    return true;
  }

  public int hashCode(){
    return (int)(myDocumentModificationStamp ^ (myDocumentModificationStamp >>> 32));
  }

  @Override
  public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
    return otherState instanceof MyEditorState;
  }
}
