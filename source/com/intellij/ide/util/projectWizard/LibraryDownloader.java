/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.javaee.J2EEBundle;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.UrlConnectionUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IOExceptionDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class LibraryDownloader {
  private LibraryInfo[] myLibraryInfos;
  private JComponent myParent;
  private @Nullable Project myProject;

  public LibraryDownloader(final LibraryInfo[] libraryInfos, final @Nullable Project project, JComponent parent) {
    myProject = project;
    myLibraryInfos = libraryInfos;
    myParent = parent;
  }

  public LibraryDownloader(final LibraryInfo[] libraryInfos, final @NotNull Project project) {
    myLibraryInfos = libraryInfos;
    myProject = project;
  }

  public VirtualFile[] download() {
    final VirtualFile dir = chooseDirectoryForLibraries();
    if (dir != null) {
      return doDownload(dir);
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  private VirtualFile[] doDownload(final VirtualFile dir) {
    HttpConfigurable.getInstance().setAuthenticator();
    final List<Pair<LibraryInfo, File>> downloadedFiles = new ArrayList<Pair<LibraryInfo, File>>();
    final List<VirtualFile> existingFiles = new ArrayList<VirtualFile>();
    final Exception[] exception = new Exception[]{null};

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        try {
          for (int i = 0; i < myLibraryInfos.length; i++) {
            LibraryInfo info = myLibraryInfos[i];
            if (indicator != null) {
              indicator.checkCanceled();
              indicator.setText(J2EEBundle.message("progress.0.of.1.file.downloaded.text", i, myLibraryInfos.length));
            }

            final VirtualFile existing = dir.findChild(getExpectedFileName(info));
            long size = existing != null ? existing.getLength() : -1;

            if (!download(info, size, downloadedFiles)) {
              existingFiles.add(existing);
            }
          }
        }
        catch (ProcessCanceledException e) {
          exception[0] = e;
        }
        catch (IOException e) {
          exception[0] = e;
        }
      }
    }, J2EEBundle.message("progress.download.libraries.title"), true, myProject);

    if (exception[0] == null) {
      try {
        return moveToDir(existingFiles, downloadedFiles, dir);
      }
      catch (IOException e) {
        final String title = J2EEBundle.message("progress.download.libraries.title");
        if (myProject != null) {
          Messages.showErrorDialog(myProject, title, e.getMessage());
        }
        else {
          Messages.showErrorDialog(myParent, title, e.getMessage());
        }
        return VirtualFile.EMPTY_ARRAY;
      }
    }

    deleteFiles(downloadedFiles);
    if (exception[0] instanceof IOException) {
      final boolean tryAgain = IOExceptionDialog.showErrorDialog((IOException)exception[0],
                                                                 J2EEBundle.message("progress.download.libraries.title"),
                                                                 J2EEBundle.message("error.library.download.failed"));
      if (tryAgain) {
        return doDownload(dir);
      }
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  private @Nullable VirtualFile chooseDirectoryForLibraries() {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(J2EEBundle.message("dialog.directory.for.libraries.title"));

    final VirtualFile[] files;
    if (myProject != null) {
      files = FileChooser.chooseFiles(myProject, descriptor, myProject.getProjectFile().getParent());
    }
    else {
      files = FileChooser.chooseFiles(myParent, descriptor);
    }

    return files.length > 0 ? files[0] : null;
  }

  private static VirtualFile[] moveToDir(final List<VirtualFile> existingFiles, final List<Pair<LibraryInfo, File>> downloadedFiles, final VirtualFile dir) throws IOException {
    List<VirtualFile> files = new ArrayList<VirtualFile>();

    final File ioDir = VfsUtil.virtualToIoFile(dir);
    for (Pair<LibraryInfo, File> pair : downloadedFiles) {
      final LibraryInfo info = pair.getFirst();
      final File toFile = generateName(info.getExpectedJarName(), info.getVersion(), ioDir);
      FileUtil.rename(pair.getSecond(), toFile);
      files.add(new WriteAction<VirtualFile>() {
        protected void run(final Result<VirtualFile> result) {
          final String url = VfsUtil.getUrlForLibraryRoot(toFile);
          result.setResult(VirtualFileManager.getInstance().refreshAndFindFileByUrl(url));
        }
      }.execute().getResultObject());
    }

    for (final VirtualFile file : existingFiles) {
      files.add(new WriteAction<VirtualFile>() {
        protected void run(final Result<VirtualFile> result) {
          final String url = VfsUtil.getUrlForLibraryRoot(VfsUtil.virtualToIoFile(file));
          result.setResult(VirtualFileManager.getInstance().refreshAndFindFileByUrl(url));
        }

      }.execute().getResultObject());
    }

    return files.toArray(new VirtualFile[files.size()]);
  }

  private static String getExpectedFileName(LibraryInfo info) {
    final String name = info.getExpectedJarName();
    final int dot = name.lastIndexOf('.');
    return name.substring(0, dot) + "-" + info.getVersion() + name.substring(dot);
  }

  private static File generateName(final String baseName, final String version, final File dir) {
    int index = baseName.lastIndexOf('.');
    String prefix = (index != -1 ? baseName.substring(0, index) : baseName) + "-" + version;
    String suffix = index != -1 ? baseName.substring(index) : "";
    File file = new File(dir, prefix + suffix);
    int count = 1;
    while (file.exists()) {
      file = new File(dir, prefix + "_" + count++ + suffix);
    }
    return file;
  }

  private static void deleteFiles(final List<Pair<LibraryInfo, File>> pairs) {
    for (Pair<LibraryInfo, File> pair : pairs) {
      FileUtil.delete(pair.getSecond());
    }
    pairs.clear();
  }

  private static boolean download(final LibraryInfo libraryInfo, final long existingFileSize, final List<Pair<LibraryInfo, File>> downloadedFiles) throws IOException {
    final String url = libraryInfo.getDownloadingUrl();
    if (url == null) return true;

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setText2(J2EEBundle.message("progress.download.jar.text", libraryInfo.getExpectedJarName(), libraryInfo.getPresentableUrl()));
    File tempFile = null;
    HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();

    InputStream input = null;
    BufferedOutputStream output = null;

    boolean deleteFile = true;

    try {
      final int responseCode = connection.getResponseCode();
      if (responseCode != HttpURLConnection.HTTP_OK) {
        throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
      }

      final int size = connection.getContentLength();
      if (size != -1 && size == existingFileSize) {
        return false;
      }

      indicator.setIndeterminate(true);
      tempFile = FileUtil.createTempFile("downloaded", "jar");
      input = UrlConnectionUtil.getConnectionInputStreamWithException(connection, indicator);
      output = new BufferedOutputStream(new FileOutputStream(tempFile));
      indicator.setIndeterminate(size == -1);

      int len;
      final byte[] buf = new byte[1024];
      int count = 0;
      while ((len = input.read(buf)) > 0) {
        indicator.checkCanceled();
        count += len;
        if (size > 0) {
          indicator.setFraction((double)count / size);
        }
        output.write(buf, 0, len);
      }

      deleteFile = false;
      downloadedFiles.add(Pair.create(libraryInfo, tempFile));
      return true;
    }
    finally {
      if (input != null) {
        input.close();
      }
      if (output != null) {
        output.close();
      }
      if (deleteFile && tempFile != null) {
        FileUtil.delete(tempFile);
      }
      connection.disconnect();
    }
  }
}
