package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;

import java.util.ArrayList;
import java.util.List;

public class ObjectsConvertor {
  public static List<VirtualFile> fp2vf(final List<FilePath> in) {
    return convert(in, new Convertor<FilePath, VirtualFile>() {
      public VirtualFile convert(final FilePath fp) {
        return fp.getVirtualFile();
      }
    });
  }

  public static List<FilePath> vf2fp(final List<VirtualFile> in) {
    return convert(in, new Convertor<VirtualFile, FilePath>() {
      public FilePath convert(final VirtualFile vf) {
        return new FilePathImpl(vf);
      }
    });
  }

  public static <T,S> List<S> convert(final List<T> in, final Convertor<T,S> convertor) {
    final List<S> out = new ArrayList<S>();
    for (T t : in) {
      out.add(convertor.convert(t));
    }
    return out;
  }
}
