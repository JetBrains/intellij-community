package com.intellij.ide.impl;

import com.intellij.ide.EditorHighlighter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author MYakovlev
 * Date: Jul 1, 2002
 */
public class EditorHighlighterImpl extends EditorHighlighter implements ProjectComponent, FocusListener, CaretListener{
  private Project myProject;
  private RangeHighlighter mySegmentHighlighter;
  private Editor myEditor;

  public EditorHighlighterImpl(Project project){
    myProject = project;
  }

  public void projectOpened(){
  }

  public void projectClosed(){
  }

  public String getComponentName(){
    return "EditorHighlighter";
  }

  public void initComponent() { }

  public void disposeComponent(){
    releaseAll();
  }

  public void selectInEditor(VirtualFile file, int startOffset, int endOffset, boolean toSelectLine, boolean toUseNormalSelection){
    releaseAll();
    openEditor(file, endOffset);
    Editor editor = openEditor(file, startOffset);

    if(toUseNormalSelection && editor != null) {
      DocumentEx doc = (DocumentEx) editor.getDocument();
      if (toSelectLine){
        int lineNumber = doc.getLineNumber(startOffset);
        if (lineNumber >= 0 && lineNumber < doc.getLineCount()) {
          editor.getSelectionModel().setSelection(doc.getLineStartOffset(lineNumber), doc.getLineEndOffset(lineNumber) + doc.getLineSeparatorLength(lineNumber));
        }
      }
      else {
        editor.getSelectionModel().setSelection(startOffset, endOffset);
      }
      return;
    }

    TextAttributes selectionAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

    if (editor != null){
      releaseAll();

      if (toSelectLine){
        DocumentEx doc = (DocumentEx) editor.getDocument();
        int lineNumber = doc.getLineNumber(startOffset);
        if (lineNumber >= 0 && lineNumber < doc.getLineCount()){
          mySegmentHighlighter = editor.getMarkupModel().addRangeHighlighter(doc.getLineStartOffset(lineNumber),
                                                                             doc.getLineEndOffset(lineNumber) + doc.getLineSeparatorLength(lineNumber),
                                                                             HighlighterLayer.LAST + 1,
                                                                             selectionAttributes, HighlighterTargetArea.EXACT_RANGE);
        }
      }
      else{
        mySegmentHighlighter = editor.getMarkupModel().addRangeHighlighter(startOffset,
                                                                           endOffset,
                                                                           HighlighterLayer.LAST + 1,
                                                                           selectionAttributes, HighlighterTargetArea.EXACT_RANGE);
      }
      myEditor = editor;
      myEditor.getContentComponent().addFocusListener(this);
      myEditor.getCaretModel().addCaretListener(this);
    }
  }

  public void focusGained(FocusEvent e) {
    releaseAll();
  }

  public void focusLost(FocusEvent e) {
  }

  public void caretPositionChanged(CaretEvent e) {
    releaseAll();
  }

  private void releaseAll() {
    if (mySegmentHighlighter != null && myEditor != null){
      myEditor.getMarkupModel().removeHighlighter(mySegmentHighlighter);
      myEditor.getContentComponent().removeFocusListener(this);
      myEditor.getCaretModel().removeCaretListener(this);
      mySegmentHighlighter = null;
      myEditor = null;
    }
  }

  private Editor openEditor(VirtualFile file, int textOffset){
    if (file == null || !isValid(file)){
      return null;
    }
    OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, file, textOffset);
    return FileEditorManager.getInstance(myProject).openTextEditor(descriptor, false);
  }

  private static boolean isValid(final VirtualFile file){
    final boolean[] ret = new boolean[1];
    ApplicationManager.getApplication().runReadAction(
      new Runnable(){
        public void run(){
          ret[0] = file.isValid();
        }
      }
    );
    return ret[0];
  }

}
