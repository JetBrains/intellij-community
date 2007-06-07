package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

public class LocalFsFinder implements FileLookup.Finder, FileLookup {

  public LookupFile find(@NotNull final String path) {
    String toFind = normalize(path);
    if (toFind.length() == 0) {
      File[] roots = File.listRoots();
      if (roots.length > 0) {
        toFind = roots[0].getAbsolutePath();
      }
    }
    return getLookupFile(path, LocalFileSystem.getInstance().findFileByIoFile(new File(toFind)));
  }

  private LookupFile getLookupFile(final String path, final VirtualFile vFile) {
    return vFile != null ? new VfsFile(this, vFile) : new IoFile(new File(path));
  }

  public String normalize(@NotNull final String path) {
    return new File(path.trim()).getAbsolutePath();
  }

  public String getSeparator() {
    return File.separator;
  }

  public static class FileChooserFilter implements LookupFilter {

    private FileChooserDescriptor myDescriptor;
    private boolean myShowHidden;

    public FileChooserFilter(final FileChooserDescriptor descriptor, boolean showHidden) {
      myDescriptor = descriptor;
      myShowHidden = showHidden;
    }

    public boolean isAccepted(final LookupFile file) {
      VirtualFile vFile = ((VfsFile)file).getFile();
      if (vFile == null) return false;
      return myDescriptor.isFileVisible(vFile, myShowHidden);
    }
  }

  public static class VfsFile implements LookupFile {
    private VirtualFile myFile;
    private LocalFsFinder myFinder;

    public VfsFile(LocalFsFinder finder, final VirtualFile file) {
      myFinder = finder;
      myFile = file;
    }

    public String getName() {
      if (myFile.getParent() == null && myFile.getName().length() == 0) return "/";
      return myFile.getName();
    }

    public String getAbsolutePath() {
      if (myFile.getParent() == null && myFile.getName().length() == 0) return "/";
      return myFile.getPresentableUrl();
    }

    public List<LookupFile> getChildren(final LookupFilter filter) {
      List<LookupFile> result = new ArrayList<LookupFile>();
      VirtualFile[] kids = myFile.getChildren();
      for (VirtualFile each : kids) {
        LookupFile eachFile = myFinder.getLookupFile(each.getPath(), each);
        if (eachFile != null && filter.isAccepted(eachFile)) {
          result.add(eachFile);
        }
      }

      return result;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public boolean exists() {
      return myFile.exists();
    }
  }

  public static class IoFile extends VfsFile {
    private File myIoFile;

    public IoFile(final File ioFile) {
      super(null, null);
      myIoFile = ioFile;
    }

    public String getName() {
      return myIoFile.getName();
    }

    public String getAbsolutePath() {
      return myIoFile.getAbsolutePath();
    }

    public List<LookupFile> getChildren(final LookupFilter filter) {
      List<LookupFile> result = new ArrayList<LookupFile>();
      File[] files = myIoFile.listFiles(new FileFilter() {
        public boolean accept(final File pathname) {
          return filter.isAccepted(new IoFile(pathname));
        }
      });
      if (files == null) return result;

      for (File each : files) {
        result.add(new IoFile(each));
      }

      return result;
    }

    public boolean exists() {
      return myIoFile.exists();
    }
  }
}
