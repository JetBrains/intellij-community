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

import java.io.Serializable;

public class MavenProfileActivation implements Serializable {
  private boolean myActiveByDefault;
  private MavenProfileActivationOS myOs;
  private String myJdk;
  private MavenProfileActivationProperty myProperty;
  private MavenProfileActivationFile myFile;

  public boolean isActiveByDefault() {
    return myActiveByDefault;
  }

  public void setActiveByDefault(boolean activeByDefault) {
    myActiveByDefault = activeByDefault;
  }

  public MavenProfileActivationOS getOs() {
    return myOs;
  }

  public void setOs(MavenProfileActivationOS os) {
    myOs = os;
  }

  public String getJdk() {
    return myJdk;
  }

  public void setJdk(String jdk) {
    myJdk = jdk;
  }

  public MavenProfileActivationProperty getProperty() {
    return myProperty;
  }

  public void setProperty(MavenProfileActivationProperty property) {
    myProperty = property;
  }

  public MavenProfileActivationFile getFile() {
    return myFile;
  }

  public void setFile(MavenProfileActivationFile file) {
    myFile = file;
  }
}
