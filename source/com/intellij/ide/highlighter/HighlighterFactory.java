package com.intellij.ide.highlighter;

import com.intellij.ide.highlighter.custom.CustomFileHighlighter;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LexerHighlighter;
import com.intellij.openapi.fileTypes.FileHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainFileHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;

public class HighlighterFactory {
  public static LexerHighlighter createJavaHighlighter(EditorColorsScheme settings, LanguageLevel languageLevel){
    return createHighlighter(new JavaFileHighlighter(languageLevel), settings);
  }

  public static LexerHighlighter createHTMLHighlighter(EditorColorsScheme settings){
    FileHighlighter highlighter = new HtmlFileHighlighter();
    return createHighlighter(highlighter, settings);
  }

  public static LexerHighlighter createHighlighter(FileHighlighter highlighter, EditorColorsScheme settings) {
    return new LexerHighlighter(highlighter, settings);
  }

  public static LexerHighlighter createXMLHighlighter(EditorColorsScheme settings){
    return createHighlighter(new XmlFileHighlighter(), settings);
  }

  public static LexerHighlighter createJSPHighlighter(EditorColorsScheme settings, Project project){
    final LanguageLevel languageLevel = project != null ? PsiManager.getInstance(project).getEffectiveLanguageLevel() : LanguageLevel.HIGHEST;
    return createHighlighter(new JspFileHighlighter(languageLevel), settings);
  }

  public static LexerHighlighter createJSPHighlighter(EditorColorsScheme settings, LanguageLevel languageLevel){
    return createHighlighter(new JspFileHighlighter(languageLevel), settings);
  }

  public static LexerHighlighter createCustomHighlighter(SyntaxTable syntaxTable, EditorColorsScheme settings){
    return createHighlighter(new CustomFileHighlighter(syntaxTable), settings);
  }

  public static LexerHighlighter createHighlighter(Project project, String fileName) {
    return createHighlighter(EditorColorsManager.getInstance().getGlobalScheme(), fileName, project);
  }

  public static LexerHighlighter createHighlighter(Project project, VirtualFile file) {
    return createHighlighter(project, file.getFileType());
  }

  public static LexerHighlighter createHighlighter(Project project, FileType fileType) {
    return createHighlighter(fileType, EditorColorsManager.getInstance().getGlobalScheme(), project);
  }

  public static LexerHighlighter createHighlighter(EditorColorsScheme settings, String fileName, Project project) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return createHighlighter(fileType, settings, project);
  }

  public static LexerHighlighter createHighlighter(FileType fileType, EditorColorsScheme settings, Project project) {
    FileHighlighter highlighter = fileType.getHighlighter(project);
    return createHighlighter(highlighter != null ? highlighter : new PlainFileHighlighter(), settings);
  }
}