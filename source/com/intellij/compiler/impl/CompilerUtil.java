/**
 * created at Jan 3, 2002
 * @author Jeka
 */
package com.intellij.compiler.impl;

import com.intellij.compiler.progress.CompilerProgressIndicator;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;
import java.io.File;
import java.io.FileFilter;
import java.util.Collection;
import java.util.Map;

public class CompilerUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompilerUtil");

  public static String quotePath(String path) {
    if(path != null && path.indexOf(' ') != -1) {
      path = path.replaceAll("\\\\", "\\\\\\\\");
      path = "\"" + path + "\"";
    }
    return path;
  }

  public static final FileFilter CLASS_FILES_FILTER = new FileFilter() {
    public boolean accept(File pathname) {
      if (pathname.isDirectory()) {
        return true;
      }
      final int dotIndex = pathname.getName().lastIndexOf('.');
      if (dotIndex > 0) {
        String extension = pathname.getName().substring(dotIndex);
        //noinspection HardCodedStringLiteral
        if (extension.equalsIgnoreCase(".class")) {
          return true;
        }
      }
      return false;
    }
  };
  public static void collectFiles(Collection<File> container, File rootDir, FileFilter fileFilter) {
    final File[] files = rootDir.listFiles(fileFilter);
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        collectFiles(container, file, fileFilter);
      }
      else {
        container.add(file);
      }
    }
  }

  public static void refreshPaths(final String[] paths) {
    if (paths.length == 0) {
      return;
    }
    doRefresh(new Runnable() {
      public void run() {
        for (String path : paths) {
          final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.replace(File.separatorChar, '/'));
          if (file != null) {
            file.refresh(false, false);
          }
        }
      }
    });
  }


  /**
   * must not be called inside ReadAction
   * @param files
   */
  public static void refreshIOFiles(final File[] files) {
    if (files.length == 0) {
      return;
    }
    doRefresh(new Runnable() {
      public void run() {
        for (File file1 : files) {
          final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
          if (file != null) {
            file.refresh(false, false);
          }
        }
      }
    });
  }

  public static void refreshIOFile(final File file) {
    doRefresh(new Runnable() {
      public void run() {
        final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (vFile != null) {
          vFile.refresh(false, false);
        }
      }
    });
  }

  public static void refreshVirtualFiles(final VirtualFile[] files) {
    doRefresh(new Runnable() {
      public void run() {
        for (VirtualFile file : files) {
          file.refresh(false, false);
        }
      }
    });
  }
  // file -> true/false for refresh recursively/non recursively coorespondingly
  public static void refreshVirtualFiles(final Map<VirtualFile, Boolean> filesToRefresh) {
    doRefresh(new Runnable() {
      public void run() {
        for (VirtualFile virtualFile : filesToRefresh.keySet()) {
          boolean recursively = filesToRefresh.get(virtualFile).booleanValue();
          virtualFile.refresh(false, recursively);
        }
      }
    });
  }

  private static void doRefresh(final Runnable refreshRunnable) {
    final Application applicationEx = ApplicationManager.getApplication();
    if (applicationEx.isDispatchThread()) {
      applicationEx.runWriteAction(refreshRunnable);
    }
    else {
      try {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        ModalityState modalityState;
        if (progress instanceof CompilerProgressIndicator){
          Window window = ((CompilerProgressIndicator)progress).getWindow();
          modalityState = window != null ? ModalityState.stateForComponent(window) : ModalityState.NON_MMODAL;
        }
        else{
          modalityState = ModalityState.NON_MMODAL;
        }
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(refreshRunnable);
          }
        }, modalityState);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

}
