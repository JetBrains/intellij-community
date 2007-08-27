package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.textarea.TextComponentEditor;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.JTextComponent;

/**
 * @author yole
 */
public abstract class TextComponentEditorAction extends EditorAction {
  protected TextComponentEditorAction(final EditorActionHandler defaultHandler) {
    super(defaultHandler);
    setEnabledInModalContext(true);
  }

  @Nullable
  protected Editor getEditor(final DataContext dataContext) {
    return getEditorFromContext(dataContext);
  }

  @Nullable
  public static Editor getEditorFromContext(final DataContext dataContext) {
    final Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (editor != null) return editor;
    final Object data = DataKeys.CONTEXT_COMPONENT.getData(dataContext);
    if (data instanceof JTextComponent) {
      return new TextComponentEditor(DataKeys.PROJECT.getData(dataContext), (JTextComponent) data);
    }
    return null;
  }
}