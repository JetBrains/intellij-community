package com.intellij.xdebugger.evaluation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerEditorsProvider {

  @NotNull
  public abstract FileType getFileType();

  @NotNull
  public abstract Document createDocument(@NotNull Project project, @NotNull String text);

}
