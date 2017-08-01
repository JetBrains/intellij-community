/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class SvnMapping {
  private final List<VirtualFile> myLonelyRoots;
  private final TreeMap<String, RootUrlInfo> myFile2UrlMap;
  private final Map<String, RootUrlInfo> myUrl2FileMap;
  // no additional info. for caching only (convert roots)
  private List<VirtualFile> myPreCalculatedUnderVcsRoots;

  public SvnMapping() {
    myFile2UrlMap = new TreeMap<>(FilePathsComparator.getInstance());
    myUrl2FileMap = new HashMap<>();
    myLonelyRoots = new ArrayList<>();

    myPreCalculatedUnderVcsRoots = null;
  }

  public void copyFrom(final SvnMapping other) {
    myFile2UrlMap.clear();
    myUrl2FileMap.clear();
    myLonelyRoots.clear();

    myFile2UrlMap.putAll(other.myFile2UrlMap);
    myUrl2FileMap.putAll(other.myUrl2FileMap);
    myLonelyRoots.addAll(other.myLonelyRoots);
    myPreCalculatedUnderVcsRoots = null;
  }

  public void addAll(final Collection<RootUrlInfo> roots) {
    for (RootUrlInfo rootInfo : roots) {
      final VirtualFile file = rootInfo.getVirtualFile();
      final File ioFile = virtualToIoFile(file);
      
      myFile2UrlMap.put(ioFile.getAbsolutePath(), rootInfo);
      myUrl2FileMap.put(rootInfo.getAbsoluteUrl(), rootInfo);
    }
  }

  public void add(final RootUrlInfo rootInfo) {
    final VirtualFile file = rootInfo.getVirtualFile();
    final File ioFile = virtualToIoFile(file);

    myFile2UrlMap.put(ioFile.getAbsolutePath(), rootInfo);
    myUrl2FileMap.put(rootInfo.getAbsoluteUrl(), rootInfo);
  }

  public List<VirtualFile> getUnderVcsRoots() {
    if (myPreCalculatedUnderVcsRoots == null) {
      myPreCalculatedUnderVcsRoots = new ArrayList<>();
      for (RootUrlInfo info : myFile2UrlMap.values()) {
        myPreCalculatedUnderVcsRoots.add(info.getVirtualFile());
      }
    }
    return myPreCalculatedUnderVcsRoots;
  }

  public List<RootUrlInfo> getAllCopies() {
    return new ArrayList<>(myFile2UrlMap.values());
  }

  @Nullable
  public String getRootForPath(@NotNull final String path) {
    String floor = myFile2UrlMap.floorKey(path);
    if (floor == null) return null;
    NavigableMap<String, RootUrlInfo> head = myFile2UrlMap.headMap(floor, true);
    for (String root : head.descendingKeySet()) {
      if (startsWithForPaths(root, path)) return root;
    }
    return null;
  }

  public Collection<String> getUrls() {
    return myUrl2FileMap.keySet();
  }

  public RootUrlInfo byFile(final String path) {
    return myFile2UrlMap.get(path);
  }

  public RootUrlInfo byUrl(final String url) {
    return myUrl2FileMap.get(url);
  }

  public boolean isEmpty() {
    return myUrl2FileMap.isEmpty();
  }

  public void reportLonelyRoots(final Collection<VirtualFile> roots) {
    myLonelyRoots.addAll(roots);
  }

  public List<VirtualFile> getLonelyRoots() {
    return myLonelyRoots;
  }

  private static class FilePathsComparator implements Comparator<String> {
    private final static FilePathsComparator ourInstance = new FilePathsComparator();

    public static FilePathsComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(String o1, String o2) {
      return FileUtil.comparePaths(o1, o2);
    }
  }
  
  private boolean startsWithForPaths(final String parent, final String child) {
    return SystemInfo.isFileSystemCaseSensitive ? child.startsWith(parent) : (StringUtil.startsWithIgnoreCase(child, parent));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SvnMapping mapping = (SvnMapping)o;

    if (!myFile2UrlMap.equals(mapping.myFile2UrlMap)) return false;
    if (!myLonelyRoots.equals(mapping.myLonelyRoots)) return false;
    if (!myUrl2FileMap.equals(mapping.myUrl2FileMap)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myLonelyRoots.hashCode();
    result = 31 * result + myFile2UrlMap.hashCode();
    result = 31 * result + myUrl2FileMap.hashCode();
    return result;
  }
}
