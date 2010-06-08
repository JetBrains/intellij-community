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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.text.MessageFormat;

public class MavenArtifact implements Serializable {
  private final String myGroupId;
  private final String myArtifactId;
  private final String myVersion;
  private final String myBaseVersion;
  private final String myType;
  private final String myClassifier;

  private String myScope;
  private final boolean myOptional;

  private final String myExtension;

  private final File myFile;
  private final boolean myResolved;
  private final boolean myStubbed;

  public MavenArtifact(String groupId,
                       String artifactId,
                       String version,
                       String baseVersion,
                       String type,
                       String classifier,
                       String scope,
                       boolean optional,
                       String extension,
                       @Nullable File file,
                       File localRepository,
                       boolean resolved,
                       boolean stubbed) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
    myBaseVersion = baseVersion;
    myType = type;
    myClassifier = classifier;
    myScope = scope;
    myOptional = optional;
    myExtension = extension;
    myFile = file != null ? file : new File(localRepository, getRelativePath());
    myResolved = resolved;
    myStubbed = stubbed;
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

  public MavenId getMavenId() {
    return new MavenId(myGroupId, myArtifactId, myVersion);
  }

  public String getType() {
    return myType;
  }

  public String getClassifier() {
    return myClassifier;
  }

  public String getScope() {
    return myScope;
  }

  public void setScope(String scope) {
    myScope = scope;
  }

  public boolean isOptional() {
    return myOptional;
  }

  public boolean isExportable() {
    if (myOptional) return false;
    return MavenConstants.SCOPE_COMPILE.equals(myScope) || MavenConstants.SCOPE_RUNTIME.equals(myScope);
  }

  public String getExtension() {
    return myExtension;
  }

  public boolean isResolved() {
    return myResolved && myFile.exists() && !myStubbed;
  }

  @NotNull
  public File getFile() {
    return myFile;
  }

  public String getPath() {
    return FileUtil.toSystemIndependentName(myFile.getPath());
  }

  public String getRelativePath() {
    return getRelativePathForExtraArtifact(null, null);
  }

  public String getRelativePathForExtraArtifact(@Nullable String extraArtifactClassifier, @Nullable String customExtension) {
    StringBuilder result = new StringBuilder();
    result.append(myGroupId.replace('.', '/'));
    result.append('/');
    result.append(myArtifactId);
    result.append('/');
    result.append(myVersion);
    result.append('/');
    result.append(myArtifactId);
    result.append('-');
    result.append(myVersion);

    if (!StringUtil.isEmptyOrSpaces(extraArtifactClassifier)) {
      if (MavenConstants.TYPE_TEST_JAR.equals(myType)) {
        result.append("-test");
      }
      result.append('-');
      result.append(extraArtifactClassifier);
    }
    else {
      if (!StringUtil.isEmptyOrSpaces(myClassifier)) {
        result.append('-');
        result.append(myClassifier);
      }
    }
    result.append(".");
    result.append(customExtension == null ? myExtension : customExtension);
    return result.toString();
  }

  public String getPathForExtraArtifact(@Nullable String extraArtifactClassifier, @Nullable String customExtension) {
    String path = getPath();

    if (!StringUtil.isEmptyOrSpaces(extraArtifactClassifier)) {
      int repoEnd = path.lastIndexOf(getRelativePath());

      if (repoEnd == -1) {
        // unknown path format: try to add a classified at the end of the filename
        int dotPos = path.lastIndexOf(".");
        if (dotPos != -1) {// sometimes path doesn't contain '.'; but i can't find any reason why.
          String withoutExtension = path.substring(0, dotPos);
          path = MessageFormat.format("{0}-{1}.{2}",
                                      withoutExtension,
                                      extraArtifactClassifier,
                                      customExtension == null ? myExtension : customExtension);
        }
      }
      else {
        String repoPath = path.substring(0, repoEnd);
        path = repoPath + getRelativePathForExtraArtifact(extraArtifactClassifier, customExtension);
      }
    }
    return path;
  }

  public String getDisplayStringSimple() {
    StringBuilder builder = new StringBuilder();

    MavenId.append(builder, myGroupId);
    MavenId.append(builder, myArtifactId);
    MavenId.append(builder, myVersion);

    return builder.toString();
  }

  public String getDisplayStringWithType() {
    StringBuilder builder = new StringBuilder();

    MavenId.append(builder, myGroupId);
    MavenId.append(builder, myArtifactId);
    MavenId.append(builder, myType);
    MavenId.append(builder, myVersion);

    return builder.toString();
  }

  public String getDisplayStringWithTypeAndClassifier() {
    StringBuilder builder = new StringBuilder();

    MavenId.append(builder, myGroupId);
    MavenId.append(builder, myArtifactId);
    MavenId.append(builder, myType);
    if (!StringUtil.isEmptyOrSpaces(myClassifier)) MavenId.append(builder, myClassifier);
    MavenId.append(builder, myVersion);

    return builder.toString();
  }

  public String getDisplayStringFull() {
    StringBuilder builder = new StringBuilder();

    MavenId.append(builder, myGroupId);
    MavenId.append(builder, myArtifactId);
    MavenId.append(builder, myType);
    if (!StringUtil.isEmptyOrSpaces(myClassifier)) MavenId.append(builder, myClassifier);
    MavenId.append(builder, myVersion);
    if (!StringUtil.isEmptyOrSpaces(myScope)) MavenId.append(builder, myScope);

    return builder.toString();
  }

  public String getDisplayStringForLibraryName() {
    StringBuilder builder = new StringBuilder();

    MavenId.append(builder, myGroupId);
    MavenId.append(builder, myArtifactId);

    if (!StringUtil.isEmptyOrSpaces(myType) && !MavenConstants.TYPE_JAR.equals(myType)) MavenId.append(builder, myType);
    if (!StringUtil.isEmptyOrSpaces(myClassifier)) MavenId.append(builder, myClassifier);

    String version = !StringUtil.isEmptyOrSpaces(myBaseVersion) ? myBaseVersion : myVersion;
    if (!StringUtil.isEmptyOrSpaces(version)) MavenId.append(builder, version);

    return builder.toString();
  }

  @Override
  public String toString() {
    return getDisplayStringFull();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenArtifact that = (MavenArtifact)o;

    if (myGroupId != null ? !myGroupId.equals(that.myGroupId) : that.myGroupId != null) return false;
    if (myArtifactId != null ? !myArtifactId.equals(that.myArtifactId) : that.myArtifactId != null) return false;
    if (myVersion != null ? !myVersion.equals(that.myVersion) : that.myVersion != null) return false;
    if (myBaseVersion != null ? !myBaseVersion.equals(that.myBaseVersion) : that.myBaseVersion != null) return false;
    if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;
    if (myClassifier != null ? !myClassifier.equals(that.myClassifier) : that.myClassifier != null) return false;
    if (myScope != null ? !myScope.equals(that.myScope) : that.myScope != null) return false;
    if (myExtension != null ? !myExtension.equals(that.myExtension) : that.myExtension != null) return false;
    if (myFile != null ? !myFile.equals(that.myFile) : that.myFile != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myGroupId != null ? myGroupId.hashCode() : 0;
    result = 31 * result + (myArtifactId != null ? myArtifactId.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    result = 31 * result + (myBaseVersion != null ? myBaseVersion.hashCode() : 0);
    result = 31 * result + (myType != null ? myType.hashCode() : 0);
    result = 31 * result + (myClassifier != null ? myClassifier.hashCode() : 0);
    result = 31 * result + (myScope != null ? myScope.hashCode() : 0);
    result = 31 * result + (myExtension != null ? myExtension.hashCode() : 0);
    result = 31 * result + (myFile != null ? myFile.hashCode() : 0);
    return result;
  }
}
