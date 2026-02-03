// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters.repositories;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.converters.repositories.beans.RepositoriesBean;
import org.jetbrains.idea.maven.dom.converters.repositories.beans.RepositoryBeanInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service(Service.Level.APP)
public final class MavenRepositoriesProvider {
  public static MavenRepositoriesProvider getInstance() {
    return ApplicationManager.getApplication().getService(MavenRepositoriesProvider.class);
  }

  final Map<String, RepositoryBeanInfo> myRepositoriesMap = new HashMap<>();

  public MavenRepositoriesProvider() {
    final RepositoriesBean repositoriesBean =
      XmlSerializer.deserialize(MavenRepositoriesProvider.class.getResource("repositories.xml"), RepositoriesBean.class);

    RepositoryBeanInfo[] repositories = repositoriesBean.getRepositories();
    assert repositories != null;

    for (RepositoryBeanInfo repository : repositories) {
      registerRepository(repository.getId(), repository);
    }
  }

  public void registerRepository(@NotNull String id, RepositoryBeanInfo info) {
    myRepositoriesMap.put(id, info);
  }

  public @NotNull Set<String> getRepositoryIds() {
    return myRepositoriesMap.keySet();
  }

  public @Nullable String getRepositoryName(@Nullable String id) {
    RepositoryBeanInfo pair = myRepositoriesMap.get(id);
    return pair != null ? pair.getName() : null;
  }

  public @Nullable String getRepositoryUrl(@Nullable String id) {
    RepositoryBeanInfo pair = myRepositoriesMap.get(id);
    return pair != null ? pair.getUrl() : null;
  }
}
