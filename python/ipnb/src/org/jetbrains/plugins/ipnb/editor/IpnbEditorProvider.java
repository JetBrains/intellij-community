// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.IpnbFileType;

/**
 * @author traff
 */
public class IpnbEditorProvider implements FileEditorProvider, DumbAware {
  @NonNls private static final String SELECTED_CELL = "selected";
  @NonNls private static final String ID = "id";

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return file.getFileType() == IpnbFileType.INSTANCE;
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new IpnbFileEditor(project, file);
  }

  @NotNull
  @Override
  public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    final IpnbEditorState state = new IpnbEditorState(-1, 0);
    final Element child = sourceElement.getChild(SELECTED_CELL);
    state.setSelectedIndex(child == null ? 0 : StringUtil.parseInt(child.getAttributeValue(ID), 0));
    return state;
  }

  @Override
  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
    IpnbEditorState editorState = (IpnbEditorState)state;
    final int id = editorState.getSelectedIndex();
    final Element element = new Element(SELECTED_CELL);
    element.setAttribute(ID, String.valueOf(id));
    targetElement.addContent(element);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return "ipnb-editor";
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
