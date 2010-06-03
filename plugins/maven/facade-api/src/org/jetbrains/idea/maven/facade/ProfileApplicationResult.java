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
package org.jetbrains.idea.maven.facade;

import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenProfile;

import java.io.Serializable;
import java.util.List;

public class ProfileApplicationResult implements Serializable {
  private final MavenModel myModel;
  private final List<MavenProfile> myActivatedProfiles;

  public ProfileApplicationResult(MavenModel model, List<MavenProfile> activatedProfiles) {
    myModel = model;
    myActivatedProfiles = activatedProfiles;
  }

  public MavenModel getModel() {
    return myModel;
  }

  public List<MavenProfile> getActivatedProfiles() {
    return myActivatedProfiles;
  }
}
