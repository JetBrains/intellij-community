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
package org.jetbrains.idea.maven.facade.artifactory;

/**
* @author Gregory.Shrago
*/
public class ArtifactoryModel {
  public static class RepositoryType {
    public String key;
    public String type;
    public String description;
    public String url;
    public String configuration;
  }

  public static class GavcResults {
      public GavcResult[] results;
  }

  public static class GavcResult {
    public String uri;
  }

  public static class ArchiveResults {
    public ArchiveResult[] results;
  }

  public static class ArchiveResult {
    public String entry;
    public String[] archiveUris;
  }

  public static class FileInfo {
    public String uri;
    public String downloadUri;
    public String metadataUri;
    public String repo;
    public String path;
    public String remoteUrl;
    public String created;
    public String createdBy;
    public String lastModified;
    public String modifiedBy;
    public String lastUpdated;
    public String size;
    public String mimeType;
    public Checksums checksums;
    public Checksums originalChecksums;
  }

  public static class Checksums {
    public String md5;
    public String sha1;
  }
}

