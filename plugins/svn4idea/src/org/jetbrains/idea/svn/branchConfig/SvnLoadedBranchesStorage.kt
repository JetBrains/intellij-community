// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SmallMapSerializer;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class SvnLoadedBranchesStorage {
  private final Object myLock;
  private SmallMapSerializer<String, Map<String, Collection<SvnBranchItem>>> myState;
  private final File myFile;
  private final Project myProject;

  public SvnLoadedBranchesStorage(final Project project) {
    myProject = project;
    final File vcsFile = new File(PathManager.getSystemPath(), "vcs");
    File file = new File(vcsFile, "svn_branches");
    file.mkdirs();
    myFile = new File(file, project.getLocationHash());
    myLock = new Object();
  }
  
  @Nullable
  public Collection<SvnBranchItem> get(final String url) {
    synchronized (myLock) {
      if (myState == null) return null;
      Map<String, Collection<SvnBranchItem>> map = myState.get("");
      return map == null ? null : map.get(SvnBranchConfigurationNew.Companion.ensureEndSlash(url));
    }
  }
  
  public void activate() {
    synchronized (myLock) {
      myState = new SmallMapSerializer<>(myFile, EnumeratorStringDescriptor.INSTANCE, createExternalizer());
    }
  }


  public void deactivate() {
    Map<String, Collection<SvnBranchItem>> branchLocationToBranchItemsMap = ContainerUtil.newHashMap();
    SvnBranchConfigurationManager manager = SvnBranchConfigurationManager.getInstance(myProject);
    Map<VirtualFile,SvnBranchConfigurationNew> mapCopy = manager.getSvnBranchConfigManager().getMapCopy();
    for (Map.Entry<VirtualFile, SvnBranchConfigurationNew> entry : mapCopy.entrySet()) {
      Map<String,InfoStorage<List<SvnBranchItem>>> branchMap = entry.getValue().getBranchMap();
      for (Map.Entry<String, InfoStorage<List<SvnBranchItem>>> storageEntry : branchMap.entrySet()) {
        branchLocationToBranchItemsMap.put(storageEntry.getKey(), storageEntry.getValue().getValue());
      }
    }
    synchronized (myLock) {
      // TODO: Possibly implement optimization - do not perform save if there are no changes in branch locations and branch items
      // ensure myState.put() is called - so myState will treat itself as dirty and myState.force() will invoke real persisting
      myState.put("", branchLocationToBranchItemsMap);
      myState.force();
      myState = null;
    }
  }

  private DataExternalizer<Map<String, Collection<SvnBranchItem>>> createExternalizer() {
    return new DataExternalizer<Map<String, Collection<SvnBranchItem>>>() {
      @Override
      public void save(@NotNull DataOutput out, Map<String, Collection<SvnBranchItem>> value) throws IOException {
        out.writeInt(value.size());
        ArrayList<String> keys = new ArrayList<>(value.keySet());
        Collections.sort(keys);
        for (String key : keys) {
          out.writeUTF(key);
          List<SvnBranchItem> list = new ArrayList<>(value.get(key));
          Collections.sort(list, comparing(item -> item.getUrl().toDecodedString(), CASE_INSENSITIVE_ORDER));
          out.writeInt(list.size());
          for (SvnBranchItem item : list) {
            out.writeUTF(item.getUrl().toDecodedString());
            out.writeLong(item.getCreationDateMillis());
            out.writeLong(item.getRevision());
          }
        }
      }

      @Override
      public Map<String, Collection<SvnBranchItem>> read(@NotNull DataInput in) throws IOException {
        final HashMap<String, Collection<SvnBranchItem>> map = new HashMap<>();
        int mapSize = in.readInt();
        for (int i = 0; i < mapSize; i++) {
          final String key = in.readUTF();
          final int size = in.readInt();
          final ArrayList<SvnBranchItem> list = new ArrayList<>(size);
          for (int j = 0; j < size; j++) {
            String urlValue = in.readUTF();
            long creation = in.readLong();
            long revision = in.readLong();
            Url url;
            try {
              url = createUrl(urlValue, false);
            }
            catch (SvnBindException e) {
              throw new IOException("Could not parse url " + urlValue, e);
            }
            list.add(new SvnBranchItem(url, creation, revision));
          }
          map.put(key, list);
        }
        return map;
      }
    };
  }
}
