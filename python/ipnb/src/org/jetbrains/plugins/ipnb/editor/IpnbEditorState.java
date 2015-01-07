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
package org.jetbrains.plugins.ipnb.editor;

import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;

final class IpnbEditorState implements FileEditorState{
  private final transient long myDocumentModificationStamp; // should not be serialized
  private int mySelectedIndex = 0;

  public IpnbEditorState(final long modificationStamp, int selectedComponentIndex) {
    myDocumentModificationStamp = modificationStamp;
    mySelectedIndex = selectedComponentIndex;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof IpnbEditorState)) return false;

    final IpnbEditorState state = (IpnbEditorState)o;
    if (mySelectedIndex == -1) return true;
    return myDocumentModificationStamp == state.myDocumentModificationStamp;
  }

  public void setSelectedIndex(int selectedIndex) {
    mySelectedIndex = selectedIndex;
  }

  public int getSelectedIndex() {
    return mySelectedIndex;
  }

  public int hashCode(){
    return (int)(myDocumentModificationStamp ^ (myDocumentModificationStamp >>> 32));
  }

  public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
    return otherState instanceof IpnbEditorState;
  }
}
