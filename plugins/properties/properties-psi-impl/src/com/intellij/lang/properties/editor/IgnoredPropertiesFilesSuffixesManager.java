/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
@State(
  name = "IgnoredPropertiesFilesSuffixesManager",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/resourceBundles.xml", scheme = StorageScheme.DIRECTORY_BASED)
  })
public class IgnoredPropertiesFilesSuffixesManager implements PersistentStateComponent<IgnoredPropertiesFilesSuffixesManager.IgnoredPropertiesFilesSuffixesState>, Disposable {
  private IgnoredPropertiesFilesSuffixesState myState = new IgnoredPropertiesFilesSuffixesState();
  private final List<SuffixesListener> myListeners = new ArrayList<SuffixesListener>();

  public IgnoredPropertiesFilesSuffixesManager(final Project project) {
    Disposer.register(project, this);
  }

  public static IgnoredPropertiesFilesSuffixesManager getInstance(final Project project) {
    return ServiceManager.getService(project, IgnoredPropertiesFilesSuffixesManager.class);
  }

  public void addListener(final SuffixesListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(final SuffixesListener listener) {
    myListeners.remove(listener);
  }

  public boolean isPropertyComplete(final ResourceBundle resourceBundle, final String key) {
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      if (propertiesFile.findPropertyByKey(key) == null && !myState.getIgnoredSuffixes().contains(PropertiesUtil.getSuffix(propertiesFile))) {
          return false;
      }
    }
    return true;
  }

  public Set<String> getIgnoredSuffixes() {
    return myState.getIgnoredSuffixes();
  }

  public List<PropertiesFile> getPropertiesFilesWithoutTranslation(final ResourceBundle resourceBundle, final Set<String> keys) {
    final PropertiesFile defaultPropertiesFile = resourceBundle.getDefaultPropertiesFile();
    return ContainerUtil.filter(resourceBundle.getPropertiesFiles(), new Condition<PropertiesFile>() {
      @Override
      public boolean value(PropertiesFile propertiesFile) {
        if (defaultPropertiesFile.equals(propertiesFile)) {
          return false;
        }
        for (String key : keys) {
          if (propertiesFile.findPropertyByKey(key) == null && !myState.getIgnoredSuffixes().contains(PropertiesUtil.getSuffix(propertiesFile))) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public void addSuffixes(Collection<String> suffixes) {
    final Set<String> oldSuffixes = new HashSet<String>(myState.getIgnoredSuffixes());
    myState.addSuffixes(suffixes);
    final Set<String> newSuffixes = myState.getIgnoredSuffixes();
    if (!oldSuffixes.equals(newSuffixes)) {
      for (SuffixesListener listener : myListeners) {
        listener.suffixesChanged();
      }
    }
  }

  public void setSuffixes(Collection<String> suffixes) {
    final Set<String> oldSuffixes = myState.getIgnoredSuffixes();
    myState.setSuffixes(suffixes);
    if (!oldSuffixes.equals(suffixes)) {
      for (SuffixesListener listener : myListeners) {
        listener.suffixesChanged();
      }
    }
  }

  @Nullable
  @Override
  public IgnoredPropertiesFilesSuffixesState getState() {
    return myState.isEmpty() ? null : myState;
  }

  @Override
  public void loadState(IgnoredPropertiesFilesSuffixesState state) {
    myState = state;
  }

  @Override
  public void dispose() {
    if (!myListeners.isEmpty()) {
      myListeners.clear();
    }
  }

  public static class IgnoredPropertiesFilesSuffixesState {
    @Property(surroundWithTag = false)
    @AbstractCollection(elementTag = "ignored-suffix", surroundWithTag = false)
    public Set<String> myIgnoredSuffixes = new HashSet<String>();

    public void addSuffixes(Collection<String> suffixes) {
      myIgnoredSuffixes.addAll(suffixes);
    }

    public void setSuffixes(Collection<String> suffixes) {
      myIgnoredSuffixes = new HashSet<String>(suffixes);
    }

    public Set<String> getIgnoredSuffixes() {
      return myIgnoredSuffixes;
    }

    public boolean isEmpty() {
      return myIgnoredSuffixes.isEmpty();
    }
  }

  public interface SuffixesListener {
    void suffixesChanged();
  }
}