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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;

import java.io.File;
import java.util.*;

@State(
  name = "SvnBranchMapperManager",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class SvnBranchMapperManager implements PersistentStateComponent<SvnBranchMapperManager.SvnBranchMapperHolder> {
  private SvnBranchMapperHolder myStateHolder;

  public static SvnBranchMapperManager getInstance() {
    return ServiceManager.getService(SvnBranchMapperManager.class);
  }

  public SvnBranchMapperManager() {
    myStateHolder = new SvnBranchMapperHolder();
  }

  public SvnBranchMapperHolder getState() {
    return myStateHolder;
  }

  public void loadState(final SvnBranchMapperHolder state) {
    myStateHolder = state;
  }

  public void put(final String url, final String value) {
    myStateHolder.put(url, value);
    notifyWcRootsChanged(url, Collections.unmodifiableCollection(myStateHolder.get(url)));
  }

  public void remove(final String url, final File value) {
    final Set<String> set = myStateHolder.get(url);
    if (set != null) {
      set.remove(value.getAbsolutePath());
    }
    notifyWcRootsChanged(url, Collections.unmodifiableCollection(set));
  }

  private static void notifyWcRootsChanged(final String url, final Collection<String> roots) {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(WC_ROOTS_CHANGED).rootsChanged(url, roots);
  }

  public void notifyBranchesChanged(final Project project, final VirtualFile vcsRoot, final SvnBranchConfigurationNew configuration) {
    final Map<String, String> map = configuration.getUrl2FileMappings(project, vcsRoot);
    if (map != null) {
      for (Map.Entry<String, String> entry : map.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
    }
  }

  public Set<String> get(final String key) {
    return myStateHolder.get(key);
  }

  public static class SvnBranchMapperHolder {
    public Map<String, Set<String>> myMapping;

    public SvnBranchMapperHolder() {
      myMapping = new HashMap<String, Set<String>>();
    }

    public void put(final String key, final String value) {
      Set<String> files = myMapping.get(key);
      if (files == null) {
        files = new HashSet<String>();
        myMapping.put(key, files);
      }
      files.add(value);
    }

    public Set<String> get(final String key) {
      return myMapping.get(key);
    }
  }

  public static interface WcRootsChangeConsumer {
    void rootsChanged(final String url, final Collection<String> roots);
  }

  public static final Topic<WcRootsChangeConsumer> WC_ROOTS_CHANGED =
      new Topic<WcRootsChangeConsumer>("SVN_WC_ROOTS_CHANGED", WcRootsChangeConsumer.class);
}
