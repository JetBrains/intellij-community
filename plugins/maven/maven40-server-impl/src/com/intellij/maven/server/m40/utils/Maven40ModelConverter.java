// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import com.intellij.util.ReflectionUtilRt;
import org.apache.maven.api.SourceRoot;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.model.*;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jdom.Element;
import org.jdom.IllegalNameException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.MavenServerGlobals;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class Maven40ModelConverter {
  public static @NotNull MavenModel convertModel(Model model) {
    if (model.getBuild() == null) {
      model.setBuild(new Build());
    }
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
    result.setPlugins(convertPlugins(model, Collections.emptyList()));

    result.setRemoteRepositories(convertRepositories(model.getRepositories()));
    result.setRemotePluginRepositories(convertRepositories(model.getPluginRepositories()));
    result.setProfiles(convertProfiles(model.getProfiles()));
    result.setModules(model.getModules());

    convertBuild(result.getBuild(), model.getBuild());
    return result;
  }

  protected static List<MavenPlugin> convertPlugins(Model mavenModel, Collection<? extends Artifact> pluginArtifacts) {
    List<MavenPlugin> result = new ArrayList<>();
    Build build = mavenModel.getBuild();

    if (build != null) {
      List<Plugin> plugins = build.getPlugins();
      if (plugins != null) {
        for (Plugin each : plugins) {
          result.add(convertPlugin(each, pluginArtifacts));
        }
      }
    }

    return result;
  }

  private static MavenPlugin convertPlugin(Plugin plugin, Collection<? extends Artifact> pluginArtifacts) {
    List<MavenPlugin.Execution> executions = new ArrayList<>(plugin.getExecutions().size());
    for (PluginExecution each : plugin.getExecutions()) {
      executions.add(convertExecution(each));
    }

    List<MavenId> deps = new ArrayList<>(plugin.getDependencies().size());
    for (Dependency each : plugin.getDependencies()) {
      deps.add(new MavenId(each.getGroupId(), each.getArtifactId(), each.getVersion()));
    }

    String pluginVersion = getPluginVersion(plugin, pluginArtifacts);
    return new MavenPlugin(plugin.getGroupId(),
                           plugin.getArtifactId(),
                           pluginVersion,
                           false,
                           "true".equals(plugin.getExtensions()),
                           convertConfiguration(plugin.getConfiguration()),
                           executions, deps);
  }

  private static String getPluginVersion(Plugin plugin, Collection<? extends Artifact> pluginArtifacts) {
    String pluginVersion = plugin.getVersion();
    if (null != pluginVersion) return pluginVersion;
    if (null == plugin.getGroupId() || null == plugin.getArtifactId()) return null;
    for (Artifact each : pluginArtifacts) {
      if (plugin.getGroupId().equals(each.getGroupId()) && plugin.getArtifactId().equals(each.getArtifactId())) {
        return each.getVersion();
      }
    }
    return null;
  }

  public static MavenPlugin.Execution convertExecution(PluginExecution execution) {
    return new MavenPlugin.Execution(execution.getId(), execution.getPhase(), execution.getGoals(),
                                     convertConfiguration(execution.getConfiguration()));
  }

  private static Element convertConfiguration(Object config) {
    return config == null ? null : xppToElement((Xpp3Dom)config);
  }

  protected static Element xppToElement(Xpp3Dom xpp) {
    Element result;
    try {
      result = new Element(xpp.getName());
    }
    catch (IllegalNameException e) {
      MavenServerGlobals.getLogger().info(e);
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

  private static List<String> asSourcesList(String directory) {
    return directory == null ? Collections.emptyList() : Collections.singletonList(directory);
  }

  public static void convertBuild(MavenBuild result, Build build) {
    convertBuildBase(result, build);
    result.setOutputDirectory(build.getOutputDirectory());
    result.setTestOutputDirectory(build.getTestOutputDirectory());
    setupSourceDirectories(result, build);
  }


  private static void setupSourceDirectories(MavenBuild result, Build build) {

    /*
     *  `sourceDirectory`, `testSourceDirectory` and `scriptSourceDirectory`
     * are ignored if the POM file contains at least one <source> element
     * for the corresponding scope and language. This rule exists because
     * Maven provides default values for those elements which may conflict
     * with user's configuration.
     */
    List<org.apache.maven.api.model.Source> sourceList = build.getDelegate().getSources();
    if (sourceList.isEmpty()) {
      List<String> sources = asSourcesList(build.getSourceDirectory());
      List<String> testSources = asSourcesList(build.getTestSourceDirectory());
      result.setSources(sources);
      result.setTestSources(testSources);
      result.setResources(convertResources(build.getResources()));
      result.setTestResources(convertResources(build.getTestResources()));
    }
    else {
      List<MavenSource> list = new ArrayList<>();
      for (org.apache.maven.api.model.Source it : sourceList) {
        MavenSource source = convert(it);
        list.add(source);
      }
      result.setMavenSources(list);
    }
  }

  public static @NotNull MavenSource convert(org.apache.maven.api.model.Source it) {
    return MavenSource.fromSourceTag(
      it.getDirectory(),
      it.getIncludes(),
      it.getExcludes(),
      it.getScope(),
      it.getLang(),
      it.getTargetPath(),
      it.getTargetVersion(),
      it.isStringFiltering(),
      it.isEnabled()
    );
  }

  public static @NotNull MavenSource convert(SourceRoot it) {
    var scope = it.scope() == null ? null : it.scope().id();
    var lang = it.language() == null ? null : it.language().id();
    return MavenSource.fromSourceTag(
      it.directory().toString(),
      Collections.emptyList(),
      Collections.emptyList(),
      scope,
      lang,
      it.targetPath().map(tp -> tp.toString()).orElse(null),
      it.targetVersion().map(tv -> tv.toString()).orElse(null),
      it.stringFiltering(),
      it.enabled()
    );
  }

  private static void convertBuildBase(MavenBuildBase result, BuildBase build) {
    result.setFinalName(build.getFinalName());
    result.setDefaultGoal(build.getDefaultGoal());
    result.setDirectory(build.getDirectory());
    result.setFilters(build.getFilters() == null ? Collections.emptyList() : build.getFilters());
  }

  public static MavenId createMavenId(Artifact artifact) {
    return new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
  }

  public static List<MavenResource> convertResources(List<Resource> resources) {
    if (resources == null) return new ArrayList<MavenResource>();

    List<MavenResource> result = new ArrayList<MavenResource>(resources.size());
    for (Resource each : resources) {
      String directory = each.getDirectory();

      if (null == directory) continue;

      result.add(new MavenResource(directory,
                                  each.isFiltering(),
                                  each.getTargetPath(),
                                  ensurePatterns(each.getIncludes()),
                                  ensurePatterns(each.getExcludes())));
    }
    return result;
  }

  private static List<String> ensurePatterns(List<String> patterns) {
    return patterns == null ? Collections.emptyList() : patterns;
  }

  public static List<MavenRemoteRepository> convertRepositories(List<? extends Repository> repositories) {
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

  public static List<MavenRemoteRepository> convertRemoteRepositories(List<? extends ArtifactRepository> repositories) {
    if (repositories == null) return new ArrayList<MavenRemoteRepository>();

    List<MavenRemoteRepository> result = new ArrayList<MavenRemoteRepository>(repositories.size());
    for (ArtifactRepository each : repositories) {
      result.add(new MavenRemoteRepository(each.getId(),
                                           each.getId(),
                                           each.getUrl(),
                                           each.getLayout() != null ? each.getLayout().getId() : "default",
                                           convertPolicy(each.getReleases()),
                                           convertPolicy(each.getSnapshots())));
    }
    return result;
  }


  public static MavenRemoteRepository.Policy convertPolicy(RepositoryPolicy policy) {
    return policy != null
           ? new MavenRemoteRepository.Policy(policy.isEnabled(), policy.getUpdatePolicy(), policy.getChecksumPolicy())
           : null;
  }

  private static MavenRemoteRepository.Policy convertPolicy(ArtifactRepositoryPolicy policy) {
    return policy != null
           ? new MavenRemoteRepository.Policy(policy.isEnabled(), policy.getUpdatePolicy(), policy.getChecksumPolicy())
           : null;
  }

  private static String convertExtension(Artifact artifact) {
    ArtifactHandler handler = artifact.getArtifactHandler();
    String result = null;
    if (handler != null) result = handler.getExtension();
    if (result == null) result = artifact.getType();
    return result;
  }

  public static List<MavenProfile> convertProfiles(Collection<? extends Profile> profiles) {
    if (profiles == null) return Collections.emptyList();
    List<MavenProfile> result = new ArrayList<MavenProfile>();
    for (Profile each : profiles) {
      String id = each.getId();
      if (id == null) continue;
      MavenProfile profile = new MavenProfile(id, each.getSource());
      List<String> modules = each.getModules();
      profile.setModules(modules == null ? Collections.emptyList() : modules);
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
      Map<String, String> result = new HashMap<String, String>();
      doConvert(object, "", result);
      return result;
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean isNativeToString(String toStringResult, Object o) {
    String className = o.getClass().getName();
    return (toStringResult.startsWith(className) && toStringResult.startsWith("@", className.length()));
  }

  private static void doConvert(Object object, String prefix, Map<String, String> result)
    throws IllegalAccessException, InvocationTargetException {
    for (Method each : ReflectionUtilRt.collectGetters(object.getClass())) {
      Class<?> type = each.getReturnType();
      if (shouldSkip(type)) continue;

      each.setAccessible(true);
      Object value = each.invoke(object);

      if (value != null) {
        String key = each.getName().substring(3);
        String name = prefix + key.substring(0, 1).toLowerCase() + key.substring(1);

        if (value instanceof String || value.getClass().isPrimitive()) {
          String sValue = String.valueOf(value);
          if (!isNativeToString(sValue, value)) {
            result.put(name, sValue);
          }
        }
        else {
          Package pack = type.getPackage();
          if (pack != null && pack.getName().startsWith("org.apache.maven")) {
            doConvert(value, name + ".", result);
          }
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

  public static @NotNull Model toNativeModel(MavenModel model) {
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

  public static Repository toNativeRepository(MavenRemoteRepository r, boolean forceResolveSnapshots) {
    Repository result = new Repository();
    result.setId(r.getId());
    result.setName(r.getName());
    result.setUrl(r.getUrl());
    result.setLayout(r.getLayout() == null ? "default" : r.getLayout());

    if (r.getReleasesPolicy() != null) result.setReleases(toNativePolicy(r.getReleasesPolicy()));

    if (forceResolveSnapshots) {
      RepositoryPolicy policy = new RepositoryPolicy();
      policy.setEnabled(true);
      policy.setUpdatePolicy("allways");
      result.setSnapshots(policy);
    }
    else {
      if (r.getSnapshotsPolicy() != null) result.setSnapshots(toNativePolicy(r.getSnapshotsPolicy()));
    }

    return result;
  }

  private static RepositoryPolicy toNativePolicy(MavenRemoteRepository.Policy policy) {
    RepositoryPolicy result = new RepositoryPolicy();
    result.setEnabled(policy.isEnabled());
    result.setUpdatePolicy(policy.getUpdatePolicy());
    result.setChecksumPolicy(policy.getChecksumPolicy());
    return result;
  }

  public static List<MavenArtifact> convertArtifacts(Collection<? extends Artifact> artifacts,
                                                     Map<Artifact, MavenArtifact> nativeToConvertedMap,
                                                     File localRepository) {
    if (artifacts == null) return new ArrayList<>();

    Set<MavenArtifact> result = new LinkedHashSet<>(artifacts.size());
    for (Artifact each : artifacts) {
      result.add(convertArtifact(each, nativeToConvertedMap, localRepository));
    }
    return new ArrayList<>(result);
  }

  public static MavenArtifact convertArtifact(Artifact artifact, Map<Artifact, MavenArtifact> nativeToConvertedMap, File localRepository) {
    MavenArtifact result = nativeToConvertedMap.get(artifact);
    if (result == null) {
      result = convertArtifact(artifact, localRepository);
      nativeToConvertedMap.put(artifact, result);
    }
    return result;
  }

  public static MavenArtifact convertArtifact(Artifact artifact, File localRepository) {
    return new MavenArtifact(artifact.getGroupId(),
                             artifact.getArtifactId(),
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
                             false /*artifact instanceof CustomMaven3Artifact && ((CustomMaven3Artifact)artifact).isStub()*/);
  }
}

