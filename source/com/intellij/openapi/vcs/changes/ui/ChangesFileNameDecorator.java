package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;

/**
 * @author yole
 */
public abstract class ChangesFileNameDecorator {
  public static ChangesFileNameDecorator getInstance(Project project) {
    return ServiceManager.getService(project, ChangesFileNameDecorator.class);
  }

  public abstract void appendFileName(final ChangesBrowserNodeRenderer renderer, final VirtualFile vFile, final String fileName, 
                                      final Color color, boolean highlightProblems);
}