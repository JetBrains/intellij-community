package com.intellij.find;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.usages.*;
import com.intellij.find.impl.FindInProjectUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;
import java.util.ArrayList;

/**
 *
 */
public class FindUtil {
  private static Key KEY = Key.create("FindUtil.KEY");
  @NonNls private static String UP = "UP";
  @NonNls private static String DOWN = "DOWN";

  public static void findWordAtCaret(Project project, Editor editor) {
    int caretOffset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    int start = 0, end = document.getTextLength();
    if (!editor.getSelectionModel().hasSelection()) {
      for (int i = caretOffset - 1; i >= 0; i--) {
        char c = text.charAt(i);
        if (!Character.isJavaIdentifierPart(c)) {
          start = i + 1;
          break;
        }
      }
      for (int i = caretOffset; i < document.getTextLength(); i++) {
        char c = text.charAt(i);
        if (!Character.isJavaIdentifierPart(c)) {
          end = i;
          break;
        }
      }
    }
    else {
      start = editor.getSelectionModel().getSelectionStart();
      end = editor.getSelectionModel().getSelectionEnd();
    }
    if (start >= end) {
      return;
    }
    FindManager findManager = FindManager.getInstance(project);
    String s = text.subSequence(start, end).toString();
    FindSettings.getInstance().addStringToFind(s);
    findManager.getFindInFileModel().setStringToFind(s);
    findManager.setFindWasPerformed();
    FindModel model = new FindModel();
    model.setStringToFind(s);
    model.setCaseSensitive(true);
    model.setWholeWordsOnly(!editor.getSelectionModel().hasSelection());
    findManager.setFindNextModel(model);
    doSearch(project, editor, caretOffset, true, model, true);
  }

  public static void find(Project project, Editor editor) {
    FindManager findManager = FindManager.getInstance(project);
    String s = editor.getSelectionModel().getSelectedText();

    FindModel model = (FindModel)findManager.getFindInFileModel().clone();
    if (s != null) {
      if (s.indexOf('\n') >= 0) {
        model.setGlobal(false);
      }
      else {
        model.setStringToFind(s);
        model.setGlobal(true);
      }
    }
    else {
      model.setGlobal(true);
    }

    model.setReplaceState(false);
    model.setFindAllEnabled(PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) != null);

    if (!findManager.showFindDialog(model)) {
      return;
    }

    if (model.isFindAll()) {
      doFindAll(project, editor, model);
      return;
    }

    if (!model.isGlobal() && editor.getSelectionModel().hasSelection()) {
      int offset = model.isForward()
                   ? editor.getSelectionModel().getSelectionStart()
                   : editor.getSelectionModel().getSelectionEnd();
      ScrollType scrollType = model.isForward() ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
      moveCaretAndDontChangeSelection(editor, offset, scrollType);
    }

    int offset;
    if (model.isGlobal()) {
      if (model.isFromCursor()) {
        offset = editor.getCaretModel().getOffset();
      }
      else {
        if (model.isForward()) {
          offset = 0;
        }
        else {
          offset = editor.getDocument().getTextLength();
        }
      }
    }
    else {
      // in selection

      if (!editor.getSelectionModel().hasSelection()) {
        // TODO[anton] actually, this should never happen - Find dialog should not allow such combination
        findManager.setFindNextModel(null);
        return;
      }

      if (model.isForward()) {
        offset = editor.getSelectionModel().getSelectionStart();
      }
      else {
        offset = editor.getSelectionModel().getSelectionEnd();
      }
    }

    findManager.setFindNextModel(null);
    findManager.getFindInFileModel().copyFrom(model);
    doSearch(project, editor, offset, true, model, true);
  }

  private static void doFindAll(final Project project, final Editor editor, final FindModel findModel) {
    final Document document = editor.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    CharSequence text = document.getCharsSequence();
    int textLength = document.getTextLength();
    final List<Usage> usages = new ArrayList<Usage>();
    if (text != null) {
      int offset = 0;
      FindManager findManager = FindManager.getInstance(project);
      findModel.setForward(true); // when find all there is no diff in direction
      
      while (offset < textLength) {
        FindResult result = findManager.findString(text, offset, findModel);
        if (!result.isStringFound()) break;

        usages.add(new UsageInfo2UsageAdapter(new UsageInfo(psiFile, result.getStartOffset(), result.getEndOffset())));

        final int prevOffset = offset;
        offset = result.getEndOffset();

        if (prevOffset == offset) {
          // for regular expr the size of the match could be zero -> could be infinite loop in finding usages!
          ++offset;
        }
      }
    }
    final UsageTarget[] usageTargets = new UsageTarget[]{ new FindInProjectUtil.StringUsageTarget(findModel.getStringToFind()) };
    final UsageViewPresentation usageViewPresentation = FindInProjectUtil.setupViewPresentation(false, findModel);
    UsageViewManager.getInstance(project).showUsages(usageTargets, usages.toArray(new Usage[0]),
                                                     usageViewPresentation);
  }

  public static void searchBack(Project project, FileEditor fileEditor) {
    if (!(fileEditor instanceof TextEditor)) return;
    TextEditor textEditor = (TextEditor)fileEditor;
    Editor editor = textEditor.getEditor();

    FindManager findManager = FindManager.getInstance(project);
    if (!findManager.findWasPerformed()) {
      find(project, editor);
      return;
    }

    FindModel model = findManager.getFindNextModel();
    if (model == null) {
      model = findManager.getFindInFileModel();
    }
    model = (FindModel)model.clone();
    model.setForward(!model.isForward());
    if (!model.isGlobal() && !editor.getSelectionModel().hasSelection()) {
      model.setGlobal(true);
    }

    int offset;
    if (UP.equals(editor.getUserData(KEY)) && !model.isForward()) {
      offset = editor.getDocument().getTextLength();
    }
    else if (DOWN.equals(editor.getUserData(KEY)) && model.isForward()) {
      offset = 0;
    }
    else {
      editor.putUserData(KEY, null);
      offset = editor.getCaretModel().getOffset();
      if (!model.isForward() && offset > 0) {
        offset--;
      }
    }
    searchAgain(project, editor, offset, model);
  }

  public static boolean searchAgain(Project project, FileEditor fileEditor) {
    if (!(fileEditor instanceof TextEditor)) return false;
    TextEditor textEditor = (TextEditor)fileEditor;
    Editor editor = textEditor.getEditor();

    FindManager findManager = FindManager.getInstance(project);
    if (!findManager.findWasPerformed()) {
      find(project, editor);
      return false;
    }

    FindModel model = findManager.getFindNextModel();
    if (model == null) {
      model = findManager.getFindInFileModel();
    }
    model = (FindModel)model.clone();

    int offset;
    if (DOWN.equals(editor.getUserData(KEY)) && model.isForward()) {
      offset = 0;
    }
    else if (UP.equals(editor.getUserData(KEY)) && !model.isForward()) {
      offset = editor.getDocument().getTextLength();
    }
    else {
      editor.putUserData(KEY, null);
      offset = editor.getCaretModel().getOffset();
      if (!model.isForward() && offset > 0 ) {
        offset--;
      }
    }
    return searchAgain(project, editor, offset, model);
  }

  private static boolean searchAgain(Project project, Editor editor, int offset, FindModel model) {
    if (!model.isGlobal() && !editor.getSelectionModel().hasSelection()) {
      model.setGlobal(true);
    }
    model.setFromCursor(false);
    if (model.isReplaceState()) {
      model.setPromptOnReplace(true);
      model.setReplaceAll(false);
      replace(project, editor, offset, model);
      return true;
    }
    else {
      doSearch(project, editor, offset, true, model, true);
      return false;
    }
  }

  public static boolean replace(Project project, Editor editor) {
    FindManager findManager = FindManager.getInstance(project);
    FindModel model = (FindModel)findManager.getFindInFileModel().clone();
    String s = editor.getSelectionModel().getSelectedText();
    if (s != null) {
      if (s.indexOf('\n') >= 0) {
        model.setGlobal(false);
      }
      else {
        model.setStringToFind(s);
        model.setGlobal(true);
      }
    }
    else {
      model.setGlobal(true);
    }
    model.setReplaceState(true);

    if (!findManager.showFindDialog(model)) {
      return false;
    }
    if (!model.isGlobal() && editor.getSelectionModel().hasSelection()) {
      int offset = model.isForward()
                   ? editor.getSelectionModel().getSelectionStart()
                   : editor.getSelectionModel().getSelectionEnd();
      ScrollType scrollType = model.isForward() ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
      moveCaretAndDontChangeSelection(editor, offset, scrollType);
    }
    int offset;
    if (model.isGlobal()) {
      if (model.isFromCursor()) {
        offset = editor.getCaretModel().getOffset();
        if (!model.isForward()) {
          offset++;
        }
      }
      else {
        if (model.isForward()) {
          offset = 0;
        }
        else {
          offset = editor.getDocument().getTextLength();
        }
      }
    }
    else {
      // in selection

      if (!editor.getSelectionModel().hasSelection()) {
        // TODO[anton] actually, this should never happen - Find dialog should not allow such combination
        findManager.setFindNextModel(null);
        return false;
      }

      if (model.isForward()) {
        offset = editor.getSelectionModel().getSelectionStart();
      }
      else {
        offset = editor.getSelectionModel().getSelectionEnd();
      }
    }

    if (s != null && editor.getSelectionModel().hasSelection() && s.equals(model.getStringToFind())) {
      if (model.isFromCursor() && model.isForward()) {
        offset = Math.min(editor.getSelectionModel().getSelectionStart(), offset);
      }
      else if (model.isFromCursor() && !model.isForward()) {
        offset = Math.max(editor.getSelectionModel().getSelectionEnd(), offset);
      }
    }
    findManager.setFindNextModel(null);
    findManager.getFindInFileModel().copyFrom(model);
    return replace(project, editor, offset, model);
  }

  private static boolean replace(Project project, Editor editor, int offset, FindModel model) {
    boolean isReplaced = false;
    Document document = editor.getDocument();
    int caretOffset = offset;

    if (!document.isWritable()) {
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(document, project)){
        return false;
      }
    }

    document.startGuardedBlockChecking();
    try {
      FindManager findManager = FindManager.getInstance(project);
      boolean toPrompt = model.isPromptOnReplace();
      model = (FindModel)model.clone();
      while (offset >= 0 && offset < editor.getDocument().getTextLength()) {
      caretOffset = offset;
        FindResult result = doSearch(project, editor, offset, !isReplaced, model, toPrompt);
        if (result == null) {
          break;
        }
        int startResultOffset = result.getStartOffset();
        model.setFromCursor(true);
        if (toPrompt) {
          int promptResult = findManager.showPromptDialog(model, FindBundle.message("find.replace.dialog.title"));
          if (promptResult == FindManager.PromptResult.SKIP) {
            offset = model.isForward() ? result.getEndOffset() : startResultOffset;
            continue;
          }
          if (promptResult == FindManager.PromptResult.CANCEL) {
            break;
          }
          if (promptResult == FindManager.PromptResult.ALL) {
            toPrompt = false;
          }
        }

        int startOffset = result.getStartOffset(), endOffset = result.getEndOffset();
        String foundString = document.getCharsSequence().subSequence(startOffset, endOffset).toString();
        String toReplace = findManager.getStringToReplace(foundString, model);
        if (model.isForward()) {
          offset = doReplace(document, model, result, toReplace).getEndOffset();
        }
        else {
          offset = doReplace(document, model, result, toReplace).getStartOffset();
        }

        //[SCR 7258]
        if (!isReplaced) {
          editor.getCaretModel().moveToOffset(0);
        }

        isReplaced = true;
      }
    }
    catch (ReadOnlyFragmentModificationException e) {
      EditorActionManager.getInstance().getReadonlyFragmentModificationHandler().handle(e);
    }
    finally {
      document.stopGuardedBlockChecking();
    }

    if (isReplaced) {
      editor.getCaretModel().moveToOffset(caretOffset);
    }

    return isReplaced;
  }

  private static FindResult doSearch(Project project,
                                     final Editor editor,
                                     int offset,
                                     boolean toWarn,
                                     FindModel model, boolean adjustEditor) {
    FindManager findManager = FindManager.getInstance(project);
    Document document = editor.getDocument();

    final FindResult result = findManager.findString(document.getCharsSequence(), offset, model);
    String stringToFind = model.getStringToFind();
    if (stringToFind == null) {
      return null;
    }

    boolean isFound = result.isStringFound();
    if (!model.isGlobal()) {
      if (result.getEndOffset() > editor.getSelectionModel().getSelectionEnd() ||
          result.getStartOffset() < editor.getSelectionModel().getSelectionStart()) {
        isFound = false;
      }
    }
    if (!isFound) {
      if (toWarn) {
        processNotFound(editor, model.getStringToFind(), model, project);
      }
      return null;
    }

    if (adjustEditor) {
      final CaretModel caretModel = editor.getCaretModel();
      final ScrollingModel scrollingModel = editor.getScrollingModel();
      int oldCaretOffset = caretModel.getOffset();
      boolean forward = oldCaretOffset < result.getStartOffset();
      final ScrollType scrollType = forward ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;

      if (model.isGlobal()) {
        caretModel.moveToOffset(result.getEndOffset());
        editor.getSelectionModel().removeSelection();
        scrollingModel.scrollToCaret(scrollType);
        scrollingModel.runActionOnScrollingFinished(
          new Runnable() {
            public void run() {
              scrollingModel.scrollTo(editor.offsetToLogicalPosition(result.getStartOffset()), scrollType);
              scrollingModel.scrollTo(editor.offsetToLogicalPosition(result.getEndOffset()), scrollType);
            }
          }
        );
      }
      else {
        moveCaretAndDontChangeSelection(editor, result.getStartOffset(), scrollType);
        moveCaretAndDontChangeSelection(editor, result.getEndOffset(), scrollType);
      }
      IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();

      EditorColorsManager manager = EditorColorsManager.getInstance();
      TextAttributes selectionAttributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

      if (!model.isGlobal()) {
        final RangeHighlighterEx segmentHighlighter = (RangeHighlighterEx)editor.getMarkupModel().addRangeHighlighter(
          result.getStartOffset(),
          result.getEndOffset(),
          HighlighterLayer.SELECTION + 1,
          selectionAttributes, HighlighterTargetArea.EXACT_RANGE);
        MyListener listener = new MyListener(editor, segmentHighlighter);
        editor.getContentComponent().addFocusListener(listener);
        caretModel.addCaretListener(listener);
      }
      else {
        editor.getSelectionModel().setSelection(result.getStartOffset(), result.getEndOffset());
      }
    }

    return result;
  }

  private static class MyListener implements FocusListener, CaretListener {
    private Editor myEditor;
    private RangeHighlighter mySegmentHighlighter;

    public MyListener(Editor editor, RangeHighlighter segmentHighlighter) {
      myEditor = editor;
      mySegmentHighlighter = segmentHighlighter;
    }

    public void focusGained(FocusEvent e) {
//        removeAll();
    }

    public void focusLost(FocusEvent e) {
    }

    public void caretPositionChanged(CaretEvent e) {
      removeAll();
    }

    private void removeAll() {
      myEditor.getMarkupModel().removeHighlighter(mySegmentHighlighter);
      myEditor.getContentComponent().addFocusListener(this);
      myEditor.getCaretModel().removeCaretListener(this);
    }
  }

  private static void processNotFound(final Editor editor, String stringToFind, FindModel model, Project project) {
    FindResult result;

    String message = FindBundle.message("find.search.string.not.found.message", stringToFind);

    if (model.isGlobal()) {
      final FindModel newModel = (FindModel)model.clone();
      FindManager findManager = FindManager.getInstance(project);
      Document document = editor.getDocument();
      if (newModel.isForward()) {
        result = findManager.findString(document.getCharsSequence(), 0, model);
      }
      else {
        result =
        findManager.findString(document.getCharsSequence(), document.getTextLength(), model);
      }
      if (result != null && !result.isStringFound()) {
        result = null;
      }

      FindModel modelForNextSearch = findManager.getFindNextModel();
      if (modelForNextSearch == null) {
        modelForNextSearch = findManager.getFindInFileModel();
      }

      if (result != null) {
        if (newModel.isForward()) {
          AnAction action = ActionManager.getInstance().getAction(
            modelForNextSearch.isForward() ? IdeActions.ACTION_FIND_NEXT : IdeActions.ACTION_FIND_PREVIOUS);
          String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
          if (shortcutsText.length() > 0) {
            message = FindBundle.message("find.search.again.from.top.hotkey.message", message, shortcutsText);
          }
          else {
            message = FindBundle.message("find.search.again.from.top.action.message", message);
          }
          editor.putUserData(KEY, DOWN);
        }
        else {
          AnAction action = ActionManager.getInstance().getAction(
            modelForNextSearch.isForward() ? IdeActions.ACTION_FIND_PREVIOUS : IdeActions.ACTION_FIND_NEXT);
          String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
          if (shortcutsText.length() > 0) {
            message = FindBundle.message("find.search.again.from.bottom.hotkey.message", message, shortcutsText);
          }
          else {
            message = FindBundle.message("find.search.again.from.bottom.action.message", message);
          }
          editor.putUserData(KEY, UP);
        }
      }
      CaretListener listener = new CaretListener() {
        public void caretPositionChanged(CaretEvent e) {
          editor.putUserData(KEY, null);
          editor.getCaretModel().removeCaretListener(this);
        }
      };
      editor.getCaretModel().addCaretListener(listener);
    }
    HintManager hintManager = HintManager.getInstance();
    JComponent component = HintUtil.createInformationLabel(message);
    final LightweightHint hint = new LightweightHint(component);
    hintManager.showEditorHint(hint, editor, HintManager.UNDER,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE |
                               HintManager.HIDE_BY_SCROLLING,
                               0, false);
  }

  private static TextRange doReplace(final Document document, final FindModel model, FindResult result, final String stringToReplace) {
    final int startOffset = result.getStartOffset();
    final int endOffset = result.getEndOffset();
    if (stringToReplace == null) return new TextRange(Integer.MAX_VALUE, Integer.MIN_VALUE);

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          document.deleteString(startOffset, endOffset);
          //[ven] I doubt converting is a good solution to SCR 21224
          document.insertString(startOffset, StringUtil.convertLineSeparators(stringToReplace));
        }
      }
    );

    int newOffset = startOffset + stringToReplace.length();

    int start = startOffset, end = newOffset;
    if (model.isRegularExpressions()) {
      String toFind = model.getStringToFind();
      if (model.isForward()) {
        if (StringUtil.endsWithChar(toFind, '$')) {
          int i = 0;
          int length = toFind.length();
          while (i + 2 <= length && toFind.charAt(length - i - 2) == '\\') i++;
          if (i % 2 == 0) end++; //This $ is a special symbol in regexp syntax
        }
        else if (StringUtil.startsWithChar(toFind, '^')) {
          while (end < document.getTextLength() && document.getCharsSequence().charAt(end) != '\n') end++;
        }
      }
      else {
        if (StringUtil.startsWithChar(toFind, '^')) {
          start--;
        }
        else if (StringUtil.endsWithChar(toFind, '$')) {
          while (start >= 0 && document.getCharsSequence().charAt(start) != '\n') start--;
        }
      }
    }
    return new TextRange(start, end);
  }

  private static void moveCaretAndDontChangeSelection(final Editor editor, int offset, ScrollType scrollType) {
    LogicalPosition pos = editor.offsetToLogicalPosition(offset);
    editor.getCaretModel().moveToLogicalPosition(pos);
    editor.getScrollingModel().scrollToCaret(scrollType);
  }
}
