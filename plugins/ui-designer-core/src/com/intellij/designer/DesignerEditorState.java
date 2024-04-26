// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    int A = Long.hashCode(myModificationStamp);
    return 31 * A + Double.hashCode(myZoom);
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object instanceof DesignerEditorState state) {
      return myModificationStamp == state.myModificationStamp && myZoom == state.myZoom;
    }
    return false;
  }

  @Override
  public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
    return otherState instanceof DesignerEditorState;
  }

  /**
   * @see com.intellij.openapi.fileEditor.FileEditorProvider#readState(Element, com.intellij.openapi.project.Project, VirtualFile)
   */
  @NotNull
  public static FileEditorState readState(@Nullable Element sourceElement, @NotNull VirtualFile file, double defaultZoom) {
    double zoom = defaultZoom;
    if (sourceElement != null) {
      try {
        zoom = Double.parseDouble(sourceElement.getAttributeValue(DESIGNER_ZOOM));
      }
      catch (Throwable ignored) {
      }
    }
    return new DesignerEditorState(file, zoom);
  }

  /**
   * @see com.intellij.openapi.fileEditor.FileEditorProvider#writeState(FileEditorState, com.intellij.openapi.project.Project, Element)
   */
  public static void writeState(@NotNull FileEditorState state, @NotNull Element targetElement) {
    targetElement.setAttribute(DESIGNER_ZOOM, Double.toString(((DesignerEditorState)state).getZoom()));
  }
}