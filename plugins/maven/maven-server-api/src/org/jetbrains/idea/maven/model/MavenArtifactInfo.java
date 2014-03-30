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

import com.intellij.openapi.util.text.StringUtil;

import java.io.Serializable;

public class MavenArtifactInfo implements Serializable {
  private final String myGroupId;
  private final String myArtifactId;
  private final String myVersion;
  private final String myPackaging;
  private final String myClassifier;
  private final String myClassNames;
  private final String myRepositoryId;

  public MavenArtifactInfo(MavenId id,
                           String packaging,
                           String classifier) {
    this(id.getGroupId(), id.getArtifactId(), id.getVersion(), packaging, classifier);
  }

  public MavenArtifactInfo(String groupId,
                           String artifactId,
                           String version,
                           String packaging,
                           String classifier) {
    this(groupId, artifactId, version, packaging, classifier, null, null);
  }

  public MavenArtifactInfo(String groupId,
                           String artifactId,
                           String version,
                           String packaging,
                           String classifier,
                           String classNames,
                           String repositoryId) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
    myPackaging = packaging;
    myClassifier = classifier;
    myClassNames = classNames;
    myRepositoryId = repositoryId;
  }

  public String getGroupId() {
    return myGroupId;
  }

  public String getArtifactId() {
    return myArtifactId;
  }

  public String getVersion() {
    return myVersion;
  }

  public String getPackaging() {
    return myPackaging;
  }

  public String getClassifier() {
    return myClassifier;
  }

  public String getClassNames() {
    return myClassNames;
  }

  public String getRepositoryId() {
    return myRepositoryId;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();

    MavenId.append(builder, myGroupId);
    MavenId.append(builder, myArtifactId);
    MavenId.append(builder, myPackaging);
    if (!StringUtil.isEmptyOrSpaces(myClassifier)) MavenId.append(builder, myClassifier);
    MavenId.append(builder, myVersion);
    MavenId.append(builder, myRepositoryId);

    return builder.toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenArtifactInfo that = (MavenArtifactInfo)o;

    if (myArtifactId != null ? !myArtifactId.equals(that.myArtifactId) : that.myArtifactId != null) return false;
    if (myClassNames != null ? !myClassNames.equals(that.myClassNames) : that.myClassNames != null) return false;
    if (myClassifier != null ? !myClassifier.equals(that.myClassifier) : that.myClassifier != null) return false;
    if (myGroupId != null ? !myGroupId.equals(that.myGroupId) : that.myGroupId != null) return false;
    if (myPackaging != null ? !myPackaging.equals(that.myPackaging) : that.myPackaging != null) return false;
    if (myRepositoryId != null ? !myRepositoryId.equals(that.myRepositoryId) : that.myRepositoryId != null) return false;
    if (myVersion != null ? !myVersion.equals(that.myVersion) : that.myVersion != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myGroupId != null ? myGroupId.hashCode() : 0;
    result = 31 * result + (myArtifactId != null ? myArtifactId.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    result = 31 * result + (myPackaging != null ? myPackaging.hashCode() : 0);
    result = 31 * result + (myClassifier != null ? myClassifier.hashCode() : 0);
    result = 31 * result + (myClassNames != null ? myClassNames.hashCode() : 0);
    result = 31 * result + (myRepositoryId != null ? myRepositoryId.hashCode() : 0);
    return result;
  }
}
