/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jdom.Element;
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
  storages = {@Storage(
    file = StoragePathMacros.APP_CONFIG + "/mavenServices.xml")})
public class MavenRepositoryServicesManager implements PersistentStateComponent<Element> {
  private final List<String> myUrls = new ArrayList<String>();

  @NotNull
  public static MavenRepositoryServicesManager getInstance() {
    return ServiceManager.getService(MavenRepositoryServicesManager.class);
  }

  @NotNull
  public static MavenRepositoryService[] getServices() {
    return new MavenRepositoryService[]{new NexusRepositoryService(), new ArtifactoryRepositoryService()};
  }

  public static String[] getServiceUrls() {
    final List<String> configured = getInstance().getUrls();
    if (!configured.isEmpty()) return ArrayUtil.toStringArray(configured);
    return new String[]{
      "http://oss.sonatype.org/service/local/",
      "http://repo.jfrog.org/artifactory/api/",
      "https://repository.jboss.org/nexus/service/local/"
    };
  }

  public List<String> getUrls() {
    return myUrls;
  }

  public void setUrls(List<String> urls) {
    myUrls.clear();
    myUrls.addAll(urls);
  }


  @Override
  public Element getState() {
    final Element element = new Element("maven-services");
    for (String url : myUrls) {
      final Element child = new Element("service-url");
      child.setText(StringUtil.escapeXml(url));
      element.addContent(child);
    }
    return element;
  }

  @Override
  public void loadState(Element state) {
    myUrls.clear();
    for (Element element : state.getChildren("service-url")) {
      myUrls.add(StringUtil.unescapeXml(element.getTextTrim()));
    }
  }

  @NotNull
  public static List<MavenRepositoryInfo> getRepositories(String url) {
    List<MavenRepositoryInfo> result = new SmartList<MavenRepositoryInfo>();
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
    List<MavenArtifactInfo> result = new SmartList<MavenArtifactInfo>();
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
