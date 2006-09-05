package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.CoModifiableList;
import com.intellij.util.containers.WeakList;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;

public class DocumentImpl extends UserDataHolderBase implements DocumentEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.DocumentImpl");

  private ArrayList<DocumentListener> myDocumentListeners = new ArrayList<DocumentListener>();
  private CoModifiableList<RangeMarkerImpl> myRangeMarkers = new CoModifiableList<RangeMarkerImpl>(new WeakList<RangeMarkerImpl>());
  private List<RangeMarker> myGuardedBlocks = new ArrayList<RangeMarker>();

  private LineSet myLineSet = new LineSet();
  private CharArray myText;

  private boolean myIsReadOnly = false;
  private boolean isStripTrailingSpacesEnabled = true;
  private long myModificationStamp;
  private HashMap<Project, MarkupModel> myProjectToMarkupModelMap = new HashMap<Project, MarkupModel>();
  private PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  private MarkupModelEx myMarkupModel;
  private DocumentListener[] myCachedDocumentListeners;
  private List<EditReadOnlyListener> myReadOnlyListeners = new ArrayList<EditReadOnlyListener>(1);

  private static final Comparator<? super DocumentListener> ourListenersComparator = new Comparator<Object>() {
    public int compare(Object o1, Object o2) {
      return getPriority(o1) - getPriority(o2);
    }

    private int getPriority(Object o) {
      if (o instanceof PrioritizedDocumentListener) return ((PrioritizedDocumentListener)o).getPriority();
      return Integer.MAX_VALUE;
    }
  };

  private int myCheckGuardedBlocks = 0;
  private boolean myGuardsSuppressed = false;
  private boolean myEventsHandling = false;
  private boolean myAssertWriteAccess = true;

  private DocumentImpl() {
    setCyclicBufferSize(0);
    setModificationStamp(LocalTimeCounter.currentTime());
  }

  public DocumentImpl(String text) {
    this();
    LOG.assertTrue(text.indexOf("\r") < 0, "Wrong line separators in Document");

    setChars(text);
    setModificationStamp(LocalTimeCounter.currentTime());
  }

  public DocumentImpl(CharSequence chars) {
    this();
    setChars(chars);
  }

  public void dontAssertWriteAccess() {
    myAssertWriteAccess = false;
  }

  public char[] getRawChars() {
    return myText.getRawChars();
  }

  public char[] getChars() {
    return CharArrayUtil.fromSequence(getCharsSequence());
  }

  private void setChars(CharSequence chars) {
    myText.replaceText(chars);
    DocumentEvent event = new DocumentEventImpl(this, 0, null, null, -1);
    myLineSet.documentCreated(event);
  }

  public MarkupModel getMarkupModel() {
    if (myMarkupModel == null) myMarkupModel = new MarkupModelImpl(this);
    return myMarkupModel;
  }

  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
    isStripTrailingSpacesEnabled = isEnabled;
  }

  public void stripTrailingSpaces(boolean inChangedLinesOnly) {
    Editor[] editors = EditorFactory.getInstance().getEditors(this, null);
    VisualPosition[] visualCarets = new VisualPosition[editors.length];
    int[] caretLines = new int[editors.length];
    for (int i = 0; i < editors.length; i++) {
      visualCarets[i] = editors[i].getCaretModel().getVisualPosition();
      caretLines[i] = editors[i].getCaretModel().getLogicalPosition().line;
    }

    if (!isStripTrailingSpacesEnabled) {
      return;
    }

    boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();

    lines:
        for (int i = 0; i < myLineSet.getLineCount(); i++) {
          if (!isTestMode) {
            for (int caretLine : caretLines) {
              if (caretLine == i) continue lines;
            }
          }

          if (!inChangedLinesOnly || myLineSet.isModified(i)) {
            int start = -1;
            int lineEnd = myLineSet.getLineEnd(i) - myLineSet.getSeparatorLength(i);
            int lineStart = myLineSet.getLineStart(i);
            CharSequence text = myText.getCharArray();
            for (int offset = lineEnd - 1; offset >= lineStart; offset--) {
              char c = text.charAt(offset);
              if (c != ' ' && c != '\t') {
                break;
              }
              start = offset;
            }
            if (start != -1) {
              deleteString(start, lineEnd);
            }
          }
        }

    for (int i = 0; i < editors.length; i++) {
      editors[i].getCaretModel().moveToVisualPosition(visualCarets[i]);
    }
  }

  public void setReadOnly(boolean isReadOnly) {
    if (myIsReadOnly != isReadOnly) {
      myIsReadOnly = isReadOnly;
      myPropertyChangeSupport.firePropertyChange(PROP_WRITABLE, !isReadOnly, isReadOnly);
    }
  }

  public boolean isWritable() {
    return !myIsReadOnly;
  }

  void addRangeMarker(RangeMarkerImpl rangeMarker) {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    myRangeMarkers.add(rangeMarker);
  }

  public RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    LOG.assertTrue(startOffset <= endOffset, "Should be startOffset <= endOffset");
    RangeMarker block = createRangeMarker(startOffset, endOffset, true);
    myGuardedBlocks.add(block);
    return block;
  }

  public void removeGuardedBlock(RangeMarker block) {
    myGuardedBlocks.remove(block);
  }

  public List<RangeMarker> getGuardedBlocks() {
    return myGuardedBlocks;
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"}) // Way too many garbage is produced otherwise in AbstractList.iterator()
  public RangeMarker getOffsetGuard(int offset) {
    for (int i = 0; i < myGuardedBlocks.size(); i++) {
      RangeMarker block = myGuardedBlocks.get(i);
      if (offsetInRange(offset, block.getStartOffset(), block.getEndOffset())) return block;
    }

    return null;
  }

  public RangeMarker getRangeGuard(int start, int end) {
    for (RangeMarker block : myGuardedBlocks) {
      if (rangeIntersect(start, end, block.getStartOffset(), block.getEndOffset())) return block;
    }

    return null;
  }

  public void startGuardedBlockChecking() {
    myCheckGuardedBlocks++;
  }

  public void stopGuardedBlockChecking() {
    LOG.assertTrue(myCheckGuardedBlocks > 0, "Unpaired start/stopGuardedBlockChecking");
    myCheckGuardedBlocks--;
  }

  private static boolean offsetInRange(int offset, int start, int end) {
    return start <= offset && offset < end;
  }

  private static boolean rangeIntersect(int s1, int e1, int s2, int e2) {
    return s2 < s1 && s1 < e2 || s2 < e1 && e1 < e2
           || s1 < s2 && s2 < e1 || s1 < e2 && e2 < e1
           || s1 == s2 && e1 == e2;
  }

  public RangeMarker createRangeMarker(int startOffset, int endOffset) {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    return new RangeMarkerImpl(this, startOffset, endOffset);
  }

  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    if (!(0 <= startOffset && startOffset <= endOffset && endOffset <= getTextLength())) {
      LOG.error("Incorrect offsets startOffset=" + startOffset + ", endOffset=" + endOffset + ", text length=" +
                getTextLength());
    }

    if (surviveOnExternalChange) {
      return new PersistentRangeMarker(this, startOffset, endOffset);
    }
    else {
      return new RangeMarkerImpl(this, startOffset, endOffset);
    }
  }

  public PersistentLineMarker createPersistentLineMarker(int offset) {
    return new PersistentLineMarker(this, offset);
  }

  public long getModificationStamp() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    return myModificationStamp;
  }

  public void setModificationStamp(long modificationStamp) {
    myModificationStamp = modificationStamp;
  }

  public void replaceText(CharSequence chars, long newModificationStamp) {
    replaceString(0, getTextLength(), chars, newModificationStamp); //TODO: optimization!!!
    clearLineModificationFlags();
  }

  public int getListenersCount() {
    return myDocumentListeners.size();
  }

  public void insertString(int offset, CharSequence s) {
    if (offset < 0 || offset > getTextLength()) throw new IndexOutOfBoundsException("Wrong offset: " + offset);
    assertWriteAccess();
    assertValidSeparators(s);

    if (!isWritable()) throw new ReadOnlyModificationException(this);
    if (s.length() == 0) return;

    RangeMarker marker = getRangeGuard(offset, offset);
    if (marker != null) {
      throwGuardedFragment(marker, offset, null, s.toString());
    }

    myText.insert(s, offset);
  }

  public void deleteString(int startOffset, int endOffset) {
    if (startOffset < 0 || startOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong startOffset: " + startOffset);
    }
    if (endOffset < 0 || endOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong endOffset: " + endOffset);
    }
    if (endOffset < startOffset) {
      throw new IllegalArgumentException("endOffset < startOffset: " + endOffset + " < " + startOffset);
    }

    assertWriteAccess();
    if (!isWritable()) throw new ReadOnlyModificationException(this);
    if (startOffset == endOffset) return;
    CharSequence sToDelete = myText.substring(startOffset, endOffset);

    RangeMarker marker = getRangeGuard(startOffset, endOffset);
    if (marker != null) {
      throwGuardedFragment(marker, startOffset, sToDelete.toString(), null);
    }

    myText.remove(startOffset, endOffset,sToDelete);
  }

  public void replaceString(int startOffset, int endOffset, CharSequence s) {
    replaceString(startOffset, endOffset, s, LocalTimeCounter.currentTime());
  }

  private void replaceString(int startOffset, int endOffset, CharSequence s, final long newModificationStamp) {
    if (startOffset < 0 || startOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong startOffset: " + startOffset);
    }
    if (endOffset < 0 || endOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong endOffset: " + endOffset);
    }
    if (endOffset < startOffset) {
      throw new IllegalArgumentException("endOffset < startOffset: " + endOffset + " < " + startOffset);
    }

    assertWriteAccess();
    assertValidSeparators(s);

    if (!isWritable()) {
      throw new ReadOnlyModificationException(this);
    }
    final int newStringLength = s.length();
    final CharSequence chars = getCharsSequence();
    int newStartInString = 0;
    int newEndInString = newStringLength;
    while (newStartInString < newStringLength &&
           startOffset < endOffset &&
           s.charAt(newStartInString) == chars.charAt(startOffset)) {
      startOffset++;
      newStartInString++;
    }

    while(endOffset > startOffset &&
          newEndInString > newStartInString &&
          s.charAt(newEndInString - 1) == chars.charAt(endOffset - 1)){
      newEndInString--;
      endOffset--;
    }
    //if (newEndInString - newStartInString == 0 && startOffset == endOffset) {
      //setModificationStamp(newModificationStamp);
      //return;
    //}

    s = s.subSequence(newStartInString, newEndInString);
    CharSequence sToDelete = myText.substring(startOffset, endOffset);
    RangeMarker guard = getRangeGuard(startOffset, endOffset);
    if (guard != null) {
      throwGuardedFragment(guard, startOffset, sToDelete.toString(), s.toString());
    }

    myText.replace(startOffset, endOffset, sToDelete, s,newModificationStamp);
  }

  private void assertWriteAccess() {
    if (myAssertWriteAccess) {
      final Application application = ApplicationManager.getApplication();
      if (application != null) {
        application.assertWriteAccessAllowed();
      }
    }
  }

  private static void assertValidSeparators(final CharSequence s) {
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == '\r') {
        LOG.error("Wrong line separators inserted into Document");
      }
    }
  }

  private void throwGuardedFragment(RangeMarker guard, int offset, String oldString, String newString) {
    if (myCheckGuardedBlocks > 0 && !myGuardsSuppressed) {
      DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp);
      throw new ReadOnlyFragmentModificationException(event, guard);
    }
  }

  public void suppressGuardedExceptions() {
    myGuardsSuppressed = true;
  }

  public void unSuppressGuardedExceptions() {
    myGuardsSuppressed = false;
  }

  public boolean isInEventsHandling() {
    return myEventsHandling;
  }

  public void clearLineModificationFlags() {
    myLineSet.clearModificationFlags();
  }

  private DocumentEvent beforeChangedUpdate(int offset, CharSequence oldString, CharSequence newString) {
    DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp);

    DocumentListener[] listeners = getCachedListeners();
    for (int i = listeners.length - 1; i >= 0; i--) {
      try {
        listeners[i].beforeDocumentChange(event);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    myEventsHandling = true;
    return event;
  }

  private void changedUpdate(DocumentEvent event, long newModificationStamp) {
    try{
      LOG.debug(event.toString());
      myLineSet.changedUpdate(event);
      setModificationStamp(newModificationStamp);

      updateRangeMarkers(event);

      DocumentListener[] listeners = getCachedListeners();
      for (DocumentListener listener : listeners) {
        try {
          listener.documentChanged(event);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
    finally{
      myEventsHandling = false;
    }
  }

  private void updateRangeMarkers(final DocumentEvent event) {
    try {
      myRangeMarkers.forEach(new CoModifiableList.InnerIterator<RangeMarkerImpl>() {
        public void process(RangeMarkerImpl rangeMarker, Iterator<RangeMarkerImpl> iterator) {
          try {
            if (rangeMarker.isValid()) {
              rangeMarker.documentChanged(event);
              if (!rangeMarker.isValid() && myGuardedBlocks.remove(rangeMarker)) {
                LOG.error("Guarded blocks should stay valid");
              }
            }
            else {
              iterator.remove();
            }
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      });
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public String getText() {
    assertReadAccessToDocumentsAllowed();
    return myText.toString();
  }

  public int getTextLength() {
    assertReadAccessToDocumentsAllowed();
    return myText.getLength();
  }

  private static void assertReadAccessToDocumentsAllowed() {
    /*
    final ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    if (application != null) {
      application.assertReadAccessToDocumentsAllowed();
    }
    */
  }

/*
  This method should be used very carefully - only to read the array, and to be sure, that nobody changes
  text, while this array is processed.
  Really it is used only to optimize paint in Editor.
  [Valentin] 25.04.2001: More really, it is used in 61 places in 29 files across the project :-)))
*/

  CharSequence getCharsNoThreadCheck() {
    return myText.getCharArray();
  }

  public CharSequence getCharsSequence() {
    assertReadAccessToDocumentsAllowed();
    return myText.getCharArray();
  }


  public void addDocumentListener(DocumentListener listener) {
    myCachedDocumentListeners = null;
    LOG.assertTrue(!myDocumentListeners.contains(listener), listener.toString());
    myDocumentListeners.add(listener);
  }

  public void addDocumentListener(final DocumentListener listener, Disposable parentDisposable) {
    addDocumentListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeDocumentListener(listener);
      }
    });
  }

  public void removeDocumentListener(DocumentListener listener) {
    myCachedDocumentListeners = null;
    boolean success = myDocumentListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public int getLineNumber(int offset) {
    assertReadAccessToDocumentsAllowed();
    int lineIndex = myLineSet.findLineIndex(offset);
    assert (lineIndex >= 0);
    return lineIndex;
  }

  public LineIterator createLineIterator() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    return myLineSet.createIterator();
  }

  public final int getLineStartOffset(int line) {
    assertReadAccessToDocumentsAllowed();
    if (line == 0) return 0; // otherwise it crashed for zero-length document
    int lineStart = myLineSet.getLineStart(line);
    assert (lineStart >= 0);
    return lineStart;
  }

  public final int getLineEndOffset(int line) {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    int result = myLineSet.getLineEnd(line) - getLineSeparatorLength(line);
    assert (result >= 0);
    return result;
  }

  public final int getLineSeparatorLength(int line) {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    int separatorLength = myLineSet.getSeparatorLength(line);
    assert (separatorLength >= 0);
    return separatorLength;
  }

  public final int getLineCount() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    int lineCount = myLineSet.getLineCount();
    assert (lineCount >= 0);
    return lineCount;
  }

  private DocumentListener[] getCachedListeners() {
    if (myCachedDocumentListeners == null) {
      Collections.sort(myDocumentListeners, ourListenersComparator);
      myCachedDocumentListeners = myDocumentListeners.toArray(new DocumentListener[myDocumentListeners.size()]);
    }

    return myCachedDocumentListeners;
  }

  public void fireReadOnlyModificationAttempt() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    EditReadOnlyListener[] listeners = myReadOnlyListeners.toArray(
      new EditReadOnlyListener[myReadOnlyListeners.size()]);
    for (EditReadOnlyListener listener : listeners) {
      listener.readOnlyModificationAttempt(this);
    }
  }

  public void addEditReadOnlyListener(EditReadOnlyListener listener) {
    myReadOnlyListeners.add(listener);
  }

  public void removeEditReadOnlyListener(EditReadOnlyListener listener) {
    myReadOnlyListeners.remove(listener);
  }

  public void removeMarkupModel(Project project) {
    MarkupModel model = myProjectToMarkupModelMap.remove(project);
    if (model != null) {
      ((MarkupModelEx)model).dispose();
    }
  }

  @NotNull
  public MarkupModel getMarkupModel(Project project) {
    return getMarkupModel(project, true);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  public MarkupModel getMarkupModel(Project project, boolean create) {
    if (project == null) {
      if (create && myMarkupModel == null) {
        myMarkupModel = new MarkupModelImpl(this);
      }
      return myMarkupModel;
    }

    final DocumentMarkupModelManager documentMarkupModelManager = project.isDisposed() ? null : DocumentMarkupModelManager.getInstance(project);
    if (documentMarkupModelManager == null || documentMarkupModelManager.isDisposed()) {
      return new EmptyMarkupModel(this);
    }

    MarkupModel model = myProjectToMarkupModelMap.get(project);
    if (create && model == null) {
      model = new MarkupModelImpl(this);
      myProjectToMarkupModelMap.put(project, model);
      documentMarkupModelManager.registerDocument(this);
    }

    return model;
  }

  private void setCharArray(final CharArray charArray) {
    myText = charArray;
  }

  public void setCyclicBufferSize(int bufferSize) {
    final CharArray charArray = bufferSize == 0 ? new CharArray() {
      protected DocumentEvent beforeChangedUpdate(int offset, CharSequence oldString, CharSequence newString) {
        return DocumentImpl.this.beforeChangedUpdate(offset, oldString, newString);
      }

      protected void afterChangedUpdate(DocumentEvent event, long newModificationStamp) {
        changedUpdate(event, newModificationStamp);
      }
    } : new CyclicCharArray(bufferSize) {
      protected DocumentEvent beforeChangedUpdate(int offset, CharSequence oldString, CharSequence newString) {
        return DocumentImpl.this.beforeChangedUpdate(offset, oldString, newString);
      }

      protected void afterChangedUpdate(DocumentEvent event, long newModificationStamp) {
        changedUpdate(event, newModificationStamp);
      }
    };
    setCharArray(charArray);
  }

  public void setText(final CharSequence text) {
    if (!CommandProcessor.getInstance().isUndoTransparentActionInProgress()) {
      CommandProcessor.getInstance().executeCommand(
        new Runnable() {
          public void run() {
            replaceString(0, getTextLength(), text);
          }
        },
        "file text set",
        null
      );
    }
    else {
      replaceString(0, getTextLength(), text);
    }

    clearLineModificationFlags();
  }

  public RangeMarker createRangeMarker(final TextRange textRange) {
    return createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
  }
}

