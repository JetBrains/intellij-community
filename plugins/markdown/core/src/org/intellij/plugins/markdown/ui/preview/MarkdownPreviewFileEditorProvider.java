package org.intellij.plugins.markdown.ui.preview;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.jetbrains.annotations.NotNull;

public class MarkdownPreviewFileEditorProvider extends WeighedFileEditorProvider {
  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    if (!MarkdownHtmlPanelProvider.hasAvailableProviders()) return false;
    FileType fileType = file.getFileType();
    return fileType == MarkdownFileType.INSTANCE ||
           ScratchUtil.isScratch(file) && LanguageUtil.getLanguageForPsi(project, file, fileType) == MarkdownLanguage.INSTANCE;
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new MarkdownPreviewFileEditor(project, file);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return "markdown-preview-editor";
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
  }
}
