// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.auth.PasswordAuthenticationData;
import org.jetbrains.idea.svn.properties.PropertyValue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

// TODO: Probably make command immutable and use CommandBuilder for updates.
public class Command {

  private final @NotNull List<String> myParameters = new ArrayList<>();
  private final @NotNull SvnCommandName myName;
  private @Nullable PasswordAuthenticationData myAuthParameters = null;

  private File workingDirectory;
  private @Nullable File myConfigDir;
  private @Nullable LineCommandListener myResultBuilder;
  private volatile @Nullable Url myRepositoryUrl;
  private @NotNull Target myTarget;
  private @Unmodifiable Collection<? extends File> myTargets;
  private @Nullable PropertyValue myPropertyValue;

  private @Nullable ProgressTracker myCanceller;

  public Command(@NotNull SvnCommandName name) {
    myName = name;
  }

  public void put(@Nullable Depth depth) {
    CommandUtil.put(myParameters, depth, false);
  }

  public void put(@NotNull File path) {
    CommandUtil.put(myParameters, path);
  }

  public void put(@NotNull Target target) {
    CommandUtil.put(myParameters, target);
  }

  public void put(@Nullable Revision revision) {
    CommandUtil.put(myParameters, revision);
  }

  public void put(@NotNull String parameter, boolean condition) {
    CommandUtil.put(myParameters, condition, parameter);
  }

  public void put(@NonNls String @NotNull ... parameters) {
    put(Arrays.asList(parameters));
  }

  public void put(@NotNull List<String> parameters) {
    myParameters.addAll(parameters);
  }

  public void putIfNotPresent(@NotNull String parameter) {
    if (!myParameters.contains(parameter)) {
      myParameters.add(parameter);
    }
  }

  public void putAuth(@Nullable PasswordAuthenticationData authData) {
    myAuthParameters = authData;
  }

  public @Nullable ProgressTracker getCanceller() {
    return myCanceller;
  }

  public void setCanceller(@Nullable ProgressTracker canceller) {
    myCanceller = canceller;
  }

  public @Nullable File getConfigDir() {
    return myConfigDir;
  }

  public File getWorkingDirectory() {
    return workingDirectory;
  }

  public @Nullable LineCommandListener getResultBuilder() {
    return myResultBuilder;
  }

  public @Nullable Url getRepositoryUrl() {
    return myRepositoryUrl;
  }

  public @NotNull Url requireRepositoryUrl() {
    Url result = getRepositoryUrl();
    assert result != null;

    return result;
  }

  public @NotNull Target getTarget() {
    return myTarget;
  }

  public @Nullable List<String> getTargetsPaths() {
    return ContainerUtil.isEmpty(myTargets) ? null : ContainerUtil.map(myTargets, file -> CommandUtil.format(file.getAbsolutePath(), null));
  }

  public @Nullable PropertyValue getPropertyValue() {
    return myPropertyValue;
  }

  public @NotNull SvnCommandName getName() {
    return myName;
  }

  public void setWorkingDirectory(File workingDirectory) {
    this.workingDirectory = workingDirectory;
  }

  public void setConfigDir(@Nullable File configDir) {
    this.myConfigDir = configDir;
  }

  public void setResultBuilder(@Nullable LineCommandListener resultBuilder) {
    myResultBuilder = resultBuilder;
  }

  public void setRepositoryUrl(@Nullable Url repositoryUrl) {
    myRepositoryUrl = repositoryUrl;
  }

  public void setTarget(@NotNull Target target) {
    myTarget = target;
  }

  public void setTargets(@Nullable @Unmodifiable Collection<? extends File> targets) {
    myTargets = targets;
  }

  public void setPropertyValue(@Nullable PropertyValue propertyValue) {
    myPropertyValue = propertyValue;
  }

  public @NotNull List<String> getParameters() {
    return new ArrayList<>(myParameters);
  }

  public @Nullable PasswordAuthenticationData getAuthParameters() {
    return myAuthParameters;
  }

  public @NlsSafe @NotNull String getText() {
    List<String> data = new ArrayList<>();

    if (myConfigDir != null) {
      data.add("--config-dir");
      data.add(myConfigDir.getPath());
    }
    data.add(myName.getName());
    data.addAll(myParameters);

    List<String> targetsPaths = getTargetsPaths();
    if (!ContainerUtil.isEmpty(targetsPaths)) {
      data.addAll(targetsPaths);
    }

    return StringUtil.join(data, " ");
  }

  public boolean isLocalInfo() {
    return is(SvnCommandName.info) && hasLocalTarget() && !myParameters.contains("--revision");
  }

  public boolean isLocalStatus() {
    return is(SvnCommandName.st) && hasLocalTarget() && !myParameters.contains("-u");
  }

  public boolean isLocalProperty() {
    boolean isPropertyCommand =
      is(SvnCommandName.proplist) || is(SvnCommandName.propget) || is(SvnCommandName.propset) || is(SvnCommandName.propdel);

    return isPropertyCommand && hasLocalTarget() && isLocal(getRevision());
  }

  public boolean isLocalCat() {
    return is(SvnCommandName.cat) && hasLocalTarget() && isLocal(getRevision());
  }

  private @Nullable Revision getRevision() {
    int index = myParameters.indexOf("--revision");

    return index >= 0 && index + 1 < myParameters.size() ? Revision.parse(myParameters.get(index + 1)) : null;
  }

  public boolean is(@NotNull SvnCommandName name) {
    return name.equals(myName);
  }

  private boolean hasLocalTarget() {
    return myTarget.isFile() && isLocal(myTarget.getPegRevision());
  }

  private static boolean isLocal(@Nullable Revision revision) {
    return revision == null ||
           Revision.UNDEFINED.equals(revision) ||
           Revision.BASE.equals(revision) ||
           Revision.WORKING.equals(revision);
  }
}