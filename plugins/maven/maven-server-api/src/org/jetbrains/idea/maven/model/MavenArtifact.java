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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;

public class MavenArtifact implements Serializable, MavenCoordinate {

  static long serialVersionUID = 6389627095309274357L;

  public static final String MAVEN_LIB_PREFIX = "Maven: ";

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

  private transient volatile String myLibraryNameCache;

  private transient volatile long myLastFileCheckTimeStamp; // File.exists() is a slow operation, don't run it more than once a second
  private static final Condition<File> ourDefaultFileExists = new Condition<File>() {
    @Override
    public boolean value(File f) {
      return f.exists();
    }
  };

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

  @Override
  public String getGroupId() {
    return myGroupId;
  }

  @Override
  public String getArtifactId() {
    return myArtifactId;
  }

  @Override
  public String getVersion() {
    return myVersion;
  }

  public String getBaseVersion() {
    return myBaseVersion;
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

  /**
   * @deprecated use MavenArtifactUtilKt#resolved
   */
  @Deprecated
  public boolean isResolved() {
    return isResolved(ourDefaultFileExists);
  }

  /**
   * @deprecated use MavenArtifactUtilKt#resolved
   */
  @Deprecated
  public boolean isResolved(Condition<? super File> fileExists) {
    if (myResolved && !myStubbed) {
      long currentTime = System.currentTimeMillis();

      if (myLastFileCheckTimeStamp + 2000 < currentTime) { // File.exists() is a slow operation, don't run it more than once a second
        if (!fileExists.value(myFile)) {
          return false; // Don't cache result if file is not exist.
        }

        myLastFileCheckTimeStamp = currentTime;
      }

      return true;
    }

    return false;
  }

  public boolean isResolvedArtifact() {
    return myResolved;
  }

  @NotNull
  public File getFile() {
    return myFile;
  }

  public String getPath() {
    return FileUtilRt.toSystemIndependentName(myFile.getPath());
  }

  public String getRelativePath() {
    return getRelativePathForExtraArtifact(null, null);
  }

  public String getFileNameWithBaseVersion(@Nullable String extraArtifactClassifier, @Nullable String customExtension) {
    StringBuilder res = new StringBuilder();
    appendFileName(res, extraArtifactClassifier, customExtension, true);
    return res.toString();
  }

  private void appendFileName(StringBuilder result,
                              @Nullable String extraArtifactClassifier,
                              @Nullable String customExtension,
                              boolean useBaseVersion) {
    result.append(myArtifactId);
    result.append('-');
    result.append(useBaseVersion ? myBaseVersion : myVersion);

    String fullClassifier = getFullClassifier(extraArtifactClassifier);
    if (fullClassifier != null) {
      result.append("-").append(fullClassifier);
    }

    result.append(".");
    result.append(customExtension == null ? myExtension : customExtension);
  }

  public String getRelativePathForExtraArtifact(@Nullable String extraArtifactClassifier, @Nullable String customExtension) {
    StringBuilder result = new StringBuilder();
    result.append(myGroupId.replace('.', '/'));
    result.append('/');
    result.append(myArtifactId);
    result.append('/');
    result.append(myBaseVersion);
    result.append('/');

    appendFileName(result, extraArtifactClassifier, customExtension, false);
    return result.toString();
  }

  @Nullable
  public String getFullClassifier(@Nullable String extraClassifier) {
    if (StringUtilRt.isEmptyOrSpaces(extraClassifier)) {
      return myClassifier;
    }

    String result = "";
    if (MavenConstants.TYPE_TEST_JAR.equals(myType) || "tests".equals(myClassifier)) {
      result += "test";
    }
    if (!StringUtilRt.isEmptyOrSpaces(extraClassifier)) {
      result += (!result.isEmpty() ? "-" + extraClassifier : extraClassifier);
    }
    return StringUtilRt.isEmptyOrSpaces(result) ? null : result;
  }

  public String getPathForExtraArtifact(@Nullable String extraArtifactClassifier, @Nullable String customExtension) {
    String path = getPath();

    if (extraArtifactClassifier == null && customExtension == null && Comparing.equal(myVersion, myBaseVersion)) {
      return path;
    }

    int slashPos = path.lastIndexOf('/');
    if (slashPos != -1) {
      StringBuilder res = new StringBuilder();
      res.append(path, 0, slashPos + 1);
      res.append(myArtifactId);
      res.append('-');
      res.append(myVersion);

      String fullClassifier = getFullClassifier(extraArtifactClassifier);
      if (fullClassifier != null) {
        res.append('-').append(fullClassifier);
      }

      res.append('.');
      res.append(customExtension == null ? myExtension : customExtension);
      return res.toString();
    }

    // unknown path format: try to add a classified at the end of the filename
    int dotPos = path.lastIndexOf('.');
    if (dotPos != -1) {// sometimes path doesn't contain '.'; but i can't find any reason why.
      return path.substring(0, dotPos) + '-' + extraArtifactClassifier +
             (customExtension == null ? myExtension : customExtension);
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
    if (!StringUtilRt.isEmptyOrSpaces(myClassifier)) MavenId.append(builder, myClassifier);
    MavenId.append(builder, myVersion);

    return builder.toString();
  }

  public String getDisplayStringFull() {
    StringBuilder builder = new StringBuilder();

    MavenId.append(builder, myGroupId);
    MavenId.append(builder, myArtifactId);
    MavenId.append(builder, myType);
    if (!StringUtilRt.isEmptyOrSpaces(myClassifier)) MavenId.append(builder, myClassifier);
    MavenId.append(builder, myVersion);
    if (!StringUtilRt.isEmptyOrSpaces(myScope)) MavenId.append(builder, myScope);

    return builder.toString();
  }

  public String getLibraryName() {
    String res = myLibraryNameCache;
    if (res == null) {
      StringBuilder builder = new StringBuilder();

      MavenId.append(builder, myGroupId);
      MavenId.append(builder, myArtifactId);

      if (!StringUtilRt.isEmptyOrSpaces(myType) && !MavenConstants.TYPE_JAR.equals(myType)) MavenId.append(builder, myType);
      if (!StringUtilRt.isEmptyOrSpaces(myClassifier)) MavenId.append(builder, myClassifier);

      String version = !StringUtilRt.isEmptyOrSpaces(myBaseVersion) ? myBaseVersion : myVersion;
      if (!StringUtilRt.isEmptyOrSpaces(version)) MavenId.append(builder, version);

      builder.insert(0, MAVEN_LIB_PREFIX);

      res = builder.toString();
      myLibraryNameCache = res;
    }

    return res;
  }

  public String getDisplayStringForLibraryName() {
    return getLibraryName().substring(MAVEN_LIB_PREFIX.length());
  }

  public static boolean isMavenLibrary(@Nullable String libraryName) {
    return libraryName != null && libraryName.startsWith(MAVEN_LIB_PREFIX);
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

  public MavenArtifact replaceFile(File newFile, File newLocalRepository) {
    return new MavenArtifact(
      myGroupId, myArtifactId, myVersion, myBaseVersion, myType, myClassifier,
      myScope, myOptional, myExtension, newFile, newLocalRepository, myResolved, myStubbed);
  }
}
