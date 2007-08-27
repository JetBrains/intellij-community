package com.intellij.openapi.editor.impl.injected;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;

/**
 * @author Alexey
 */
public class DocumentWindow extends UserDataHolderBase implements DocumentEx {
  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.editor.impl.injected.DocumentRangee");
  private final DocumentEx myDelegate;
  //sorted by startOffset
  private final RangeMarker[] myHostRanges;
  private final boolean myOneLine;
  private final String myPrefix;
  private final String mySuffix;
  private final int myPrefixLineCount;
  private final int mySuffixLineCount;

  public DocumentWindow(@NotNull DocumentEx delegate, boolean oneLine, @NotNull String prefix, @NotNull String suffix, @NotNull TextRange... ranges) {
    myDelegate = delegate;
    myOneLine = oneLine;
    myPrefix = prefix;
    mySuffix = suffix;
    myHostRanges = new RangeMarker[ranges.length];
    for (int i = 0; i < ranges.length; i++) {
      TextRange range = ranges[i];
      RangeMarker rangeMarker = delegate.createRangeMarker(range);
      rangeMarker.setGreedyToLeft(true);
      rangeMarker.setGreedyToRight(true);
      myHostRanges[i] = rangeMarker;
    }
    myPrefixLineCount = Math.max(1, new DocumentImpl(myPrefix).getLineCount());
    mySuffixLineCount = Math.max(1, new DocumentImpl(mySuffix).getLineCount());
  }

  @Terminal
  public int getLineCount() {
    return new DocumentImpl(getText()).getLineCount();
  }

  @Terminal
  public int getLineStartOffset(int line) {
    assert line >= 0 : line;
    return new DocumentImpl(getText()).getLineStartOffset(line);
  }

  @Terminal
  public int getLineEndOffset(int line) {
    if (line==0 && myPrefix.length()==0) return getTextLength();
    return new DocumentImpl(getText()).getLineEndOffset(line);
  }

  @Terminal
  public String getText() {
    StringBuilder text = new StringBuilder(myPrefix);
    String hostText = myDelegate.getText();
    for (RangeMarker hostRange : myHostRanges) {
      if (hostRange.isValid()) {
        text.append(hostText, hostRange.getStartOffset(), hostRange.getEndOffset());
      }
    }
    text.append(mySuffix);
    return text.toString();
  }

  public CharSequence getCharsSequence() {
    return getText();
  }

  public char[] getChars() {
    return CharArrayUtil.fromSequence(getText());
  }

  public int getTextLength() {
    int length = myPrefix.length() + mySuffix.length();
    for (RangeMarker hostRange : myHostRanges) {
      length += hostRange.getEndOffset() - hostRange.getStartOffset();
    }
    return length;
  }

  @Terminal
  public int getLineNumber(int offset) {
    if (offset < myPrefix.length()) return 0;
    offset -= myPrefix.length();
    int lineNumber = myPrefixLineCount - 1;
    String hostText = myDelegate.getText();
    for (RangeMarker currentRange : myHostRanges) {
      int length = currentRange.getEndOffset() - currentRange.getStartOffset();
      String rangeText = hostText.substring(currentRange.getStartOffset(), currentRange.getEndOffset());
      if (offset < length) {
        return lineNumber + StringUtil.getLineBreakCount(rangeText.substring(0, offset));
      }
      offset -= length;
      lineNumber += StringUtil.getLineBreakCount(rangeText);
    }
    lineNumber = getLineCount() - 1;
    return lineNumber < 0 ? 0 : lineNumber;
  }

  public int getHostNumber(int injectedOffset) {
    if (injectedOffset < myPrefix.length()) return -1;
    injectedOffset -= myPrefix.length();
    for (int i = 0; i < myHostRanges.length; i++) {
      RangeMarker currentRange = myHostRanges[i];
      int length = currentRange.getEndOffset() - currentRange.getStartOffset();
      if (injectedOffset < length) {
        return i;
      }
      injectedOffset -= length;
    }
    return -1;
  }
  public TextRange getHostRange(int hostOffset) {
    for (RangeMarker currentRange : myHostRanges) {
      TextRange textRange = new TextRange(currentRange.getStartOffset(), currentRange.getEndOffset());
      if (textRange.grown(1).contains(hostOffset)) return textRange;
    }
    return null;
  }

  public int getPrevHostsCombinedLength(int hostNumber) {
    int res = 0;
    for (int i = 0; i < hostNumber; i++) {
      RangeMarker currentRange = myHostRanges[i];
      int length = currentRange.getEndOffset() - currentRange.getStartOffset();
      res += length;
    }
    return res;
  }

  public void insertString(final int offset, CharSequence s) {
    LOG.assertTrue(offset >= myPrefix.length());
    LOG.assertTrue(offset <= getTextLength() - mySuffix.length());
    if (isOneLine()) {
      s = StringUtil.replace(s.toString(), "\n", "");
    }
    myDelegate.insertString(injectedToHost(offset), s);
  }

  public void deleteString(final int startOffset, final int endOffset) {
    LOG.assertTrue(startOffset >= myPrefix.length());
    LOG.assertTrue(startOffset <= getTextLength() - mySuffix.length());
    LOG.assertTrue(endOffset >= myPrefix.length());
    LOG.assertTrue(endOffset <= getTextLength() - mySuffix.length());
    //todo handle delete that span ranges

    myDelegate.deleteString(injectedToHost(startOffset), injectedToHost(endOffset));
  }

  public void replaceString(final int startOffset, final int endOffset, CharSequence s) {
    if (startOffset < myPrefix.length() || startOffset > getTextLength() - mySuffix.length() || endOffset < myPrefix.length() || endOffset > getTextLength() - mySuffix.length()) {
      LOG.assertTrue(s.equals(getText().substring(startOffset, endOffset)));
      return;
    }
    if (isOneLine()) {
      s = StringUtil.replace(s.toString(), "\n", "");
    }
    //LOG.assertTrue(startOffset >= myPrefix.length());
    //LOG.assertTrue(startOffset <= getTextLength() - mySuffix.length());
    //LOG.assertTrue(endOffset >= myPrefix.length());
    //LOG.assertTrue(endOffset <= getTextLength() - mySuffix.length());
    //todo handle delete that span ranges
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
    TextRange hostRange = injectedToHost(new TextRange(startOffset, endOffset));
    RangeMarker hostMarker = myDelegate.createRangeMarker(hostRange);
    return new RangeMarkerWindow(this, hostMarker);
  }

  public RangeMarker createRangeMarker(final int startOffset, final int endOffset, final boolean surviveOnExternalChange) {
    TextRange hostRange = injectedToHost(new TextRange(startOffset, endOffset));
    //todo persistent?
    return myDelegate.createRangeMarker(hostRange.getStartOffset(), hostRange.getEndOffset(), surviveOnExternalChange);
  }

  @SuppressWarnings({"deprecation"})
  public MarkupModel getMarkupModel() {
    return new MarkupModelWindow((MarkupModelEx)myDelegate.getMarkupModel(), this);
  }

  @NotNull
  public MarkupModel getMarkupModel(final Project project) {
    return new MarkupModelWindow((MarkupModelEx)myDelegate.getMarkupModel(project), this);
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
    TextRange hostRange = injectedToHost(new TextRange(startOffset, endOffset));
    return myDelegate.createGuardedBlock(hostRange.getStartOffset(), hostRange.getEndOffset());
  }

  public void removeGuardedBlock(final RangeMarker block) {
    myDelegate.removeGuardedBlock(block);
  }

  public RangeMarker getOffsetGuard(final int offset) {
    return myDelegate.getOffsetGuard(injectedToHost(offset));
  }

  public RangeMarker getRangeGuard(final int startOffset, final int endOffset) {
    TextRange hostRange = injectedToHost(new TextRange(startOffset, endOffset));

    return myDelegate.getRangeGuard(hostRange.getStartOffset(), hostRange.getEndOffset());
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

  public void setText(CharSequence text) {
    LOG.assertTrue(text.toString().startsWith(myPrefix));
    LOG.assertTrue(text.toString().endsWith(mySuffix));
    if (isOneLine()) {
      text = StringUtil.replace(text.toString(), "\n", "");
    }

    String[] changes = calculateMinEditSequence(text.toString());
    assert changes.length == myHostRanges.length;
    for (int i = 0; i < changes.length; i++) {
      String change = changes[i];
      RangeMarker hostRange = myHostRanges[i];
      myDelegate.replaceString(hostRange.getStartOffset(), hostRange.getEndOffset(), change);
    }
  }

  public RangeMarker[] getHostRanges() {
    return myHostRanges;
  }

  public RangeMarker createRangeMarker(final TextRange textRange) {
    return myDelegate.createRangeMarker(injectedToHost(textRange));
  }

  public void stripTrailingSpaces(final boolean inChangedLinesOnly) {
    myDelegate.stripTrailingSpaces(inChangedLinesOnly);
  }

  public void setStripTrailingSpacesEnabled(final boolean isEnabled) {
    myDelegate.setStripTrailingSpacesEnabled(isEnabled);
  }

  public int getLineSeparatorLength(final int line) {
    return myDelegate.getLineSeparatorLength(injectedToHostLine(line));
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
    setText(chars);
    myDelegate.setModificationStamp(newModificationStamp);
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

  public void clearLineModificationFlags() {
  }

  public void removeRangeMarker(RangeMarkerEx rangeMarker) {
    myDelegate.removeRangeMarker(rangeMarker); //todo
  }

  public void addRangeMarker(RangeMarkerEx rangeMarker) {
    myDelegate.addRangeMarker(rangeMarker); //todo
  }

  public boolean isInBulkUpdate() {
    return false;
  }

  public void setInBulkUpdate(boolean value) {
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

  @Terminal
  public int hostToInjected(int hostOffset) {
    if (hostOffset < myHostRanges[0].getStartOffset()) return myPrefix.length();
    int offset = myPrefix.length();
    for (RangeMarker currentRange : myHostRanges) {
      if (currentRange.getStartOffset() <= hostOffset && hostOffset < currentRange.getEndOffset()) {
        return offset + hostOffset - currentRange.getStartOffset();
      }
      offset += currentRange.getEndOffset() - currentRange.getStartOffset();
    }
    return getTextLength() - mySuffix.length();
  }

  @Terminal
  public int injectedToHost(int offset) {
    return injectedToHost(offset, true);
  }

  private int injectedToHost(int offset, boolean strict) {
    if (offset < myPrefix.length()) return myHostRanges[0].getStartOffset();
    offset -= myPrefix.length();
    for (RangeMarker currentRange : myHostRanges) {
      int length = currentRange.getEndOffset() - currentRange.getStartOffset();
      if (offset < length || !strict && offset == length) return currentRange.getStartOffset() + offset;
      offset -= length;
    }
    return myHostRanges[myHostRanges.length-1].getEndOffset();
  }

  public TextRange injectedToHost(TextRange injected) {
    return new TextRange(injectedToHost(injected.getStartOffset()), injectedToHost(injected.getEndOffset(), false));
  }

  public int injectedToHostLine(int line) {
    if (line < myPrefixLineCount) {
      return myDelegate.getLineNumber(myHostRanges[0].getStartOffset());
    }
    int lineCount = getLineCount();
    if (line > lineCount - mySuffixLineCount) {
      return lineCount;
    }
    int offset = getLineStartOffset(line);
    int hostOffset = injectedToHost(offset);

    return myDelegate.getLineNumber(hostOffset);
  }

  public boolean containsRange(int start, int end) {
    if (end - start >= myHostRanges[0].getEndOffset() - myHostRanges[0].getStartOffset()) return false;
    for (RangeMarker hostRange : myHostRanges) {
      if (new TextRange(hostRange.getStartOffset(), hostRange.getEndOffset()).contains(new TextRange(start, end))) return true;
    }
    return false;
  }

  public RangeMarker getFirstTextRange() {
    return myHostRanges[0];
  }

  private static @interface Terminal{}

  public int hostToInjectedLine(int hostLine) {
    int hostOffset = myDelegate.getLineStartOffset(hostLine);
    int offset = hostToInjected(hostOffset);
    return getLineNumber(offset);
  }

  @Nullable
  public TextRange intersectWithEditable(@NotNull TextRange rangeToEdit) {
    return new TextRange(myPrefix.length(), getTextLength() - mySuffix.length()).intersection(rangeToEdit);
  }

  public boolean intersects(DocumentWindow documentWindow) {
    int i = 0;
    int j = 0;
    while (i < myHostRanges.length && j < documentWindow.myHostRanges.length) {
      RangeMarker range = myHostRanges[i];
      RangeMarker otherRange = documentWindow.myHostRanges[j];
      if (new TextRange(range.getStartOffset(), range.getEndOffset()).intersects(new TextRange(otherRange.getStartOffset(), otherRange.getEndOffset()))) return true;
      if (range.getEndOffset() > otherRange.getStartOffset()) i++;
      else if (range.getStartOffset() < otherRange.getEndOffset()) j++;
      else {
        i++;
        j++;
      }
    }
    return false;
  }

  // minimum sequence of text replacement operations for each host range
  // result[i] == null means no change
  // result[i] == "" means delete
  // result[i] == string means replace
  public String[] calculateMinEditSequence(String newText) {
    String refined = newText.substring(myPrefix.length(), newText.length() - mySuffix.length());
    String[] result = new String[myHostRanges.length];
    String hostText = myDelegate.getText();
    calculateMinEditSequence(hostText, refined, result, 0, result.length - 1);
    return result;
  }

  private void calculateMinEditSequence(String hostText, String newText, String[] result, int i, int j) {
    String rangeText1 = hostText.substring(myHostRanges[i].getStartOffset(), myHostRanges[i].getEndOffset());
    if (i == j) {
      result[i] = rangeText1.equals(newText) ? null : newText;
      return;
    }
    if (StringUtil.startsWith(newText, rangeText1)) {
      result[i] = null;  //no change
      calculateMinEditSequence(hostText, newText.substring(rangeText1.length()), result, i+1, j);
      return;
    }
    String rangeText2 = hostText.substring(myHostRanges[j].getStartOffset(), myHostRanges[j].getEndOffset());
    if (StringUtil.endsWith(newText, rangeText2)) {
      result[j] = null;  //no change
      calculateMinEditSequence(hostText, newText.substring(rangeText2.length()), result, i, j-1);
      return;
    }
    if (i+1 == j) {
      String prefix = StringUtil.commonPrefix(rangeText1, newText);
      result[i] = prefix;
      result[j] = newText.substring(prefix.length());
      return;
    }
    String middleText = hostText.substring(myHostRanges[i+1].getStartOffset(), myHostRanges[i+1].getEndOffset());
    int m = newText.indexOf(middleText);
    if (m != -1) {
      result[i] = newText.substring(0, m);
      result[i+1] = null;
      calculateMinEditSequence(hostText, newText.substring(m+middleText.length(), newText.length()), result, i+2, j);
      return;
    }
    middleText = hostText.substring(myHostRanges[j-1].getStartOffset(), myHostRanges[j-1].getEndOffset());
    m = newText.lastIndexOf(middleText);
    if (m != -1) {
      result[j] = newText.substring(m+middleText.length());
      result[j-1] = null;
      calculateMinEditSequence(hostText, newText.substring(0, m), result, i, j-2);
      return;
    }
    result[i] = "";
    result[j] = "";
    calculateMinEditSequence(hostText, newText, result, i+1, j-1);
  }

  public boolean areRangesEqual(DocumentWindow window) {
    if (!myPrefix.equals(window.getPrefix())) return false;
    if (!mySuffix.equals(window.getSuffix())) return false;
    if (myHostRanges.length != window.myHostRanges.length) return false;
    for (int i = 0; i < myHostRanges.length; i++) {
      RangeMarker hostRange = myHostRanges[i];
      RangeMarker other = window.myHostRanges[i];
      if (hostRange.getStartOffset() != other.getStartOffset()) return false;
      if (hostRange.getEndOffset() != other.getEndOffset()) return false;
    }
    return true;
  }

  public boolean isValid() {
    for (RangeMarker range : myHostRanges) {
      if (!range.isValid()) return false;
    }
    return true;
  }

  public boolean equals(Object o) {
    if (!(o instanceof DocumentWindow)) return false;
    DocumentWindow window = (DocumentWindow)o;
    return myDelegate.equals(window.getDelegate()) && areRangesEqual(window);
  }

  public int hashCode() {
    return myHostRanges[0].getStartOffset();
  }

  public boolean isOneLine() {
    return myOneLine;
  }
}