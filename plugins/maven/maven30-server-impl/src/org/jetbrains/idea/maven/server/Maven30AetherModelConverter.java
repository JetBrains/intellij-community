/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.jetbrains.idea.maven.model.*;
import org.sonatype.aether.graph.DependencyNode;

import java.io.File;
import java.rmi.RemoteException;
import java.util.*;

/**
 * {@link Maven30AetherModelConverter} provides adapted methods of {@link MavenModelConverter} for aether models conversion
 *
 * @author Vladislav.Soroka
 * @since 9/24/2014
 */
public class Maven30AetherModelConverter extends MavenModelConverter {

  public static MavenModel convertModelWithAetherDependencyTree(Model model,
                                                                List<String> sources,
                                                                List<String> testSources,
                                                                Collection<Artifact> dependencies,
                                                                Collection<DependencyNode> dependencyTree,
                                                                Collection<Artifact> extensions,
                                                                File localRepository) throws RemoteException {
    MavenModel result = new MavenModel();
    result.setMavenId(new MavenId(model.getGroupId(), model.getArtifactId(), model.getVersion()));

    Parent parent = model.getParent();
    if (parent != null) {
      result.setParent(new MavenParent(new MavenId(parent.getGroupId(), parent.getArtifactId(), parent.getVersion()),
                                       parent.getRelativePath()));
    }
    result.setPackaging(model.getPackaging());
    result.setName(model.getName());
    result.setProperties(model.getProperties() == null ? new Properties() : model.getProperties());
    result.setPlugins(convertPlugins(model));

    Map<Artifact, MavenArtifact> convertedArtifacts = new THashMap<Artifact, MavenArtifact>();
    result.setExtensions(convertArtifacts(extensions, convertedArtifacts, localRepository));
    result.setDependencies(convertArtifacts(dependencies, convertedArtifacts, localRepository));
    result.setDependencyTree(convertAetherDependencyNodes(null, dependencyTree, convertedArtifacts, localRepository));

    result.setRemoteRepositories(convertRepositories(model.getRepositories()));
    result.setProfiles(convertProfiles(model.getProfiles()));
    result.setModules(model.getModules());

    convertBuild(result.getBuild(), model.getBuild(), sources, testSources);
    return result;
  }

  public static List<MavenArtifactNode> convertAetherDependencyNodes(MavenArtifactNode parent,
                                                                     Collection<DependencyNode> nodes,
                                                                     Map<Artifact, MavenArtifact> nativeToConvertedMap,
                                                                     File localRepository) {
    List<MavenArtifactNode> result = new ArrayList<MavenArtifactNode>(nodes.size());
    for (DependencyNode each : nodes) {
      Artifact a = RepositoryUtils.toArtifact(each.getDependency().getArtifact());
      MavenArtifact ma = convertArtifact(a, nativeToConvertedMap, localRepository);

      Map<?, ?> data = each.getData();
      //String premanagedVersion = DependencyManagerUtils.getPremanagedVersion(each);
      //String premanagedScope = DependencyManagerUtils.getPremanagedScope(each);

      //Object winner = data.get(ConflictResolver.NODE_DATA_WINNER);
      //if(winner instanceof DependencyNode) {
      //  DependencyNode winnerNode = (DependencyNode)winner;
      //  if(!StringUtil.equals(each.getVersion().toString(), winnerNode.getVersion().toString())) {
      //    state = MavenArtifactState.CONFLICT;
      //    Artifact winnerArtifact = RepositoryUtils.toArtifact(winnerNode.getDependency().getArtifact());
      //    relatedArtifact = convertArtifact(winnerArtifact, nativeToConvertedMap, localRepository);
      //  }
      //}
      //

      MavenArtifactNode newNode = new MavenArtifactNode(
        parent, ma, MavenArtifactState.ADDED, null, each.getDependency().getScope(),
        each.getPremanagedVersion(), each.getPremanagedScope());
      newNode.setDependencies(convertAetherDependencyNodes(newNode, each.getChildren(), nativeToConvertedMap, localRepository));
      result.add(newNode);
    }
    return result;
  }
}
