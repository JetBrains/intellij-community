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
package org.jetbrains.idea.maven.server.embedder;

import com.intellij.util.ReflectionUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.model.*;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jdom.Element;
import org.jdom.IllegalNameException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.Maven2ServerGlobals;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.rmi.RemoteException;
import java.util.*;

public class Maven2ModelConverter {
  @NotNull
  public static MavenModel convertModel(Model model, File localRepository) throws RemoteException {
    Build build = model.getBuild();
    return convertModel(model,
                        asSourcesList(build.getSourceDirectory()),
                        asSourcesList(build.getTestSourceDirectory()),
                        Collections.<Artifact>emptyList(),
                        Collections.<DependencyNode>emptyList(),
                        Collections.<Artifact>emptyList(),
                        localRepository);
  }

  private static List<String> asSourcesList(String directory) {
    return directory == null ? Collections.<String>emptyList() : Collections.singletonList(directory);
  }

  @NotNull
  public static MavenModel convertModel(Model model,
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
    result.setDependencyTree(convertDependencyNodes(null, dependencyTree, convertedArtifacts, localRepository));

    result.setRemoteRepositories(convertRepositories(model.getRepositories()));
    result.setProfiles(convertProfiles(model.getProfiles()));
    result.setModules(model.getModules());

    convertBuild(result.getBuild(), model.getBuild(), sources, testSources);
    return result;
  }

  private static void convertBuild(MavenBuild result, Build build, List<String> sources, List<String> testSources) {
    convertBuildBase(result, build);
    result.setOutputDirectory(build.getOutputDirectory());
    result.setTestOutputDirectory(build.getTestOutputDirectory());
    result.setSources(sources);
    result.setTestSources(testSources);
  }

  private static void convertBuildBase(MavenBuildBase result, BuildBase build) {
    result.setFinalName(build.getFinalName());
    result.setDefaultGoal(build.getDefaultGoal());
    result.setDirectory(build.getDirectory());
    result.setResources(convertResources(build.getResources()));
    result.setTestResources(convertResources(build.getTestResources()));
    result.setFilters(build.getFilters() == null ? Collections.<String>emptyList() : build.getFilters());
  }

  public static MavenId createMavenId(Artifact artifact) {
    return new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
  }

  private static List<MavenResource> convertResources(List<Resource> resources) {
    if (resources == null) return new ArrayList<MavenResource>();

    List<MavenResource> result = new ArrayList<MavenResource>(resources.size());
    for (Resource each : resources) {
      result.add(new MavenResource(each.getDirectory(),
                                   each.isFiltering(),
                                   each.getTargetPath(),
                                   ensurePatterns(each.getIncludes()),
                                   ensurePatterns(each.getExcludes())));
    }
    return result;
  }

  private static List<String> ensurePatterns(List<String> patterns) {
    return patterns == null ? Collections.<String>emptyList() : patterns;
  }

  private static List<MavenRemoteRepository> convertRepositories(List<Repository> repositories) {
    if (repositories == null) return new ArrayList<MavenRemoteRepository>();

    List<MavenRemoteRepository> result = new ArrayList<MavenRemoteRepository>(repositories.size());
    for (Repository each : repositories) {
      result.add(new MavenRemoteRepository(each.getId(),
                                           each.getName(),
                                           each.getUrl(),
                                           each.getLayout(),
                                           convertPolicy(each.getReleases()),
                                           convertPolicy(each.getSnapshots())));
    }
    return result;
  }

  private static MavenRemoteRepository.Policy convertPolicy(RepositoryPolicy policy) {
    return policy != null
           ? new MavenRemoteRepository.Policy(policy.isEnabled(), policy.getUpdatePolicy(), policy.getChecksumPolicy())
           : null;
  }

  public static List<MavenArtifact> convertArtifacts(Collection<Artifact> artifacts,
                                                      Map<Artifact, MavenArtifact> nativeToConvertedMap,
                                                      File localRepository) {
    if (artifacts == null) return new ArrayList<MavenArtifact>();

    List<MavenArtifact> result = new ArrayList<MavenArtifact>(artifacts.size());
    for (Artifact each : artifacts) {
      result.add(convertArtifact(each, nativeToConvertedMap, localRepository));
    }
    return result;
  }

  public static List<MavenArtifactNode> convertDependencyNodes(MavenArtifactNode parent,
                                                               Collection<DependencyNode> nodes,
                                                               Map<Artifact, MavenArtifact> nativeToConvertedMap,
                                                               File localRepository) {
    List<MavenArtifactNode> result = new ArrayList<MavenArtifactNode>(nodes.size());
    for (DependencyNode each : nodes) {
      Artifact a = each.getArtifact();
      MavenArtifact ma = convertArtifact(a, nativeToConvertedMap, localRepository);

      MavenArtifactState state = MavenArtifactState.ADDED;
      switch (each.getState()) {
        case DependencyNode.INCLUDED:
          break;
        case DependencyNode.OMITTED_FOR_CONFLICT:
          state = MavenArtifactState.CONFLICT;
          break;
        case DependencyNode.OMITTED_FOR_DUPLICATE:
          state = MavenArtifactState.DUPLICATE;
          break;
        case DependencyNode.OMITTED_FOR_CYCLE:
          state = MavenArtifactState.CYCLE;
          break;
        default:
          assert false : "unknown dependency node state: " + each.getState();
      }
      MavenArtifact relatedMA = each.getRelatedArtifact() == null ? null
                                                                  : convertArtifact(each.getRelatedArtifact(), nativeToConvertedMap,
                                                                                    localRepository);
      MavenArtifactNode newNode = new MavenArtifactNode(parent, ma, state, relatedMA, each.getOriginalScope(),
                                                        each.getPremanagedVersion(), each.getPremanagedScope());
      newNode.setDependencies(convertDependencyNodes(newNode, each.getChildren(), nativeToConvertedMap, localRepository));
      result.add(newNode);
    }
    return result;
  }

  private static MavenArtifact convertArtifact(Artifact artifact, Map<Artifact, MavenArtifact> nativeToConvertedMap, File localRepository) {
    MavenArtifact result = nativeToConvertedMap.get(artifact);
    if (result == null) {
      result = convertArtifact(artifact, localRepository);
      nativeToConvertedMap.put(artifact, result);
    }
    return result;
  }

  public static MavenArtifact convertArtifact(Artifact artifact, File localRepository) {
    return new MavenArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                             artifact.getVersion(),
                             artifact.getBaseVersion(),
                             artifact.getType(),
                             artifact.getClassifier(),

                             artifact.getScope(),
                             artifact.isOptional(),

                             convertExtension(artifact),

                             artifact.getFile(),
                             localRepository,

                             artifact.isResolved(),
                             artifact instanceof CustomArtifact && ((CustomArtifact)artifact).isStub());
  }

  private static String convertExtension(Artifact artifact) {
    ArtifactHandler handler = artifact.getArtifactHandler();
    String result = null;
    if (handler != null) result = handler.getExtension();
    if (result == null) result = artifact.getType();
    return result;
  }

  private static List<MavenPlugin> convertPlugins(Model mavenModel) throws RemoteException {
    List<MavenPlugin> result = new ArrayList<MavenPlugin>();
    Set<String> pluginKeys = new THashSet<String>();
    Build build = mavenModel.getBuild();
    doConvertPlugins(build, false, result, pluginKeys);
    if (build != null) doConvertPlugins(build.getPluginManagement(), true, result, pluginKeys);
    return result;
  }

  private static void doConvertPlugins(PluginContainer container,
                                       boolean management,
                                       List<MavenPlugin> result,
                                       Set<String> pluginKeys) throws RemoteException {
    if (container == null) return;

    List<Plugin> plugins = container.getPlugins();
    if (plugins == null) return;

    for (Plugin each : plugins) {
      String key = each.getGroupId() + ":" + each.getArtifactId();
      result.add(convertPlugin(management, each));
      pluginKeys.add(key);
    }
  }

  private static MavenPlugin convertPlugin(boolean isDefault, Plugin plugin) throws RemoteException {
    List<MavenPlugin.Execution> executions = new ArrayList<MavenPlugin.Execution>(plugin.getExecutions().size());
    for (PluginExecution each : plugin.getExecutions()) {
      executions.add(convertExecution(each));
    }

    List<MavenId> deps = new ArrayList<MavenId>(plugin.getDependencies().size());
    for (Dependency each : plugin.getDependencies()) {
      deps.add(new MavenId(each.getGroupId(), each.getArtifactId(), each.getVersion()));
    }

    return new MavenPlugin(plugin.getGroupId(),
                           plugin.getArtifactId(),
                           plugin.getVersion(),
                           isDefault,
                           plugin.isExtensions(),
                           convertConfiguration(plugin.getConfiguration()),
                           executions, deps);
  }

  public static MavenPlugin.Execution convertExecution(PluginExecution execution) throws RemoteException {
    return new MavenPlugin.Execution(execution.getId(), execution.getPhase(), execution.getGoals(), convertConfiguration(execution.getConfiguration()));
  }

  private static Element convertConfiguration(Object config) throws RemoteException {
    return config == null ? null : xppToElement((Xpp3Dom)config);
  }

  private static Element xppToElement(Xpp3Dom xpp) throws RemoteException {
    Element result;
    try {
      result = new Element(xpp.getName());
    }
    catch (IllegalNameException e) {
      Maven2ServerGlobals.getLogger().info(e);
      return null;
    }

    Xpp3Dom[] children = xpp.getChildren();
    if (children == null || children.length == 0) {
      result.setText(xpp.getValue());
    }
    else {
      for (Xpp3Dom each : children) {
        Element child = xppToElement(each);
        if (child != null) result.addContent(child);
      }
    }
    return result;
  }

  public static List<MavenProfile> convertProfiles(Collection<Profile> profiles) {
    if (profiles == null) return Collections.emptyList();
    List<MavenProfile> result = new ArrayList<MavenProfile>();
    for (Profile each : profiles) {
      String id = each.getId();
      if (id == null) continue;
      MavenProfile profile = new MavenProfile(id, each.getSource());
      List<String> modules = each.getModules();
      profile.setModules(modules == null ? Collections.<String>emptyList() : modules);
      profile.setActivation(convertActivation(each.getActivation()));
      if (each.getBuild() != null) convertBuildBase(profile.getBuild(), each.getBuild());
      result.add(profile);
    }
    return result;
  }

  private static MavenProfileActivation convertActivation(Activation activation) {
    if (activation == null) return null;

    MavenProfileActivation result = new MavenProfileActivation();
    result.setActiveByDefault(activation.isActiveByDefault());
    result.setOs(convertOsActivation(activation.getOs()));
    result.setJdk(activation.getJdk());
    result.setFile(convertFileActivation(activation.getFile()));
    result.setProperty(convertPropertyActivation(activation.getProperty()));
    return result;
  }

  private static MavenProfileActivationOS convertOsActivation(ActivationOS os) {
    return os == null ? null : new MavenProfileActivationOS(os.getName(), os.getFamily(), os.getArch(), os.getVersion());
  }

  private static MavenProfileActivationFile convertFileActivation(ActivationFile file) {
    return file == null ? null : new MavenProfileActivationFile(file.getExists(), file.getMissing());
  }

  private static MavenProfileActivationProperty convertPropertyActivation(ActivationProperty property) {
    return property == null ? null : new MavenProfileActivationProperty(property.getName(), property.getValue());
  }

  public static Map<String, String> convertToMap(Object object) {
    try {
      Map<String, String> result = new THashMap<String, String>();
      doConvert(object, "", result);
      return result;
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean isNativeToString(String toStringResult, Object o) {
    String className = o.getClass().getName();
    return (toStringResult.startsWith(className) && toStringResult.startsWith("@", className.length()));
  }

  private static void doConvert(Object object, String prefix, Map<String, String> result) throws IllegalAccessException {
    for (Field each : ReflectionUtil.collectFields(object.getClass())) {
      Class<?> type = each.getType();
      if (shouldSkip(type)) continue;

      each.setAccessible(true);
      Object value = each.get(object);

      if (value != null) {
        String name = prefix + each.getName();

        String sValue = String.valueOf(value);
        if (!isNativeToString(sValue, value)) {
          result.put(name, sValue);
        }

        Package pack = type.getPackage();
        if (pack != null && Model.class.getPackage().getName().equals(pack.getName())) {
          doConvert(value, name + ".", result);
        }
      }
    }
  }

  public static boolean shouldSkip(Class clazz) {
    return clazz.isArray()
           || Collection.class.isAssignableFrom(clazz)
           || Map.class.isAssignableFrom(clazz)
           || Xpp3Dom.class.isAssignableFrom(clazz);
  }

  public static MavenArchetype convertArchetype(Archetype archetype) {
    return new MavenArchetype(archetype.getGroupId(),
                              archetype.getArtifactId(),
                              archetype.getVersion(),
                              archetype.getRepository(),
                              archetype.getDescription());
  }

  public static Model toNativeModel(MavenModel model) {
    Model result = new Model();
    result.setArtifactId(model.getMavenId().getArtifactId());
    result.setGroupId(model.getMavenId().getGroupId());
    result.setVersion(model.getMavenId().getVersion());
    result.setPackaging(model.getPackaging());
    result.setName(model.getName());

    if (model.getParent() != null) {
      Parent parent = new Parent();
      parent.setArtifactId(model.getParent().getMavenId().getArtifactId());
      parent.setGroupId(model.getParent().getMavenId().getGroupId());
      parent.setVersion(model.getParent().getMavenId().getVersion());
      parent.setRelativePath(model.getParent().getRelativePath());
      result.setParent(parent);
    }
    toNativeModelBase(model, result);

    result.setBuild(new Build());
    MavenBuild modelBuild = model.getBuild();
    toNativeBuildBase(modelBuild, result.getBuild());
    result.getBuild().setOutputDirectory(modelBuild.getOutputDirectory());
    result.getBuild().setTestOutputDirectory(modelBuild.getTestOutputDirectory());

    if (modelBuild.getSources().size() > 1) throw new RuntimeException("too many source directories: " + modelBuild.getSources());
    if (modelBuild.getTestSources().size() > 1) throw new RuntimeException("too many test directories: " + modelBuild.getTestSources());

    if (modelBuild.getSources().size() == 1) {
      result.getBuild().setSourceDirectory(modelBuild.getSources().get(0));
    }
    if (modelBuild.getTestSources().size() == 1) {
      result.getBuild().setTestSourceDirectory(modelBuild.getTestSources().get(0));
    }

    result.setProfiles(toNativeProfiles(model.getProfiles()));

    return result;
  }

  private static List<Profile> toNativeProfiles(List<MavenProfile> profiles) {
    List<Profile> result = new ArrayList<Profile>(profiles.size());
    for (MavenProfile each : profiles) {
      Profile p = new Profile();
      p.setId(each.getId());
      p.setSource(each.getSource());
      p.setBuild(new Build());
      p.setActivation(toNativeActivation(each.getActivation()));
      toNativeModelBase(each, p);
      toNativeBuildBase(each.getBuild(), p.getBuild());
      result.add(p);
    }
    return result;
  }

  private static Activation toNativeActivation(MavenProfileActivation activation) {
    if (activation == null) return null;
    Activation result = new Activation();
    result.setActiveByDefault(activation.isActiveByDefault());
    result.setJdk(activation.getJdk());
    result.setOs(toNativeOsActivation(activation.getOs()));
    result.setFile(toNativeFileActivation(activation.getFile()));
    result.setProperty(toNativePropertyActivation(activation.getProperty()));
    return result;
  }

  private static ActivationOS toNativeOsActivation(MavenProfileActivationOS os) {
    if (os == null) return null;
    ActivationOS result = new ActivationOS();
    result.setArch(os.getArch());
    result.setFamily(os.getFamily());
    result.setName(os.getName());
    result.setVersion(os.getVersion());
    return result;
  }

  private static ActivationFile toNativeFileActivation(MavenProfileActivationFile file) {
    if (file == null) return null;
    ActivationFile result = new ActivationFile();
    result.setExists(file.getExists());
    result.setMissing(file.getMissing());
    return result;
  }

  private static ActivationProperty toNativePropertyActivation(MavenProfileActivationProperty property) {
    if (property == null) return null;
    ActivationProperty result = new ActivationProperty();
    result.setName(property.getName());
    result.setValue(property.getValue());
    return result;
  }

  private static void toNativeModelBase(MavenModelBase from, ModelBase to) {
    to.setModules(from.getModules());
    to.setProperties(from.getProperties());
  }

  private static void toNativeBuildBase(MavenBuildBase from, BuildBase to) {
    to.setFinalName(from.getFinalName());
    to.setDefaultGoal(from.getDefaultGoal());
    to.setDirectory(from.getDirectory());
    to.setFilters(from.getFilters());
    to.setResources(toNativeResources(from.getResources()));
    to.setTestResources(toNativeResources(from.getTestResources()));
  }

  private static List<Resource> toNativeResources(List<MavenResource> resources) {
    List<Resource> result = new ArrayList<Resource>(resources.size());
    for (MavenResource each : resources) {
      Resource r = new Resource();
      r.setDirectory(each.getDirectory());
      r.setTargetPath(each.getTargetPath());
      r.setFiltering(each.isFiltered());
      r.setIncludes(each.getIncludes());
      r.setExcludes(each.getExcludes());
      result.add(r);
    }
    return result;
  }

  public static Repository toNativeRepository(MavenRemoteRepository r) {
    Repository result = new Repository();
    result.setId(r.getId());
    result.setName(r.getName());
    result.setUrl(r.getUrl());
    result.setLayout(r.getLayout() == null ? "default" : r.getLayout());

    if (r.getReleasesPolicy() != null) result.setReleases(toNativePolicy(r.getReleasesPolicy()));
    if (r.getSnapshotsPolicy() != null) result.setSnapshots(toNativePolicy(r.getSnapshotsPolicy()));

    return result;
  }

  private static RepositoryPolicy toNativePolicy(MavenRemoteRepository.Policy policy) {
    RepositoryPolicy result = new RepositoryPolicy();
    result.setEnabled(policy.isEnabled());
    result.setUpdatePolicy(policy.getUpdatePolicy());
    result.setChecksumPolicy(policy.getChecksumPolicy());
    return result;
  }

  public static MavenArtifactInfo convertArtifactInfo(ArtifactInfo a) {
    return new MavenArtifactInfo(a.groupId, a.artifactId, a.version, a.packaging, a.classifier, a.classNames, a.repository);
  }
}

