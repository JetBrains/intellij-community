/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class DesignerEditorState implements FileEditorState {
  private static final String DESIGNER_ZOOM = "ui-designer-zoom";

  private final long myModificationStamp;
  private final double myZoom;

  public DesignerEditorState(VirtualFile file, double zoom) {
    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    myModificationStamp = document != null ? document.getModificationStamp() : file.getModificationStamp();
    myZoom = zoom;
  }

  public double getZoom() {
    return myZoom;
  }

  @Override
  public int hashCode() {
    int A = (int)(myModificationStamp ^ (myModificationStamp >>> 32));
    long B = Double.doubleToLongBits(myZoom);
    return 31 * A + (int)(B ^ (B >>> 32));
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object instanceof DesignerEditorState) {
      DesignerEditorState state = (DesignerEditorState)object;
      return myModificationStamp == state.myModificationStamp && myZoom == state.myZoom;
    }
    return false;
  }

  @Override
  public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
    return otherState instanceof DesignerEditorState;
  }

  /**
   * @see com.intellij.openapi.fileEditor.FileEditorProvider#readState(org.jdom.Element, com.intellij.openapi.project.Project, com.intellij.openapi.vfs.VirtualFile)
   */
  @NotNull
  public static FileEditorState readState(@NotNull Element sourceElement, @NotNull VirtualFile file, double defaultZoom) {
    double zoom = defaultZoom;

    try {
      zoom = Double.parseDouble(sourceElement.getAttributeValue(DESIGNER_ZOOM));
    }
    catch (Throwable e) {
      // ignore
    }

    return new DesignerEditorState(file, zoom);
  }

  /**
   * @see com.intellij.openapi.fileEditor.FileEditorProvider#writeState(com.intellij.openapi.fileEditor.FileEditorState, com.intellij.openapi.project.Project, org.jdom.Element)
   */
  public static void writeState(@NotNull FileEditorState state, @NotNull Element targetElement) {
    targetElement.setAttribute(DESIGNER_ZOOM, Double.toString(((DesignerEditorState)state).getZoom()));
  }
}