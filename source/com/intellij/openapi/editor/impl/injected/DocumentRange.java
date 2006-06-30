package com.intellij.openapi.editor.impl.injected;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditReadOnlyListener;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.Disposable;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;

/**
 * @author Alexey
 */
public class DocumentRange extends UserDataHolderBase implements DocumentEx {
  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.editor.impl.injected.DocumentRangee");
  private final DocumentEx myDelegate;
  private final RangeMarker myHostRange;
  private final String myPrefix;
  private final String mySuffix;
  private final int myPrefixLineCount;
  private final int mySuffixLineCount;


  public DocumentRange(DocumentEx delegate, TextRange range, String prefix,String suffix) {
    myDelegate = delegate;
    myPrefix = prefix;
    mySuffix = suffix;
    myHostRange = delegate.createRangeMarker(range);
    myHostRange.setGreedyToLeft(true);
    myHostRange.setGreedyToRight(true);
    myPrefixLineCount = Math.max(1, new DocumentImpl(myPrefix).getLineCount());
    mySuffixLineCount = Math.max(1, new DocumentImpl(mySuffix).getLineCount());
  }

  public int getLineCount() {
    return
    myPrefixLineCount -1+
    myDelegate.getLineNumber(myHostRange.getEndOffset()) - myDelegate.getLineNumber(myHostRange.getStartOffset()) + 1 +
    mySuffixLineCount-1
      ;
  }

  public int getLineStartOffset(int line) {
    return new DocumentImpl(getText()).getLineStartOffset(line);
  }

  public int getLineEndOffset(int line) {
    if (line==0 && myPrefix.length()==0) return getTextLength();
    return new DocumentImpl(getText()).getLineEndOffset(line);
  }

  public String getText() {
    return myPrefix+
           myDelegate.getText().substring(myHostRange.getStartOffset(), myHostRange.getEndOffset())
           +mySuffix
      ;
  }

  public CharSequence getCharsSequence() {
    return getText();
  }

  public char[] getChars() {
    return CharArrayUtil.fromSequence(getText());
  }

  public int getTextLength() {
    return myPrefix.length() + myHostRange.getEndOffset()- myHostRange.getStartOffset() +mySuffix.length();
  }

  public int getLineNumber(final int offset) {
    return hostToInjectedLine(myDelegate.getLineNumber(injectedToHost(offset)));
  }

  public void insertString(final int offset, final CharSequence s) {
    LOG.assertTrue(offset >= myPrefix.length());
    LOG.assertTrue(offset <= getTextLength() - mySuffix.length());
    myDelegate.insertString(injectedToHost(offset), s);
  }

  public void deleteString(final int startOffset, final int endOffset) {
    LOG.assertTrue(startOffset >= myPrefix.length());
    LOG.assertTrue(startOffset <= getTextLength() - mySuffix.length());
    LOG.assertTrue(endOffset >= myPrefix.length());
    LOG.assertTrue(endOffset <= getTextLength() - mySuffix.length());
    myDelegate.deleteString(injectedToHost(startOffset), injectedToHost(endOffset));
  }

  public void replaceString(final int startOffset, final int endOffset, final CharSequence s) {
    LOG.assertTrue(startOffset >= myPrefix.length());
    LOG.assertTrue(startOffset <= getTextLength() - mySuffix.length());
    LOG.assertTrue(endOffset >= myPrefix.length());
    LOG.assertTrue(endOffset <= getTextLength() - mySuffix.length());
    myDelegate.replaceString(injectedToHost(startOffset), injectedToHost(endOffset), s);
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

  public void addDocumentListener(DocumentListener listener, Disposable parentDisposable) {
    myDelegate.addDocumentListener(listener, parentDisposable);
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
    return new MarkupModelDelegate((MarkupModelEx)myDelegate.getMarkupModel(project), this);
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
    LOG.assertTrue(text.toString().startsWith(myPrefix));
    LOG.assertTrue(text.toString().endsWith(mySuffix));
    myDelegate.replaceString(myHostRange.getStartOffset(), myHostRange.getEndOffset(), text.subSequence(myPrefix.length(), text.length()-mySuffix.length()));
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
    return myHostRange;
  }

  public DocumentEx getDelegate() {
    return myDelegate;
  }

  public String getPrefix() {
    return myPrefix;
  }

  public String getSuffix() {
    return mySuffix;
  }

  public int hostToInjected(int offset) {
    if (offset < getTextRange().getStartOffset()) return 0;
    if (offset >= getTextRange().getEndOffset()) return getTextRange().getEndOffset()-getTextRange().getStartOffset();
    return offset - getTextRange().getStartOffset() + getPrefix().length();
  }

  public int injectedToHost(int offset) {
    if (offset < myPrefix.length()) return getTextRange().getStartOffset();
    if (offset >= getTextLength() - mySuffix.length()) return getTextRange().getEndOffset();
    return offset + getTextRange().getStartOffset() - getPrefix().length();
  }

  public int injectedToHostLine(int line) {
    if (line < myPrefixLineCount) {
      return 0;
    }
    if (line > getLineCount()- mySuffixLineCount) {
      return getLineCount();
    }
    int hostLine = myDelegate.getLineNumber(myHostRange.getStartOffset()) + line - (myPrefixLineCount -1);
    return hostLine;
  }

  public int hostToInjectedLine(int hostLine) {
    return hostLine - myDelegate.getLineNumber(myHostRange.getStartOffset()) + (myPrefixLineCount-1);
  }

  public boolean isEditable(TextRange rangeToEdit) {
    return rangeToEdit.getStartOffset() >= myPrefix.length() && rangeToEdit.getEndOffset() <= getTextLength() - mySuffix.length();
  }
}