// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.converters.repositories;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashMap;
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
    return ApplicationManager.getApplication().getService(MavenRepositoriesProvider.class);
  }

  final Map<String, RepositoryBeanInfo> myRepositoriesMap = new THashMap<>();

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

  @NotNull
  public Set<String> getRepositoryIds() {
    return myRepositoriesMap.keySet();
  }

  @Nullable
  public String getRepositoryName(@Nullable String id) {
    RepositoryBeanInfo pair = myRepositoriesMap.get(id);
    return pair != null ? pair.getName() : null;
  }

  @Nullable
  public String getRepositoryUrl(@Nullable String id) {
    RepositoryBeanInfo pair = myRepositoriesMap.get(id);
    return pair != null ? pair.getUrl() : null;
  }
}
