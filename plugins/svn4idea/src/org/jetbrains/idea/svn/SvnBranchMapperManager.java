package org.jetbrains.idea.svn;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
  }

  public void remove(final String url, final File value) {
    final Set<String> set = myStateHolder.get(url);
    if (set != null) {
      set.remove(value.getAbsolutePath());
    }
  }

  public void notifyMappingChanged(final Project project, final VirtualFile vcsRoot, final SvnBranchConfiguration configuration) {
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
}
