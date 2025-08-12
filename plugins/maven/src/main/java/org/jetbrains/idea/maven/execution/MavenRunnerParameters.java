// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;

import java.io.File;
import java.util.*;

public final class MavenRunnerParameters implements Cloneable {
  private final boolean isPomExecution;
  private String myWorkingDirPath;
  private String myPomFileName;

  private String myMultimoduleDir;
  private final List<String> myGoals = new ArrayList<>();

  private boolean myResolveToWorkspace;

  private final List<String> myProjectsCmdOptionValues = new ArrayList<>();
  private @Nullable String myCmdOptions = null;

  private final Map<String, Boolean> myProfilesMap = new LinkedHashMap<>();

  private final Collection<String> myEnabledProfilesForXmlSerializer = new TreeSet<>();

  public MavenRunnerParameters() {
    this(true, "", null, null, null, null);
  }

  /**
   * @deprecated use {@link MavenRunnerParameters#MavenRunnerParameters(boolean, String, String, List, Collection)}
   */
  @Deprecated(forRemoval = true)
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

  public MavenRunnerParameters(boolean isPomExecution,
                               @NotNull String workingDirPath,
                               @Nullable String pomFileName,
                               @Nullable List<String> goals,
                               @NotNull MavenExplicitProfiles explicitProfiles) {
    this(isPomExecution, workingDirPath, pomFileName, goals, explicitProfiles.getEnabledProfiles(), explicitProfiles.getDisabledProfiles());
  }

  public MavenRunnerParameters(boolean isPomExecution,
                               @NotNull String workingDirPath,
                               @Nullable String pomFileName,
                               @Nullable List<String> goals,
                               @Nullable Collection<String> explicitEnabledProfiles,
                               @Nullable Collection<String> explicitDisabledProfiles) {
    this.isPomExecution = isPomExecution;
    setWorkingDirPath(workingDirPath);
    setPomFileName(pomFileName);
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

  public MavenRunnerParameters(@NotNull String workingDirPath,
                               @Nullable String pomFileName,
                               boolean isPomExecution,
                               @Nullable List<String> goals,
                               @NotNull Map<String, Boolean> profilesMap) {
    this.isPomExecution = isPomExecution;
    setWorkingDirPath(workingDirPath);
    setPomFileName(pomFileName);
    setGoals(goals);
    setProfilesMap(profilesMap);
  }

  public MavenRunnerParameters(MavenRunnerParameters that) {
    this(that.myWorkingDirPath, that.myPomFileName, that.isPomExecution, that.myGoals, that.myProfilesMap);
    myResolveToWorkspace = that.myResolveToWorkspace;
    setProjectsCmdOptionValues(that.myProjectsCmdOptionValues);
  }

  public boolean isPomExecution() {
    return isPomExecution;
  }

  public @NotNull @NlsSafe String getWorkingDirPath() {
    return myWorkingDirPath;
  }

  public void setWorkingDirPath(@NotNull @NlsSafe String workingDirPath) {
    myWorkingDirPath = workingDirPath;
  }

  public @NotNull File getWorkingDirFile() {
    assert myWorkingDirPath != null;
    return new File(myWorkingDirPath);
  }

  @Transient
  public void setCommandLine(@NotNull String value) {
    List<String> commandLine = ParametersListUtil.parse(value);
    int pomFileNameIndex = 1 + commandLine.indexOf("-f");
    if (pomFileNameIndex != 0) {
      if (pomFileNameIndex < commandLine.size()) {
        setPomFileName(commandLine.remove(pomFileNameIndex));
      }
      commandLine.remove(pomFileNameIndex - 1);
    }
    setGoals(commandLine);
  }

  @Transient
  public @NotNull String getCommandLine() {
    List<String> commandLine = new ArrayList<>(getGoals());
    String pomFileName = getPomFileName();
    if (pomFileName != null) {
      commandLine.add("-f");
      commandLine.add(pomFileName);
    }
    return ParametersList.join(commandLine);
  }

  public void setPomFileName(String pomFileName) {
    myPomFileName = pomFileName;
  }

  public @NlsSafe String getPomFileName() {
    return myPomFileName;
  }


  public @NlsSafe @Nullable String getMultimoduleDir() {
    return myMultimoduleDir;
  }

  public void setMultimoduleDir(@Nullable String multimoduleDir) {
    if (StringUtil.isEmptyOrSpaces(multimoduleDir)) {
      myMultimoduleDir = null;
    }
    else {
      myMultimoduleDir = multimoduleDir;
    }

  }

  public List<String> getGoals() {
    return Collections.unmodifiableList(myGoals);
  }

  public void setGoals(@Nullable List<String> goals) {
    if (myGoals == goals) return;  // Called from XML Serializer
    myGoals.clear();

    if (goals != null) {
      myGoals.addAll(goals);
    }
  }

  public List<String> getProjectsCmdOptionValues() {
    return myProjectsCmdOptionValues;
  }

  public void setProjectsCmdOptionValues(@Nullable List<String> projectsCmdOptionValues) {
    if (myProjectsCmdOptionValues == projectsCmdOptionValues) return;
    myProjectsCmdOptionValues.clear();

    if (projectsCmdOptionValues != null) {
      myProjectsCmdOptionValues.addAll(projectsCmdOptionValues);
    }
  }

  public List<String> getOptions() {
    List<String> options = new ArrayList<>();
    if (!myProjectsCmdOptionValues.isEmpty()) {
      options.add("--projects=" + String.join(",", myProjectsCmdOptionValues));
    }
    if (StringUtil.isNotEmpty(myCmdOptions)) {
      options.add(myCmdOptions);
    }
    return options;
  }

  public @Nullable String getCmdOptions() {
    return myCmdOptions;
  }

  public void setCmdOptions(@Nullable String cmdOptions) {
    myCmdOptions = cmdOptions;
  }

  /**
   * @deprecated Must be used by XML Serializer only!!!
   */
  @Deprecated
  @OptionTag("profiles")
  public Collection<String> getEnabledProfilesForXmlSerializer() {
    return myEnabledProfilesForXmlSerializer;
  }

  /**
   * @deprecated Must be used by XML Serializer only!!!
   */
  @Deprecated
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

  public boolean isResolveToWorkspace() {
    return myResolveToWorkspace;
  }

  public void setResolveToWorkspace(boolean resolveToWorkspace) {
    myResolveToWorkspace = resolveToWorkspace;
  }

  @Override
  public MavenRunnerParameters clone() {
    return new MavenRunnerParameters(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenRunnerParameters that = (MavenRunnerParameters)o;

    if (isPomExecution != that.isPomExecution) return false;
    if (myResolveToWorkspace != that.myResolveToWorkspace) return false;
    if (!myGoals.equals(that.myGoals)) return false;
    if (!myProjectsCmdOptionValues.equals(that.myProjectsCmdOptionValues)) return false;
    if (!Objects.equals(myWorkingDirPath, that.myWorkingDirPath)) return false;
    if (!Objects.equals(myPomFileName, that.myPomFileName)) return false;
    if (!Objects.equals(myMultimoduleDir, that.myMultimoduleDir)) return false;
    if (!myProfilesMap.equals(that.myProfilesMap)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = isPomExecution ? 1 : 0;
    result = 31 * result + (myWorkingDirPath != null ? myWorkingDirPath.hashCode() : 0);
    result = 31 * result + myGoals.hashCode();
    result = 31 * result + (myPomFileName != null ? myPomFileName.hashCode() : 0);
    result = 31 * result + (myMultimoduleDir != null ? myMultimoduleDir.hashCode() : 0);
    result = 31 * result + myProfilesMap.hashCode();
    return result;
  }
}
