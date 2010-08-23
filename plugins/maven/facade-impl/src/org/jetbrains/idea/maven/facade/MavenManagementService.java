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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facade.artifactory.ArtifactoryService;
import org.jetbrains.idea.maven.facade.nexus.NexusService;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class MavenManagementService {
  public abstract String getDisplayName();
  @Nullable
  public abstract List<MavenRepositoryInfo> getRepositories(String url) throws Exception;
  public abstract List<MavenArtifactInfo> findArtifacts(MavenArtifactInfo template, String url) throws Exception;


  public final String toString() {
    return getDisplayName();
  }

  @NotNull
  public static MavenManagementService[] getServices() {
    return new MavenManagementService[] { new NexusService(), new ArtifactoryService() };
  }
}
