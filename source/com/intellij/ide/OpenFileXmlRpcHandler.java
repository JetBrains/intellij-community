/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author mike
 */
public class OpenFileXmlRpcHandler implements ApplicationComponent {
  private static final String HANDLER_NAME = "fileOpener";
  private final XmlRpcServer myXmlRpcServer;

  public OpenFileXmlRpcHandler(final XmlRpcServer xmlRpcServer) {
    myXmlRpcServer = xmlRpcServer;
  }

  @NonNls
  public String getComponentName() {
    return "OpenFileXmlRpcHandler";
  }

  public void initComponent() {
    myXmlRpcServer.addHandler(HANDLER_NAME, new OpenFileHandler());
  }

  public void disposeComponent() {
    myXmlRpcServer.removeHandler(HANDLER_NAME);
  }

  public static class OpenFileHandler {
    @SuppressWarnings({"MethodMayBeStatic"})
    public boolean open(final String absolutePath) {
      final Application application = ApplicationManager.getApplication();

      application.invokeLater(new Runnable() {
        public void run() {
          final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
          if (openProjects.length == 0) return;
          Project project = openProjects[0];

          String correctPath = absolutePath.replace(File.separatorChar, '/');
          final VirtualFile[] virtualFiles = new VirtualFile[1];
          final String correctPath1 = correctPath;
          application.runWriteAction(new Runnable() {
            public void run() {
              virtualFiles[0] = LocalFileSystem.getInstance().refreshAndFindFileByPath(correctPath1);
            }
          });
          if (virtualFiles[0] == null) return;

          FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
          if (editorProviderManager.getProviders(project, virtualFiles[0]).length == 0) return;
          OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFiles[0]);
          FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
        }
      });

      return true;
    }
  }
}
