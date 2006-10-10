/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class FileTypeAssocTable {
  private final Map<String, FileType> myExtensionMappings;
  private final List<Pair<FileNameMatcher, FileType>> myMatchingMappings;

  private FileTypeAssocTable(final Map<String, FileType> extensionMappings, final List<Pair<FileNameMatcher, FileType>> matchingMappings) {
    myExtensionMappings = new THashMap<String, FileType>(extensionMappings);
    myMatchingMappings = new ArrayList<Pair<FileNameMatcher, FileType>>(matchingMappings);
  }

  public FileTypeAssocTable() {
    this(Collections.<String, FileType>emptyMap(), Collections.<Pair<FileNameMatcher, FileType>>emptyList());
  }

  public boolean isAssociatedWith(FileType type, FileNameMatcher matcher) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      return myExtensionMappings.get(((ExtensionFileNameMatcher)matcher).getExtension()) == type;
    }

    for (Pair<FileNameMatcher, FileType> mapping : myMatchingMappings) {
      if (matcher.equals(mapping.getFirst()) && type == mapping.getSecond()) return true;
    }

    return false;
  }

  public void addAssociation(FileNameMatcher matcher, FileType type) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      myExtensionMappings.put(((ExtensionFileNameMatcher)matcher).getExtension(), type);
    }
    else {
      myMatchingMappings.add(new Pair<FileNameMatcher, FileType>(matcher, type));
    }
  }

  public boolean removeAssociation(FileNameMatcher matcher, FileType type) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      String extension = ((ExtensionFileNameMatcher)matcher).getExtension();
      if (myExtensionMappings.get(extension) == type) {
        myExtensionMappings.remove(extension);
        return true;
      }
      return false;
    }

    List<Pair<FileNameMatcher, FileType>> copy = new ArrayList<Pair<FileNameMatcher, FileType>>(myMatchingMappings);
    for (Pair<FileNameMatcher, FileType> assoc : copy) {
      if (matcher.equals(assoc.getFirst())) {
        myMatchingMappings.remove(assoc);
        return true;
      }
    }

    return false;
  }

  public boolean removeAllAssociations(FileType type) {
    boolean changed = false;
    Set<String> exts = myExtensionMappings.keySet();
    String[] extsStrings = exts.toArray(new String[exts.size()]);
    for (String s : extsStrings) {
      if (myExtensionMappings.get(s) == type) {
        myExtensionMappings.remove(s);
        changed = true;
      }
    }

    List<Pair<FileNameMatcher, FileType>> copy = new ArrayList<Pair<FileNameMatcher, FileType>>(myMatchingMappings);
    for (Pair<FileNameMatcher, FileType> assoc : copy) {
      if (assoc.getSecond() == type) {
        myMatchingMappings.remove(assoc);
        changed = true;
      }
    }

    return changed;
  }

  @Nullable
  public FileType findAssociatedFileType(@NotNull @NonNls String fileName) {
    for (Pair<FileNameMatcher, FileType> mapping : myMatchingMappings) {
      if (mapping.getFirst().accept(fileName)) return mapping.getSecond();
    }

    return myExtensionMappings.get(FileUtil.getExtension(fileName));
  }

  @Nullable
  public FileType findAssociatedFileType(final FileNameMatcher matcher) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      return myExtensionMappings.get(((ExtensionFileNameMatcher)matcher).getExtension());
    }

    for (Pair<FileNameMatcher, FileType> mapping : myMatchingMappings) {
      if (matcher.equals(mapping.getFirst())) return mapping.getSecond();
    }

    return null;
  }

  @Deprecated
  @NotNull
  public String[] getAssociatedExtensions(FileType type) {
    Map<String, FileType> extMap = myExtensionMappings;
    return getAssociatedExtensions(extMap, type);
  }

  @NotNull
  private static String[] getAssociatedExtensions(Map<String, FileType> extMap, FileType type) {
    List<String> exts = new ArrayList<String>();
    for (String ext : extMap.keySet()) {
      if (extMap.get(ext) == type) {
        exts.add(ext);
      }
    }
    return exts.toArray(new String[exts.size()]);
  }

  @NotNull
  public FileTypeAssocTable copy() {
    return new FileTypeAssocTable(myExtensionMappings, myMatchingMappings);
  }

  @NotNull
  public List<FileNameMatcher> getAssociations(final FileType type) {
    List<FileNameMatcher> result = new ArrayList<FileNameMatcher>();
    for (Pair<FileNameMatcher, FileType> mapping : myMatchingMappings) {
      if (mapping.getSecond() == type) {
        result.add(mapping.getFirst());
      }
    }

    for (Map.Entry<String,FileType> entries : myExtensionMappings.entrySet()) {
      if (entries.getValue() == type) {
        result.add(new ExtensionFileNameMatcher(entries.getKey()));
      }
    }

    return result;
  }

  public boolean hasAssociationsFor(final FileType fileType) {
    if (myExtensionMappings.values().contains(fileType)) return true;
    for (Pair<FileNameMatcher, FileType> mapping : myMatchingMappings) {
      if (mapping.getSecond() == fileType) return true;
    }
    return false;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FileTypeAssocTable that = (FileTypeAssocTable)o;

    if (!myExtensionMappings.equals(that.myExtensionMappings)) return false;
    if (!myMatchingMappings.equals(that.myMatchingMappings)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myExtensionMappings.hashCode();
    result = 31 * result + myMatchingMappings.hashCode();
    return result;
  }
}
