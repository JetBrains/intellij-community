package com.intellij.ide.highlighter;

import com.intellij.ide.highlighter.custom.CustomFileHighlighter;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.jsp.JspSpiUtil;
import org.jetbrains.annotations.Nullable;

public class HighlighterFactory {
  public static EditorHighlighter createJavaHighlighter(EditorColorsScheme settings, LanguageLevel languageLevel){
    return createHighlighter(new JavaFileHighlighter(languageLevel), settings);
  }

  public static EditorHighlighter createHTMLHighlighter(EditorColorsScheme settings){
    SyntaxHighlighter highlighter = new HtmlFileHighlighter();
    return createHighlighter(highlighter, settings);
  }

  public static EditorHighlighter createHighlighter(SyntaxHighlighter highlighter, EditorColorsScheme settings) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(highlighter, settings);
  }

  public static EditorHighlighter createXMLHighlighter(EditorColorsScheme settings){
    return createHighlighter(new XmlFileHighlighter(), settings);
  }

  @Nullable
  public static EditorHighlighter createJSPHighlighter(EditorColorsScheme settings, Project project, VirtualFile virtualFile) {
    return JspSpiUtil.createJSPHighlighter(settings, project, virtualFile);
  }

  public static EditorHighlighter createCustomHighlighter(SyntaxTable syntaxTable, EditorColorsScheme settings){
    return createHighlighter(new CustomFileHighlighter(syntaxTable), settings);
  }

  public static EditorHighlighter createHighlighter(Project project, String fileName) {
    return createHighlighter(EditorColorsManager.getInstance().getGlobalScheme(), fileName, project);
  }

  public static EditorHighlighter createHighlighter(Project project, VirtualFile file) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file);
  }

  public static EditorHighlighter createHighlighter(Project project, FileType fileType) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType);
  }

  public static EditorHighlighter createHighlighter(EditorColorsScheme settings, String fileName, Project project) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return createHighlighter(fileType, settings, project);
  }

  public static EditorHighlighter createHighlighter(FileType fileType, EditorColorsScheme settings, Project project) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, settings, project);
  }

  public static EditorHighlighter createHighlighter(VirtualFile vFile, EditorColorsScheme settings, Project project) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(vFile, settings, project);
  }
}