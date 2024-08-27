// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import org.jetbrains.idea.maven.model.*;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class MavenProjectState implements Cloneable, Serializable {
  long myLastReadStamp = 0;

  MavenId myMavenId;
  MavenId myParentId;
  String myPackaging;
  String myName;

  String myFinalName;
  String myDefaultGoal;

  String myBuildDirectory;
  String myOutputDirectory;
  String myTestOutputDirectory;

  List<String> mySources;
  List<String> myTestSources;
  List<MavenResource> myResources;
  List<MavenResource> myTestResources;

  List<String> myFilters;
  Properties myProperties;
  List<MavenPlugin> myPlugins;
  List<MavenArtifact> myExtensions;

  List<MavenArtifact> myDependencies;
  List<MavenArtifactNode> myDependencyTree;
  List<MavenRemoteRepository> myRemoteRepositories;
  List<MavenArtifact> myAnnotationProcessors;

  Map<String, String> myModulesPathsAndNames;

  Map<String, String> myModelMap;

  Collection<String> myProfilesIds;
  MavenExplicitProfiles myActivatedProfilesIds;
  String myDependencyHash;

  Collection<MavenProjectProblem> myReadingProblems;
  Set<MavenId> myUnresolvedArtifactIds;
  File myLocalRepository;

  volatile List<MavenProjectProblem> myProblemsCache;
  volatile List<MavenArtifact> myUnresolvedDependenciesCache;
  volatile List<MavenPlugin> myUnresolvedPluginsCache;
  volatile List<MavenArtifact> myUnresolvedExtensionsCache;
  volatile List<MavenArtifact> myUnresolvedAnnotationProcessors;

  transient ConcurrentHashMap<Key<?>, Object> myCache = new ConcurrentHashMap<>();

  @Override
  public MavenProjectState clone() {
    try {
      MavenProjectState result = (MavenProjectState)super.clone();
      myCache = new ConcurrentHashMap<>();
      result.resetCache();
      return result;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  void resetCache() {
    myProblemsCache = null;
    myUnresolvedDependenciesCache = null;
    myUnresolvedPluginsCache = null;
    myUnresolvedExtensionsCache = null;
    myUnresolvedAnnotationProcessors = null;

    myCache.clear();
  }

  public MavenProjectChanges getChanges(MavenProjectState newState) {
    if (myLastReadStamp == 0) return MavenProjectChanges.ALL;

    MavenProjectChangesBuilder result = new MavenProjectChangesBuilder();

    result.setHasPackagingChanges(!Objects.equals(myPackaging, newState.myPackaging));

    result.setHasOutputChanges(!Objects.equals(myFinalName, newState.myFinalName)
                               || !Objects.equals(myBuildDirectory, newState.myBuildDirectory)
                               || !Objects.equals(myOutputDirectory, newState.myOutputDirectory)
                               || !Objects.equals(myTestOutputDirectory, newState.myTestOutputDirectory));

    result.setHasSourceChanges(!Comparing.equal(mySources, newState.mySources)
                               || !Comparing.equal(myTestSources, newState.myTestSources)
                               || !Comparing.equal(myResources, newState.myResources)
                               || !Comparing.equal(myTestResources, newState.myTestResources));

    boolean repositoryChanged = !Comparing.equal(myLocalRepository, newState.myLocalRepository);

    result.setHasDependencyChanges(repositoryChanged || !Comparing.equal(myDependencies, newState.myDependencies));
    result.setHasPluginChanges(repositoryChanged || !Comparing.equal(myPlugins, newState.myPlugins));
    result.setHasPropertyChanges(!Comparing.equal(myProperties, newState.myProperties));
    return result;
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    myCache = new ConcurrentHashMap<>();
  }
}
