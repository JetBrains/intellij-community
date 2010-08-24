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
package org.jetbrains.idea.maven.services;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
@State(
  name = "MavenServices",
  storages = {@Storage(
    id = "other",
    file = "$APP_CONFIG$/mavenServices.xml")})
public class MavenServicesManager implements PersistentStateComponent<Element> {

  private final List<String> myUrls = new ArrayList<String>();

  public static MavenServicesManager getInstance() {
    return ServiceManager.getService(MavenServicesManager.class);
  }

  public static String[] getServiceUrls() {
    final List<String> configured = getInstance().getUrls();
    if (!configured.isEmpty()) return configured.toArray(new String[configured.size()]);
    return new String[]{
      "http://oss.sonatype.org/service/local/",
      "http://repo.jfrog.org/artifactory/api/"
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
    for (Element element : (List<Element>)state.getChildren("service-url")) {
      myUrls.add(StringUtil.unescapeXml(element.getTextTrim()));
    }
  }
}
