// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;

import java.util.*;

import static com.intellij.openapi.util.SystemInfo.isFileSystemCaseSensitive;
import static com.intellij.openapi.util.io.FileUtil.comparePaths;
import static com.intellij.openapi.util.text.StringUtil.startsWithIgnoreCase;
import static com.intellij.util.containers.ContainerUtil.*;
import static com.intellij.util.containers.ContainerUtilRt.newHashMap;

public class SvnMapping {
  private static final Comparator<String> FILE_PATHS_COMPARATOR = (path1, path2) -> comparePaths(path1, path2);

  @NotNull private final List<VirtualFile> myLonelyRoots = newArrayList();
  @NotNull private final TreeMap<String, RootUrlInfo> myFile2UrlMap = new TreeMap<>(FILE_PATHS_COMPARATOR);
  @NotNull private final Map<Url, RootUrlInfo> myUrl2FileMap = newHashMap();
  // no additional info. for caching only (convert roots)
  @NotNull private final ClearableLazyValue<List<VirtualFile>> myPreCalculatedUnderVcsRoots = new ClearableLazyValue<List<VirtualFile>>() {
    @NotNull
    @Override
    protected List<VirtualFile> compute() {
      return map(myFile2UrlMap.values(), RootUrlInfo::getVirtualFile);
    }
  };

  public void copyFrom(@NotNull SvnMapping other) {
    myFile2UrlMap.clear();
    myUrl2FileMap.clear();
    myLonelyRoots.clear();

    myFile2UrlMap.putAll(other.myFile2UrlMap);
    myUrl2FileMap.putAll(other.myUrl2FileMap);
    myLonelyRoots.addAll(other.myLonelyRoots);
    myPreCalculatedUnderVcsRoots.drop();
  }

  public void addAll(@NotNull Collection<RootUrlInfo> roots) {
    roots.forEach(this::add);
  }

  public void add(@NotNull RootUrlInfo rootInfo) {
    myFile2UrlMap.put(rootInfo.getPath(), rootInfo);
    myUrl2FileMap.put(rootInfo.getUrl(), rootInfo);
  }

  @NotNull
  public List<VirtualFile> getUnderVcsRoots() {
    return myPreCalculatedUnderVcsRoots.getValue();
  }

  @NotNull
  public List<RootUrlInfo> getAllCopies() {
    return newArrayList(myFile2UrlMap.values());
  }

  @Nullable
  public String getRootForPath(@NotNull String path) {
    return find(myFile2UrlMap.headMap(path, true).descendingKeySet(),
                root -> isFileSystemCaseSensitive ? path.startsWith(root) : startsWithIgnoreCase(path, root));
  }

  @NotNull
  public Collection<Url> getUrls() {
    return myUrl2FileMap.keySet();
  }

  @Nullable
  public RootUrlInfo byFile(@NotNull String path) {
    return myFile2UrlMap.get(path);
  }

  @Nullable
  public RootUrlInfo byUrl(@NotNull Url url) {
    return myUrl2FileMap.get(url);
  }

  public boolean isEmpty() {
    return myUrl2FileMap.isEmpty();
  }

  public void reportLonelyRoots(@NotNull Collection<VirtualFile> roots) {
    myLonelyRoots.addAll(roots);
  }

  @NotNull
  public List<VirtualFile> getLonelyRoots() {
    return myLonelyRoots;
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
