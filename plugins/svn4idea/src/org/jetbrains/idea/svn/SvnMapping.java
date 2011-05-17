/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class SvnMapping {
  private final List<VirtualFile> myLonelyRoots;
  private final TreeMap<String, RootUrlInfo> myFile2UrlMap;
  private final Map<String, RootUrlInfo> myUrl2FileMap;
  private boolean myRootsDifferFromSettings;
  // no additional info. for caching only (convert roots)
  private List<VirtualFile> myPreCalculatedUnderVcsRoots;

  public SvnMapping() {
    myFile2UrlMap = new TreeMap<String, RootUrlInfo>(FilePathsComparator.getInstance());
    myUrl2FileMap = new HashMap<String, RootUrlInfo>();
    myLonelyRoots = new ArrayList<VirtualFile>();

    myPreCalculatedUnderVcsRoots = null;

    myRootsDifferFromSettings = false;
  }

  public void copyFrom(final SvnMapping other) {
    myFile2UrlMap.clear();
    myUrl2FileMap.clear();
    myLonelyRoots.clear();

    myFile2UrlMap.putAll(other.myFile2UrlMap);
    myUrl2FileMap.putAll(other.myUrl2FileMap);
    myLonelyRoots.addAll(other.myLonelyRoots);
    myRootsDifferFromSettings = other.myRootsDifferFromSettings;
    myPreCalculatedUnderVcsRoots = null;
  }

  public void addAll(final Collection<RootUrlInfo> roots) {
    for (RootUrlInfo rootInfo : roots) {
      final VirtualFile file = rootInfo.getVirtualFile();
      final File ioFile = new File(file.getPath());
      
      myRootsDifferFromSettings |= ! rootInfo.getRoot().getPath().equals(file.getPath());

      myFile2UrlMap.put(ioFile.getAbsolutePath(), rootInfo);
      myUrl2FileMap.put(rootInfo.getAbsoluteUrl(), rootInfo);
    }
  }

  public void add(final RootUrlInfo rootInfo) {
    final VirtualFile file = rootInfo.getVirtualFile();
    final File ioFile = new File(file.getPath());

    myRootsDifferFromSettings |= ! rootInfo.getRoot().getPath().equals(file.getPath());

    myFile2UrlMap.put(ioFile.getAbsolutePath(), rootInfo);
    myUrl2FileMap.put(rootInfo.getAbsoluteUrl(), rootInfo);
  }

  public List<VirtualFile> getUnderVcsRoots() {
    if (myPreCalculatedUnderVcsRoots == null) {
      myPreCalculatedUnderVcsRoots = new ArrayList<VirtualFile>();
      for (RootUrlInfo info : myFile2UrlMap.values()) {
        myPreCalculatedUnderVcsRoots.add(info.getVirtualFile());
      }
    }
    return myPreCalculatedUnderVcsRoots;
  }

  public List<RootUrlInfo> getAllCopies() {
    return new ArrayList<RootUrlInfo>(myFile2UrlMap.values());
  }

  @Nullable
  public String getRootForPath(final String path) {
    return myFile2UrlMap.floorKey(path);
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

  public boolean isRootsDifferFromSettings() {
    return myRootsDifferFromSettings;
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
}
