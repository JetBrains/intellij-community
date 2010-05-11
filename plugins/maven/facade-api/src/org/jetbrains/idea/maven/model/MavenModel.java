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
package org.jetbrains.idea.maven.model;

import java.util.List;

public class MavenModel extends MavenModelBase {
  private MavenId myMavenId;
  private MavenParent myParent;
  private String myPackaging;
  private String myName;

  private List<MavenProfile> myProfiles;

  private final MavenBuild myBuild = new MavenBuild();

  public MavenId getMavenId() {
    return myMavenId;
  }

  public void setMavenId(MavenId mavenId) {
    myMavenId = mavenId;
  }

  public MavenParent getParent() {
    return myParent;
  }

  public void setParent(MavenParent parent) {
    myParent = parent;
  }

  public String getPackaging() {
    return myPackaging;
  }

  public void setPackaging(String packaging) {
    myPackaging = packaging;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public List<MavenProfile> getProfiles() {
    return myProfiles;
  }

  public void setProfiles(List<MavenProfile> profiles) {
    myProfiles = profiles;
  }

  public MavenBuild getBuild() {
    return myBuild;
  }
}
