/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.persistent.SmallMapSerializer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/24/11
 * Time: 1:21 PM
 */
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
      return map == null ? null : map.get(SvnBranchConfigurationNew.ensureEndSlash(url));
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
          Collections.sort(list, SerializationComparator.getInstance());
          out.writeInt(list.size());
          for (SvnBranchItem item : list) {
            out.writeUTF(item.getUrl());
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
            String url = in.readUTF();
            long creation = in.readLong();
            long revision = in.readLong();
            list.add(new SvnBranchItem(url, new Date(creation), revision));
          }
          map.put(key, list);
        }
        return map;
      }
    };
  }
  
  private static class SerializationComparator implements Comparator<SvnBranchItem> {
    private final static SerializationComparator ourInstance = new SerializationComparator();
    
    public static SerializationComparator getInstance() {
      return ourInstance;
    }
    
    @Override
    public int compare(SvnBranchItem o1, SvnBranchItem o2) {
      return o1.getUrl().compareToIgnoreCase(o2.getUrl());
    }
  }
}
