/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.converters.repositories;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.converters.repositories.beans.RepositoriesBean;
import org.jetbrains.idea.maven.dom.converters.repositories.beans.RepositoryBeanInfo;

import java.util.Map;
import java.util.Set;

/**
 * @author Serega.Vasiliev
 */
public class MavenRepositoriesProvider {
  public static MavenRepositoriesProvider getInstance() {
    return ServiceManager.getService(MavenRepositoriesProvider.class);
  }

  Map<String, Pair<String, String>> myRepositoriesMap = new HashMap<String, Pair<String, String>>();

  public MavenRepositoriesProvider() {
    final RepositoriesBean repositoriesBean =
      XmlSerializer.deserialize(MavenRepositoriesProvider.class.getResource("repositories.xml"), RepositoriesBean.class);

    assert repositoriesBean != null;
    RepositoryBeanInfo[] repositories = repositoriesBean.getRepositories();
    assert repositories != null;

    for (RepositoryBeanInfo repository : repositories) {
      registerRepository(repository.getId(), repository.getName(), repository.getUrl());
    }
  }

  public void registerRepository(@NotNull String id, @NotNull String name, @NotNull String url) {
    myRepositoriesMap.put(id, Pair.create(name, url));
  }

  @NotNull
  public Set<String> getRepositoryIds() {
    return myRepositoriesMap.keySet();
  }

  @Nullable
  public String getRepositoryName(@Nullable String id) {
    Pair<String, String> pair = myRepositoriesMap.get(id);
    return pair != null ? pair.getFirst() : null;
  }

  @Nullable
  public String getRepositoryUrl(@Nullable String id) {
    Pair<String, String> pair = myRepositoriesMap.get(id);
    return pair != null ? pair.getSecond() : null;
  }
}
