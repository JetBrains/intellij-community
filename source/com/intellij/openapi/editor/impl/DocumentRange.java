package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditReadOnlyListener;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;

/**
 * @author Alexey
 */
public class DocumentRange extends UserDataHolderBase implements DocumentEx {
  private final DocumentEx myDelegate;
  private RangeMarker myRange;

  public DocumentRange(DocumentEx delegate, TextRange range) {
    myDelegate = delegate;
    myRange = delegate.createRangeMarker(range);
    myRange.setGreedyToLeft(true);
    myRange.setGreedyToRight(true);
  }

  public int getLineCount() {
    return myDelegate.getLineNumber(myRange.getEndOffset()) - myDelegate.getLineNumber(myRange.getStartOffset()) + 1;
  }

  public int getLineStartOffset(int line) {
    return Math.max(0, myDelegate.getLineStartOffset(myDelegate.getLineNumber(myRange.getStartOffset()) + line) - myRange.getStartOffset());
  }

  public int getLineEndOffset(int line) {
    int myLineInParent = myDelegate.getLineNumber(myRange.getStartOffset()) + line;
    return Math.min(myRange.getEndOffset(), myDelegate.getLineEndOffset(myLineInParent)) - myRange.getStartOffset();
  }

  public String getText() {
    return myDelegate.getText().substring(myRange.getStartOffset(), myRange.getEndOffset());
  }

  public CharSequence getCharsSequence() {
    return myDelegate.getCharsSequence().subSequence(myRange.getStartOffset(), myRange.getEndOffset());
  }

  public char[] getChars() {
    return CharArrayUtil.fromSequence(getText());
  }

  public int getTextLength() {
    return myRange.getEndOffset()-myRange.getStartOffset();
  }

  public int getLineNumber(final int offset) {
    return myDelegate.getLineNumber(offset+myRange.getStartOffset()) - myDelegate.getLineNumber(myRange.getStartOffset());
  }

  public void insertString(final int offset, final CharSequence s) {
    myDelegate.insertString(offset + myRange.getStartOffset(), s);
  }

  public void deleteString(final int startOffset, final int endOffset) {
    myDelegate.deleteString(startOffset + myRange.getStartOffset(), endOffset + myRange.getStartOffset());
  }

  public void replaceString(final int startOffset, final int endOffset, final CharSequence s) {
    myDelegate.replaceString(startOffset + myRange.getStartOffset(), endOffset + myRange.getStartOffset(), s);
  }

  public boolean isWritable() {
    return myDelegate.isWritable();
  }

  public long getModificationStamp() {
    return myDelegate.getModificationStamp();
  }

  public void fireReadOnlyModificationAttempt() {
    myDelegate.fireReadOnlyModificationAttempt();
  }

  public void addDocumentListener(final DocumentListener listener) {
    myDelegate.addDocumentListener(listener);
  }

  public void removeDocumentListener(final DocumentListener listener) {
    myDelegate.removeDocumentListener(listener);
  }

  public RangeMarker createRangeMarker(final int startOffset, final int endOffset) {
    return myDelegate.createRangeMarker(startOffset, endOffset);
  }

  public RangeMarker createRangeMarker(final int startOffset, final int endOffset, final boolean surviveOnExternalChange) {
    return myDelegate.createRangeMarker(startOffset, endOffset, surviveOnExternalChange);
  }

  public MarkupModel getMarkupModel() {
    return myDelegate.getMarkupModel();
  }

  @NotNull
  public MarkupModel getMarkupModel(final Project project) {
    return myDelegate.getMarkupModel(project);
  }

  public void addPropertyChangeListener(final PropertyChangeListener listener) {
    myDelegate.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(final PropertyChangeListener listener) {
    myDelegate.removePropertyChangeListener(listener);
  }

  public void setReadOnly(final boolean isReadOnly) {
    myDelegate.setReadOnly(isReadOnly);
  }

  public RangeMarker createGuardedBlock(final int startOffset, final int endOffset) {
    return myDelegate.createGuardedBlock(startOffset, endOffset);
  }

  public void removeGuardedBlock(final RangeMarker block) {
    myDelegate.removeGuardedBlock(block);
  }

  public RangeMarker getOffsetGuard(final int offset) {
    return myDelegate.getOffsetGuard(offset);
  }

  public RangeMarker getRangeGuard(final int start, final int end) {
    return myDelegate.getRangeGuard(start, end);
  }

  public void startGuardedBlockChecking() {
    myDelegate.startGuardedBlockChecking();
  }

  public void stopGuardedBlockChecking() {
    myDelegate.stopGuardedBlockChecking();
  }

  public void setCyclicBufferSize(final int bufferSize) {
    myDelegate.setCyclicBufferSize(bufferSize);
  }

  public void setText(final CharSequence text) {
    myDelegate.replaceString(myRange.getStartOffset(), myRange.getEndOffset(), text);
  }

  public RangeMarker createRangeMarker(final TextRange textRange) {
    return myDelegate.createRangeMarker(textRange);
  }

  public void stripTrailingSpaces(final boolean inChangedLinesOnly) {
    myDelegate.stripTrailingSpaces(inChangedLinesOnly);
  }

  public void setStripTrailingSpacesEnabled(final boolean isEnabled) {
    myDelegate.setStripTrailingSpacesEnabled(isEnabled);
  }

  public int getLineSeparatorLength(final int line) {
    return myDelegate.getLineSeparatorLength(line);
  }

  public LineIterator createLineIterator() {
    return myDelegate.createLineIterator();
  }

  public void setModificationStamp(final long modificationStamp) {
    myDelegate.setModificationStamp(modificationStamp);
  }

  public void addEditReadOnlyListener(final EditReadOnlyListener listener) {
    myDelegate.addEditReadOnlyListener(listener);
  }

  public void removeEditReadOnlyListener(final EditReadOnlyListener listener) {
    myDelegate.removeEditReadOnlyListener(listener);
  }

  public void replaceText(final CharSequence chars, final long newModificationStamp) {
    myDelegate.replaceText(chars, newModificationStamp);
  }

  public int getListenersCount() {
    return myDelegate.getListenersCount();
  }

  public void suppressGuardedExceptions() {
    myDelegate.suppressGuardedExceptions();
  }

  public void unSuppressGuardedExceptions() {
    myDelegate.unSuppressGuardedExceptions();
  }

  public boolean isInEventsHandling() {
    return myDelegate.isInEventsHandling();
  }

  public RangeMarker getTextRange() {
    return myRange;
  }

  public DocumentEx getDelegate() {
    return myDelegate;
  }
}