package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ObjectsConvertor {
  private static final Convertor<FilePath, VirtualFile> FILEPATH_TO_VIRTUAL = new Convertor<FilePath, VirtualFile>() {
    public VirtualFile convert(FilePath fp) {
      return fp.getVirtualFile();
    }
  };

  private static final Convertor<VirtualFile, FilePath> VIRTUAL_FILEPATH = new Convertor<VirtualFile, FilePath>() {
    public FilePath convert(VirtualFile vf) {
      return new FilePathImpl(vf);
    }
  };

  private static final Convertor<FilePath, File> FILEPATH_FILE = new Convertor<FilePath, File>() {
    public File convert(FilePath fp) {
      return fp.getIOFile();
    }
  };

  public static List<VirtualFile> fp2vf(final List<FilePath> in) {
    return convert(in, FILEPATH_TO_VIRTUAL);
  }

  public static List<FilePath> vf2fp(final List<VirtualFile> in) {
    return convert(in, VIRTUAL_FILEPATH);
  }

  public static List<File> fp2jiof(final Collection<FilePath> in) {
    return convert(in, FILEPATH_FILE);
  }

  public static <T,S> List<S> convert(final Collection<T> in, final Convertor<T,S> convertor) {
    final List<S> out = new ArrayList<S>();
    for (T t : in) {
      out.add(convertor.convert(t));
    }
    return out;
  }
}
