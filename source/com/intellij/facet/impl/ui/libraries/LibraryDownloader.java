/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.ide.IdeBundle;
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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
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
  private LibraryDownloadInfo[] myLibraryInfos;
  private JComponent myParent;
  private @Nullable Project myProject;

  public LibraryDownloader(final LibraryDownloadInfo[] libraryInfos, final @Nullable Project project, JComponent parent) {
    myProject = project;
    myLibraryInfos = libraryInfos;
    myParent = parent;
  }

  public LibraryDownloader(final LibraryDownloadInfo[] libraryInfos, final @NotNull Project project) {
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
    final List<Pair<LibraryDownloadInfo, File>> downloadedFiles = new ArrayList<Pair<LibraryDownloadInfo, File>>();
    final List<VirtualFile> existingFiles = new ArrayList<VirtualFile>();
    final Exception[] exception = new Exception[]{null};
    final Ref<LibraryDownloadInfo> currentLibrary = new Ref<LibraryDownloadInfo>();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        try {
          for (int i = 0; i < myLibraryInfos.length; i++) {
            LibraryDownloadInfo info = myLibraryInfos[i];
            currentLibrary.set(info);
            if (indicator != null) {
              indicator.checkCanceled();
              indicator.setText(IdeBundle.message("progress.0.of.1.file.downloaded.text", i, myLibraryInfos.length));
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
    }, IdeBundle.message("progress.download.libraries.title"), true, myProject);

    if (exception[0] == null) {
      try {
        return moveToDir(existingFiles, downloadedFiles, dir);
      }
      catch (IOException e) {
        final String title = IdeBundle.message("progress.download.libraries.title");
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
      String message = IdeBundle.message("error.library.download.failed");
      if (currentLibrary.get() != null) {
        message += ": " + currentLibrary.get().getDownloadUrl();
      }
      final boolean tryAgain = IOExceptionDialog.showErrorDialog((IOException)exception[0],
                                                                 IdeBundle.message("progress.download.libraries.title"), message);
      if (tryAgain) {
        return doDownload(dir);
      }
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  private @Nullable VirtualFile chooseDirectoryForLibraries() {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(IdeBundle.message("dialog.directory.for.libraries.title"));

    final VirtualFile[] files;
    if (myProject != null) {
      files = FileChooser.chooseFiles(myProject, descriptor, myProject.getBaseDir());
    }
    else {
      files = FileChooser.chooseFiles(myParent, descriptor);
    }

    return files.length > 0 ? files[0] : null;
  }

  private static VirtualFile[] moveToDir(final List<VirtualFile> existingFiles, final List<Pair<LibraryDownloadInfo, File>> downloadedFiles, final VirtualFile dir) throws IOException {
    List<VirtualFile> files = new ArrayList<VirtualFile>();

    final File ioDir = VfsUtil.virtualToIoFile(dir);
    for (Pair<LibraryDownloadInfo, File> pair : downloadedFiles) {
      final LibraryDownloadInfo info = pair.getFirst();
      final File toFile = generateName(info, ioDir);
      FileUtil.rename(pair.getSecond(), toFile);
      files.add(new WriteAction<VirtualFile>() {
        protected void run(final Result<VirtualFile> result) {
          final String url = VfsUtil.getUrlForLibraryRoot(toFile);
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(toFile);
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

  private static String getExpectedFileName(LibraryDownloadInfo info) {
    return info.getFileNamePrefix() + info.getFileNameSuffix();
  }

  private static File generateName(LibraryDownloadInfo info, final File dir) {
    String prefix = info.getFileNamePrefix();
    String suffix = info.getFileNameSuffix();
    File file = new File(dir, prefix + suffix);
    int count = 1;
    while (file.exists()) {
      file = new File(dir, prefix + "_" + count + suffix);
      count++;
    }
    return file;
  }

  private static void deleteFiles(final List<Pair<LibraryDownloadInfo, File>> pairs) {
    for (Pair<LibraryDownloadInfo, File> pair : pairs) {
      FileUtil.delete(pair.getSecond());
    }
    pairs.clear();
  }

  private static boolean download(final LibraryDownloadInfo libraryInfo, final long existingFileSize, final List<Pair<LibraryDownloadInfo, File>> downloadedFiles) throws IOException {
    final String url = libraryInfo.getDownloadUrl();

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    String presentableUrl = libraryInfo.getPresentableUrl();
    indicator.setText2(IdeBundle.message("progress.download.jar.text", getExpectedFileName(libraryInfo), presentableUrl));
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
