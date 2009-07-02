package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.vcs.annotate.AnnotationSource;

import java.awt.*;
import java.util.List;

public class AnnotationGutterLineConvertorProxy implements ActiveAnnotationGutter {
  private final UpToDateLineNumberProvider myGetUpToDateLineNumber;
  private final ActiveAnnotationGutter myDelegate;

  public AnnotationGutterLineConvertorProxy(final UpToDateLineNumberProvider getUpToDateLineNumber, final ActiveAnnotationGutter delegate) {
    myGetUpToDateLineNumber = getUpToDateLineNumber;
    myDelegate = delegate;
  }

  public String getLineText(int line, Editor editor) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
    if (currentLine == UpToDateLineNumberProvider.ABSENT_LINE_NUMBER) return "";
    return myDelegate.getLineText(currentLine, editor);
  }

  public String getToolTip(int line, Editor editor) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
    if (currentLine == UpToDateLineNumberProvider.ABSENT_LINE_NUMBER) return "";
    return myDelegate.getToolTip(currentLine, editor);
  }

  public EditorFontType getStyle(int line, Editor editor) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
    if (currentLine == UpToDateLineNumberProvider.ABSENT_LINE_NUMBER) return EditorFontType.PLAIN;
    return myDelegate.getStyle(currentLine, editor);
  }

  public ColorKey getColor(int line, Editor editor) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
    if (currentLine == UpToDateLineNumberProvider.ABSENT_LINE_NUMBER) return AnnotationSource.LOCAL.getColor();
    return myDelegate.getColor(currentLine, editor);
  }

  public List<AnAction> getPopupActions(Editor editor) {
    return myDelegate.getPopupActions(editor);
  }

  public void gutterClosed() {
    myDelegate.gutterClosed();
  }

  public void doAction(int lineNum) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(lineNum);
    if (currentLine == UpToDateLineNumberProvider.ABSENT_LINE_NUMBER) return;
    myDelegate.doAction(currentLine);
  }

  public Cursor getCursor(int lineNum) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(lineNum);
    if (currentLine == UpToDateLineNumberProvider.ABSENT_LINE_NUMBER) return Cursor.getDefaultCursor();
    return myDelegate.getCursor(currentLine);
  }
}
