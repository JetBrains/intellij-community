/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.utils.Path;

import java.io.File;
import java.util.*;

public class MavenRunnerParameters implements Cloneable {
  private boolean isPomExecution;
  private Path myWorkingDirPath;
  private String myPomFileName;
  private final List<String> myGoals = new ArrayList<>();

  private boolean myResolveToWorkspace;

  private final Map<String, Boolean> myProfilesMap = new LinkedHashMap<>();

  private final Collection<String> myEnabledProfilesForXmlSerializer = new TreeSet<>();

  public MavenRunnerParameters() {
    this(true, "", null, null, null, null);
  }

  /**
   * @deprecated use {@link MavenRunnerParameters#MavenRunnerParameters(boolean, String, String, List, Collection)}
   */
  @Deprecated
  public MavenRunnerParameters(boolean isPomExecution,
                               @NotNull String workingDirPath,
                               @Nullable List<String> goals,
                               @Nullable Collection<String> explicitEnabledProfiles) {
    this(isPomExecution, workingDirPath, null, goals, explicitEnabledProfiles, null);
  }

  public MavenRunnerParameters(boolean isPomExecution,
                               @NotNull String workingDirPath,
                               @Nullable String pomFileName,
                               @Nullable List<String> goals,
                               @Nullable Collection<String> explicitEnabledProfiles) {
    this(isPomExecution, workingDirPath, pomFileName, goals, explicitEnabledProfiles, null);
  }

  /**
   * @deprecated use {@link MavenRunnerParameters#MavenRunnerParameters(boolean, String, String, List, MavenExplicitProfiles)}
   */
  @Deprecated
  public MavenRunnerParameters(boolean isPomExecution,
                               @NotNull String workingDirPath,
                               @Nullable List<String> goals,
                               @NotNull MavenExplicitProfiles explicitProfiles) {
    this(isPomExecution, workingDirPath, null, goals, explicitProfiles);
  }

  public MavenRunnerParameters(boolean isPomExecution,
                               @NotNull String workingDirPath,
                               @Nullable String pomFileName,
                               @Nullable List<String> goals,
                               @NotNull MavenExplicitProfiles explicitProfiles) {
    this(isPomExecution, workingDirPath, pomFileName, goals, explicitProfiles.getEnabledProfiles(), explicitProfiles.getDisabledProfiles());
  }

  /**
   * @deprecated use {@link MavenRunnerParameters#MavenRunnerParameters(boolean, String, String, List, Collection, Collection)}
   */
  @Deprecated
  public MavenRunnerParameters(boolean isPomExecution,
                               @NotNull String workingDirPath,
                               @Nullable List<String> goals,
                               @Nullable Collection<String> explicitEnabledProfiles,
                               @Nullable Collection<String> explicitDisabledProfiles) {
    this(isPomExecution, workingDirPath, null, goals, explicitEnabledProfiles, explicitDisabledProfiles);
  }

  public MavenRunnerParameters(boolean isPomExecution,
                               @NotNull String workingDirPath,
                               @Nullable String pomFileName,
                               @Nullable List<String> goals,
                               @Nullable Collection<String> explicitEnabledProfiles,
                               @Nullable Collection<String> explicitDisabledProfiles) {
    this.isPomExecution = isPomExecution;
    setWorkingDirPath(workingDirPath);
    this.myPomFileName = pomFileName;
    setGoals(goals);

    if (explicitEnabledProfiles != null) {
      for (String profile : explicitEnabledProfiles) {
        myProfilesMap.put(profile, Boolean.TRUE);
      }
    }

    if (explicitDisabledProfiles != null) {
      for (String profile : explicitDisabledProfiles) {
        myProfilesMap.put(profile, Boolean.FALSE);
      }
    }
  }

  public MavenRunnerParameters(String workingDirPath, String pomFileName, boolean isPomExecution,
                               @Nullable List<String> goals,
                               @NotNull Map<String, Boolean> profilesMap) {
    this.isPomExecution = isPomExecution;
    setWorkingDirPath(workingDirPath);
    setPomFileName(pomFileName);
    setGoals(goals);
    setProfilesMap(profilesMap);
  }

  public MavenRunnerParameters(MavenRunnerParameters that) {
    this(that.getWorkingDirPath(), that.myPomFileName, that.isPomExecution, that.myGoals, that.myProfilesMap);
    myResolveToWorkspace = that.myResolveToWorkspace;
  }

  public boolean isPomExecution() {
    return isPomExecution;
  }

  @NotNull
  public String getWorkingDirPath() {
    return myWorkingDirPath.getPath();
  }

  public void setWorkingDirPath(@NotNull String workingDirPath) {
    myWorkingDirPath = new Path(workingDirPath);
  }

  @NotNull
  public File getWorkingDirFile() {
    return new File(myWorkingDirPath.getPath());
  }

  public void setPomFileName(String pomFileName) {
    myPomFileName = pomFileName;
  }

  public String getPomFileName() {
    return myPomFileName;
  }

  public List<String> getGoals() {
    return myGoals;
  }

  public void setGoals(@Nullable List<String> goals) {
    if (myGoals == goals) return;  // Called from XML Serializer
    myGoals.clear();

    if (goals != null) {
      myGoals.addAll(goals);
    }
  }

  @Deprecated // Must be used by XML Serializer only!!!
  @OptionTag("profiles")
  public Collection<String> getEnabledProfilesForXmlSerializer() {
    return myEnabledProfilesForXmlSerializer;
  }

  @Deprecated // Must be used by XML Serializer only!!!
  public void setEnabledProfilesForXmlSerializer(@Nullable Collection<String> enabledProfilesForXmlSerializer) {
    if (enabledProfilesForXmlSerializer != null) {
      if (myEnabledProfilesForXmlSerializer == enabledProfilesForXmlSerializer) return; // Called from XML Serializer
      myEnabledProfilesForXmlSerializer.retainAll(enabledProfilesForXmlSerializer);
      myEnabledProfilesForXmlSerializer.addAll(enabledProfilesForXmlSerializer);
    }
  }

  public void fixAfterLoadingFromOldFormat() {
    for (String profile : myEnabledProfilesForXmlSerializer) {
      myProfilesMap.put(profile, true);
    }
    myEnabledProfilesForXmlSerializer.clear();

    File workingDir = getWorkingDirFile();
    if (MavenConstants.POM_XML.equals(workingDir.getName())) {
      setWorkingDirPath(workingDir.getParent());
    }
  }

  @OptionTag("profilesMap")
  @MapAnnotation(sortBeforeSave = false)
  public Map<String, Boolean> getProfilesMap() {
    return myProfilesMap;
  }

  public void setProfilesMap(@NotNull Map<String, Boolean> profilesMap) {
    if (myProfilesMap == profilesMap) return; // Called from XML Serializer
    myProfilesMap.clear();
    for (Map.Entry<String, Boolean> entry : profilesMap.entrySet()) {
      if (entry.getValue() != null) {
        myProfilesMap.put(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Was left for compatibility with old plugins.
   * @deprecated use getProfileMap()
   * @return
   */
  @Transient
  public Collection<String> getProfiles() {
    return Maps.filterValues(myProfilesMap, Predicates.equalTo(true)).keySet();
  }

  /**
   * Was left for compatibility with old plugins.
   * @deprecated use getProfileMap()
   * @param profiles
   */
  public void setProfiles(@Nullable Collection<String> profiles) {
    if (profiles != null) {
      for (String profile : profiles) {
        myProfilesMap.put(profile, true);
      }
    }
  }

  public boolean isResolveToWorkspace() {
    return myResolveToWorkspace;
  }

  public void setResolveToWorkspace(boolean resolveToWorkspace) {
    myResolveToWorkspace = resolveToWorkspace;
  }

  public MavenRunnerParameters clone() {
    return new MavenRunnerParameters(this);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenRunnerParameters that = (MavenRunnerParameters)o;

    if (isPomExecution != that.isPomExecution) return false;
    if (myResolveToWorkspace != that.myResolveToWorkspace) return false;
    if (!myGoals.equals(that.myGoals)) return false;
    if (myWorkingDirPath != null ? !myWorkingDirPath.equals(that.myWorkingDirPath) : that.myWorkingDirPath != null) return false;
    if (!myProfilesMap.equals(that.myProfilesMap)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = isPomExecution ? 1 : 0;
    result = 31 * result + (myWorkingDirPath != null ? myWorkingDirPath.hashCode() : 0);
    result = 31 * result + myGoals.hashCode();
    result = 31 * result + myProfilesMap.hashCode();
    return result;
  }
}
