/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.services;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.services.artifactory.ArtifactoryRepositoryService;
import org.jetbrains.idea.maven.services.nexus.NexusRepositoryService;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
@State(
  name = "MavenServices",
  storages = @Storage("mavenServices.xml")
)
public class MavenRepositoryServicesManager implements PersistentStateComponent<MavenRepositoryServicesManager> {
  private final List<String> myUrls = new ArrayList<>();

  public MavenRepositoryServicesManager() {
    myUrls.add("https://oss.sonatype.org/service/local/");
    myUrls.add("http://repo.jfrog.org/artifactory/api/");
    myUrls.add("https://repository.jboss.org/nexus/service/local/");
  }

  @NotNull
  public static MavenRepositoryServicesManager getInstance() {
    return ServiceManager.getService(MavenRepositoryServicesManager.class);
  }

  @NotNull
  public static MavenRepositoryService[] getServices() {
    return new MavenRepositoryService[]{new NexusRepositoryService(), new ArtifactoryRepositoryService()};
  }

  public static String[] getServiceUrls() {
    return ArrayUtil.toStringArray(getInstance().getUrls());
  }

  @NotNull
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false, elementTag = "service-url", elementValueAttribute = "")
  public List<String> getUrls() {
    return myUrls;
  }

  public void setUrls(@NotNull List<String> urls) {
    if (myUrls != urls) {
      myUrls.clear();
      myUrls.addAll(urls);
    }
  }

  @Override
  public MavenRepositoryServicesManager getState() {
    return this;
  }

  @Override
  public void loadState(MavenRepositoryServicesManager state) {
    myUrls.clear();
    myUrls.addAll(state.getUrls());
  }

  @NotNull
  public static List<MavenRepositoryInfo> getRepositories(String url) {
    List<MavenRepositoryInfo> result = new SmartList<>();
    for (MavenRepositoryService service : getServices()) {
      try {
        result.addAll(service.getRepositories(url));
      }
      catch (IOException e) {
        MavenLog.LOG.info(e);
      }
    }
    return result;
  }

  @NotNull
  public static List<MavenArtifactInfo> findArtifacts(@NotNull MavenArtifactInfo template, @NotNull String url) {
    List<MavenArtifactInfo> result = new SmartList<>();
    for (MavenRepositoryService service : getServices()) {
      try {
        result.addAll(service.findArtifacts(url, template));
      }
      catch (IOException e) {
        MavenLog.LOG.info(e);
      }
    }
    return result;
  }
}
