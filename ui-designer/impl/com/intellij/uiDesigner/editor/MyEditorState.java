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

  public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
    return otherState instanceof MyEditorState;
  }
}
