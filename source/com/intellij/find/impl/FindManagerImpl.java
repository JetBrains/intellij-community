
package com.intellij.find.impl;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.HighlightManagerImpl;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.*;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ReplacePromptDialog;
import com.intellij.util.text.StringSearcher;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FindManagerImpl extends FindManager implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.impl.FindManagerImpl");

  private FindUsagesManager myFindUsagesManager;
  private boolean isFindWasPerformed = false;
  private Point myReplaceInFilePromptPos = new Point(-1, -1);
  private Point myReplaceInProjectPromptPos = new Point(-1, -1);
  private FindModel myFindInProjectModel = new FindModel();
  private FindModel myFindInFileModel = new FindModel();
  private FindModel myFindNextModel = null;
  private static FindResultImpl NOT_FOUND_RESULT = new FindResultImpl();
  private Project myProject;
  private com.intellij.usages.UsageViewManager myAnotherManager;
  private Key HIGHLIGHTER_WAS_NOT_FOUND_KEY = Key.create("com.intellij.find.impl.FindManagerImpl.HighlighterNotFoundKey");
  @NonNls private static final String FIND_USAGES_MANAGER_ELEMENT = "FindUsagesManager";

  public FindManagerImpl(Project project, FindSettings findSettings, com.intellij.usages.UsageViewManager anotherManager) {
    myProject = project;
    myAnotherManager = anotherManager;
    findSettings.initModelBySetings(myFindInFileModel);
    findSettings.initModelBySetings(myFindInProjectModel);

    myFindUsagesManager = new FindUsagesManager(myProject, myAnotherManager);
    myFindInProjectModel.setMultipleFiles(true);
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    final Element findUsages = element.getChild(FIND_USAGES_MANAGER_ELEMENT);
    if (findUsages != null) {
      myFindUsagesManager.readExternal(findUsages);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final Element findUsages = new Element(FIND_USAGES_MANAGER_ELEMENT);
    element.addContent(findUsages);
    myFindUsagesManager.writeExternal(findUsages);
  }

  public int showPromptDialog(final FindModel model, String title) {
    ReplacePromptDialog replacePromptDialog = new ReplacePromptDialog(model.isMultipleFiles(), title, myProject) {
      public Point getInitialLocation() {
        if (model.isMultipleFiles() && myReplaceInProjectPromptPos.x >= 0 && myReplaceInProjectPromptPos.y >= 0){
          return myReplaceInProjectPromptPos;
        }
        if (!model.isMultipleFiles() && myReplaceInFilePromptPos.x >= 0 && myReplaceInFilePromptPos.y >= 0){
          return myReplaceInFilePromptPos;
        }
        return null;
      }
    };

    replacePromptDialog.show();

    if (model.isMultipleFiles()){
      myReplaceInProjectPromptPos = replacePromptDialog.getLocation();
    }
    else{
      myReplaceInFilePromptPos = replacePromptDialog.getLocation();
    }
    return replacePromptDialog.getExitCode();
  }

  public boolean showFindDialog(FindModel model) {
    FindDialog findDialog = new FindDialog(myProject, model);
    findDialog.show();
    if (!findDialog.isOK()){
      return false;
    }

    findDialog.apply();
    String stringToFind = model.getStringToFind();
    if (stringToFind == null || stringToFind.length() == 0){
      return false;
    }
    FindSettings.getInstance().addStringToFind(stringToFind);
    if (!model.isMultipleFiles()){
      setFindWasPerformed();
    }
    if (model.isReplaceState()){
      FindSettings.getInstance().addStringToReplace(model.getStringToReplace());
    }
    if (model.isMultipleFiles() && !model.isProjectScope()){
      FindSettings.getInstance().addDirectory(model.getDirectoryName());

      if (model.getDirectoryName()!=null) {
        myFindInProjectModel.setWithSubdirectories(model.isWithSubdirectories());
      }
    }
    return true;
  }

  @NotNull
  public FindModel getFindInFileModel() {
    return myFindInFileModel;
  }

  @NotNull
  public FindModel getFindInProjectModel() {
    myFindInProjectModel.setFromCursor(false);
    myFindInProjectModel.setForward(true);
    myFindInProjectModel.setGlobal(true);
    return myFindInProjectModel;
  }

  public boolean findWasPerformed() {
    return isFindWasPerformed;
  }

  public void setFindWasPerformed() {
    isFindWasPerformed = true;
    myFindUsagesManager.clearFindingNextUsageInFile();
  }

  public FindModel getFindNextModel() {
    return myFindNextModel;
  }

  public void setFindNextModel(FindModel findNextModel) {
    myFindNextModel = findNextModel;
  }

  @NotNull
  public FindResult findString(CharSequence text, int offset, FindModel model){
    if (LOG.isDebugEnabled()) {
      LOG.debug("offset="+offset);
      LOG.debug("textlength="+text.length());
      LOG.debug(model.toString());
    }

    while(true){
      FindResult result = doFindString(text, offset, model);

      if (!model.isWholeWordsOnly()) {
        return result;
      }
      if (!result.isStringFound()){
        return result;
      }
      if (isWholeWord(text, result.getStartOffset(), result.getEndOffset())){
        return result;
      }

      if(model.isForward()) offset = result.getStartOffset() + 1; else offset = result.getEndOffset() - 1;
    }
  }

  private static boolean isWholeWord(CharSequence text, int startOffset, int endOffset) {
    boolean isWordStart = startOffset == 0  || !Character.isJavaIdentifierPart(text.charAt(startOffset - 1));
    boolean isWordEnd   = endOffset == text.length() || !Character.isJavaIdentifierPart(text.charAt(endOffset)) ||
                          (endOffset > 0 && !Character.isJavaIdentifierPart(text.charAt(endOffset - 1)));

    return isWordStart && isWordEnd;
  }

  private static FindResult doFindString(CharSequence text, int offset, FindModel model) {
    String toFind = model.getStringToFind();
    if (toFind == null || toFind.length() == 0){
      return NOT_FOUND_RESULT;
    }

    if (model.isRegularExpressions()){
      return findStringByRegularExpression(text, offset, model);
    }

    StringSearcher searcher = new StringSearcher(toFind);
    searcher.setCaseSensitive(model.isCaseSensitive());
    searcher.setForwardDirection(model.isForward());
    int index;
    if (model.isForward()){
      final int res = searcher.scan(text.subSequence(offset, text.length()));
      index = res < 0 ? -1 : res + offset;
    }
    else{
      index = searcher.scan(text.subSequence(0, offset));
    }
    if (index < 0){
      return NOT_FOUND_RESULT;
    }
    else{
      return new FindResultImpl(index, index + toFind.length());
    }
  }

  private static FindResult findStringByRegularExpression(CharSequence text, int startOffset, FindModel model) {
    String toFind = model.getStringToFind();

    Pattern pattern;
    try{
      if (model.isCaseSensitive()){
        pattern = Pattern.compile(toFind, Pattern.MULTILINE);
      }
      else{
        pattern = Pattern.compile(toFind, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
      }
    }
    catch(PatternSyntaxException e){
      LOG.error(e);
      return null;
    }

    Matcher matcher = pattern.matcher(text);

    if (model.isForward()){
      if (matcher.find(startOffset)) {
        if (matcher.end() <= text.length()) {
          return new FindResultImpl(matcher.start(), matcher.end());
        }
      }
      return NOT_FOUND_RESULT;
    }
    else{
      int start = -1, end = -1;
      while(matcher.find() && matcher.end() < startOffset){
        start = matcher.start();
        end = matcher.end();
      }
      if (start < 0){
        return NOT_FOUND_RESULT;
      }
      else{
        return new FindResultImpl(start, end);
      }
    }
  }

  public String getStringToReplace(String foundString, FindModel model) {
    if (model == null) {
      return null;
    }
    String toReplace = model.getStringToReplace();
    if (!model.isRegularExpressions()) {
      if (model.isPreserveCase()) {
        return replaceWithCaseRespect (toReplace, foundString);
      }
      return toReplace;
    }

    String toFind = model.getStringToFind();

    Pattern pattern;
    try{
      int flags = Pattern.MULTILINE;
      if (!model.isCaseSensitive()) {
        flags |= Pattern.CASE_INSENSITIVE;
      }
      pattern = Pattern.compile(toFind, flags);
    }
    catch(PatternSyntaxException e){
      return toReplace;
    }

    Matcher matcher = pattern.matcher(foundString);
    if (matcher.matches()) {
      try {
        return matcher.replaceAll(StringUtil.unescapeStringCharacters(toReplace));
      }
      catch (Exception e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                Messages.showErrorDialog(myProject, FindBundle.message("find.replace.invalid.replacement.string"),
                                         FindBundle.message("find.replace.invalid.replacement.string.title"));
              }
            });
        return null;
      }
    } else {
      // There are valid situations (for example, IDEADEV-2543 or positive lookbehind assertions)
      // where an expression which matches a string in context will not match the same string
      // separately).
      return toReplace;
    }
  }

  private String replaceWithCaseRespect(String toReplace, String foundString) {
    if (foundString.length() == 0 || toReplace.length() == 0) return toReplace;
    StringBuffer buffer = new StringBuffer();

    if (Character.isUpperCase(foundString.charAt(0))) {
      buffer.append(Character.toUpperCase(toReplace.charAt(0)));
    } else {
      buffer.append(Character.toLowerCase(toReplace.charAt(0)));
    }
    if (toReplace.length() == 1) return buffer.toString();

    if (foundString.length() == 1) {
      buffer.append(toReplace.substring(1));
      return buffer.toString();
    }

    boolean isTailUpper = true;
    boolean isTailLower = true;
    for (int i = 1; i < foundString.length(); i++) {
      isTailUpper &= Character.isUpperCase(foundString.charAt(i));
      isTailLower &= Character.isLowerCase(foundString.charAt(i));
      if (!isTailUpper && !isTailLower) break;
    }

    if (isTailUpper) {
      buffer.append(toReplace.substring(1).toUpperCase());
    } else if (isTailLower) {
      buffer.append(toReplace.substring(1).toLowerCase());
    } else {
      buffer.append(toReplace.substring(1));
    }
    return buffer.toString();
  }

  public boolean canFindUsages(PsiElement element) {
    return myFindUsagesManager.canFindUsages(element);
  }

  public void findUsages(PsiElement element) {
    myFindUsagesManager.findUsages(element, null, null);
  }

  public void findUsagesInEditor(PsiElement element, FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      Editor editor = textEditor.getEditor();
      Document document = editor.getDocument();
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);

      myFindUsagesManager.findUsages(element, psiFile, fileEditor);
    }
  }

  public boolean findNextUsageInEditor(FileEditor fileEditor) {
    LOG.assertTrue(fileEditor != null);

    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      Editor editor = textEditor.getEditor();

      FindModel model = getFindNextModel();
      if (model != null && model.searchHighlighters()) {
        RangeHighlighter[] highlighters = ((HighlightManagerImpl)HighlightManager.getInstance(myProject)).getHighlighters(editor);
        if (highlighters.length > 0) {
          return highlightNextHighlighter(highlighters, editor, editor.getCaretModel().getOffset(), true, false);
        }
      }
    }

    return myFindUsagesManager.findNextUsageInFile(fileEditor);
  }

  private boolean highlightNextHighlighter(RangeHighlighter[] highlighters, Editor editor, int offset, boolean isForward, boolean secondPass) {
    RangeHighlighter highlighterToSelect = null;
    Object wasNotFound = editor.getUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY);
    for (int i = 0; i < highlighters.length; i++) {
      RangeHighlighter highlighter = highlighters[i];
      int start = highlighter.getStartOffset();
      int end = highlighter.getEndOffset();
      if (highlighter.isValid() && start < end) {
        if (isForward && (start > offset || (start == offset && secondPass))) {
          if (highlighterToSelect == null || highlighterToSelect.getStartOffset() > start) highlighterToSelect = highlighter;
        }
        if (!isForward && (end < offset || (end == offset && secondPass))) {
          if (highlighterToSelect == null || highlighterToSelect.getEndOffset() < end) highlighterToSelect = highlighter;
        }
      }
    }
    if (highlighterToSelect != null) {
      editor.getSelectionModel().setSelection(highlighterToSelect.getStartOffset(), highlighterToSelect.getEndOffset());
      editor.getCaretModel().moveToOffset(highlighterToSelect.getStartOffset());
      ScrollType scrollType;
      if (!secondPass) {
        scrollType = isForward ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
      }
      else {
        scrollType = !isForward ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
      }
      editor.getScrollingModel().scrollToCaret(scrollType);
      editor.putUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY, null);
      return true;
    }

    if (wasNotFound == null) {
      editor.putUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY, Boolean.TRUE);
      String message = FindBundle.message("find.highlight.no.more.highlights.found");
      if (isForward) {
        AnAction action=ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT);
        String shortcutsText=KeymapUtil.getFirstKeyboardShortcutText(action);
        if (shortcutsText.length() > 0) {
          message = FindBundle.message("find.search.again.from.top.hotkey.message", message, shortcutsText);
        }
        else {
          message = FindBundle.message("find.search.again.from.top.action.message", message);
        }
      }
      else {
        AnAction action=ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS);
        String shortcutsText=KeymapUtil.getFirstKeyboardShortcutText(action);
        if (shortcutsText.length() > 0) {
          message = FindBundle.message("find.search.again.from.bottom.hotkey.message", message, shortcutsText);
        }
        else {
          message = FindBundle.message("find.search.again.from.bottom.action.message", message);
        }
      }
      HintManager hintManager = HintManager.getInstance();
      JComponent component = HintUtil.createInformationLabel(message);
      final LightweightHint hint = new LightweightHint(component);
      hintManager.showEditorHint(hint, editor, HintManager.UNDER, HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false);
      return true;
    } else if (!secondPass) {
      offset = isForward ? 0 : editor.getDocument().getTextLength();
      return highlightNextHighlighter(highlighters, editor, offset, isForward, true);
    }

    return false;
  }

  public boolean findPreviousUsageInEditor(FileEditor fileEditor) {
    LOG.assertTrue(fileEditor != null);

    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      Editor editor = textEditor.getEditor();

      FindModel model = getFindNextModel();
      if (model != null && model.searchHighlighters()) {
        RangeHighlighter[] highlighters = ((HighlightManagerImpl)HighlightManager.getInstance(myProject)).getHighlighters(editor);
        if (highlighters.length > 0) {
          return highlightNextHighlighter(highlighters, editor, editor.getCaretModel().getOffset(), false, false);
        }
      }
    }

    return myFindUsagesManager.findPreviousUsageInFile(fileEditor);
  }

  public String getComponentName() {
    return "FindManager";
  }

  public FindUsagesManager getFindUsagesManager() {
    return myFindUsagesManager;
  }
}

