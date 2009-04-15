package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;

import java.io.File;
import java.util.*;

public class SvnMapping {
  private final List<VirtualFile> myLonelyRoots;
  private final Map<String, RootUrlInfo> myFile2UrlMap;
  private final Map<String, RootUrlInfo> myUrl2FileMap;
  //private final List<VirtualFile> myPhysicalCopies;
  private boolean myRootsDifferFromSettings;
  // no additional info. for caching only (convert roots)
  private List<VirtualFile> myPreCalculatedUnderVcsRoots;

  public SvnMapping() {
    myFile2UrlMap = new HashMap<String, RootUrlInfo>();
    myUrl2FileMap = new HashMap<String, RootUrlInfo>();
    myLonelyRoots = new ArrayList<VirtualFile>();
    //myPhysicalCopies = new ArrayList<VirtualFile>();

    myPreCalculatedUnderVcsRoots = null;

    myRootsDifferFromSettings = false;
  }

  public void copyFrom(final SvnMapping other) {
    myFile2UrlMap.clear();
    myUrl2FileMap.clear();
    myLonelyRoots.clear();
    //myPhysicalCopies.clear();

    myFile2UrlMap.putAll(other.myFile2UrlMap);
    myUrl2FileMap.putAll(other.myUrl2FileMap);
    myLonelyRoots.addAll(other.myLonelyRoots);
    //myPhysicalCopies.addAll(other.myPhysicalCopies);
    myRootsDifferFromSettings = other.myRootsDifferFromSettings;
    myPreCalculatedUnderVcsRoots = null;
  }

  public void add(final VirtualFile file, final VirtualFile root, final SVNInfo info, final SVNURL repositoryUrl) {
    final File ioFile = new File(file.getPath());
    final SVNURL url = info.getURL();
    final RootUrlInfo rootInfo = new RootUrlInfo(repositoryUrl, url, SvnFormatSelector.getWorkingCopyFormat(ioFile), file, root);

    myRootsDifferFromSettings |= ! root.getPath().equals(file.getPath());

    myFile2UrlMap.put(ioFile.getAbsolutePath(), rootInfo);
    myUrl2FileMap.put(rootInfo.getAbsoluteUrl(), rootInfo);
  }

  /*
  // todo check called. dont forget
  public void setPhysicalCopies(final List<VirtualFile> roots) {
    myPhysicalCopies.clear();
    myPhysicalCopies.addAll(roots);
  }*/

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

  public Collection<String> getFileRoots() {
    return myFile2UrlMap.keySet();
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

  /*public List<VirtualFile> getPhysicalCopies() {
    return myPhysicalCopies;
  }*/

  public boolean isRootsDifferFromSettings() {
    return myRootsDifferFromSettings;
  }

  public void reportLonelyRoot(final VirtualFile root) {
    myLonelyRoots.add(root);
  }

  public List<VirtualFile> getLonelyRoots() {
    return myLonelyRoots;
  }
}
