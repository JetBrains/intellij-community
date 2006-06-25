package com.intellij.ide.highlighter;

import com.intellij.ide.highlighter.custom.CustomFileHighlighter;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.lang.jsp.JspEditorHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;

public class HighlighterFactory {
  public static LexerEditorHighlighter createJavaHighlighter(EditorColorsScheme settings, LanguageLevel languageLevel){
    return createHighlighter(new JavaFileHighlighter(languageLevel), settings);
  }

  public static LexerEditorHighlighter createHTMLHighlighter(EditorColorsScheme settings){
    SyntaxHighlighter highlighter = new HtmlFileHighlighter();
    return createHighlighter(highlighter, settings);
  }

  public static LexerEditorHighlighter createHighlighter(SyntaxHighlighter highlighter, EditorColorsScheme settings) {
    return new LexerEditorHighlighter(highlighter, settings);
  }

  public static LexerEditorHighlighter createXMLHighlighter(EditorColorsScheme settings){
    return createHighlighter(new XmlFileHighlighter(), settings);
  }

  public static LexerEditorHighlighter createJSPHighlighter(EditorColorsScheme settings, Project project, VirtualFile virtualFile) {
    return new JspEditorHighlighter(settings, project, virtualFile);
  }

  public static LexerEditorHighlighter createCustomHighlighter(SyntaxTable syntaxTable, EditorColorsScheme settings){
    return createHighlighter(new CustomFileHighlighter(syntaxTable), settings);
  }

  public static LexerEditorHighlighter createHighlighter(Project project, String fileName) {
    return createHighlighter(EditorColorsManager.getInstance().getGlobalScheme(), fileName, project);
  }

  public static LexerEditorHighlighter createHighlighter(Project project, VirtualFile file) {
    return createHighlighter(file, EditorColorsManager.getInstance().getGlobalScheme(), project);
  }

  public static LexerEditorHighlighter createHighlighter(Project project, FileType fileType) {
    return createHighlighter(fileType, EditorColorsManager.getInstance().getGlobalScheme(), project);
  }

  public static LexerEditorHighlighter createHighlighter(EditorColorsScheme settings, String fileName, Project project) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return createHighlighter(fileType, settings, project);
  }

  public static LexerEditorHighlighter createHighlighter(FileType fileType, EditorColorsScheme settings, Project project) {
    if (fileType == StdFileTypes.JSP) {
      return createJSPHighlighter(settings, project, null);
    }

    SyntaxHighlighter highlighter = fileType.getHighlighter(project, null);
    return createHighlighter(highlighter != null ? highlighter : new PlainSyntaxHighlighter(), settings);
  }

  public static LexerEditorHighlighter createHighlighter(VirtualFile vFile, EditorColorsScheme settings, Project project) {
    final FileType fileType = vFile.getFileType();
    if (fileType == StdFileTypes.JSP) {
      return createJSPHighlighter(settings, project, vFile);
    }

    SyntaxHighlighter highlighter = fileType.getHighlighter(project, vFile);
    return createHighlighter(highlighter != null ? highlighter : new PlainSyntaxHighlighter(), settings);
  }
}