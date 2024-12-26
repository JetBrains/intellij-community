// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import com.intellij.util.ReflectionUtilRt;
import org.apache.maven.api.Artifact;
import org.apache.maven.api.model.*;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jdom.Element;
import org.jdom.IllegalNameException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.MavenServerGlobals;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

public final class Maven40ApiModelConverter {
  public static @NotNull MavenModel convertModel(Model model) {
    Build build = model.getBuild();
    return convertModel(
      model,
      asSourcesList(build.getSourceDirectory()),
      asSourcesList(build.getTestSourceDirectory()));
  }

  public static @NotNull MavenModel convertModel(Model model,
                                                 List<String> sources,
                                                 List<String> testSources) {
    MavenModel result = new MavenModel();
    result.setMavenId(new MavenId(model.getGroupId(), model.getArtifactId(), model.getVersion()));

    Parent parent = model.getParent();
    if (parent != null) {
      result.setParent(new MavenParent(
        new MavenId(parent.getGroupId(), parent.getArtifactId(), parent.getVersion()), parent.getRelativePath()));
    }
    result.setPackaging(model.getPackaging());
    result.setName(model.getName());
    result.setProperties(model.getProperties());
    result.setPlugins(convertPlugins(model));

    result.setRemoteRepositories(convertRepositories(model.getRepositories()));
    result.setRemotePluginRepositories(convertRepositories(model.getPluginRepositories()));
    result.setProfiles(convertProfiles(model.getProfiles()));
    result.setModules(model.getModules());

    convertBuild(result.getBuild(), model.getBuild(), sources, testSources);
    return result;
  }

  public static List<MavenPlugin> convertPlugins(Model mavenModel) {
    List<MavenPlugin> result = new ArrayList<>();
    Build build = mavenModel.getBuild();

    if (build != null) {
      List<Plugin> plugins = build.getPlugins();
      if (plugins != null) {
        for (Plugin each : plugins) {
          result.add(convertPlugin(false, each));
        }
      }
    }

    return result;
  }

  private static MavenPlugin convertPlugin(boolean isDefault, Plugin plugin) {
    List<MavenPlugin.Execution> executions = new ArrayList<>(plugin.getExecutions().size());
    for (PluginExecution each : plugin.getExecutions()) {
      executions.add(convertExecution(each));
    }

    List<MavenId> deps = new ArrayList<>(plugin.getDependencies().size());
    for (Dependency each : plugin.getDependencies()) {
      deps.add(new MavenId(each.getGroupId(), each.getArtifactId(), each.getVersion()));
    }

    return new MavenPlugin(plugin.getGroupId(),
                           plugin.getArtifactId(),
                           plugin.getVersion(),
                           isDefault,
                           "true".equals(plugin.getExtensions()),
                           convertConfiguration(plugin.getConfiguration()),
                           executions, deps);
  }

  public static MavenPlugin.Execution convertExecution(PluginExecution execution) {
    return new MavenPlugin.Execution(execution.getId(), execution.getPhase(), execution.getGoals(), convertConfiguration(execution.getConfiguration()));
  }

  private static Element convertConfiguration(Object config) {
    return config == null ? null : xppToElement((Xpp3Dom)config);
  }

  private static Element xppToElement(Xpp3Dom xpp) {
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

  public static void convertBuild(MavenBuild result, Build build, List<String> sources, List<String> testSources) {
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
    result.setFilters(build.getFilters() == null ? Collections.emptyList() : build.getFilters());
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

  private static MavenRemoteRepository.Policy convertPolicy(RepositoryPolicy policy) {
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
    return artifact.getExtension();
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

  public static MavenArtifact convertArtifactAndPath(Artifact artifact, Path artifactPath, File localRepository) {
    return new MavenArtifact(artifact.getGroupId(),
                             artifact.getArtifactId(),
                             artifact.getVersion().asString(),
                             artifact.getVersion().asString(),
                             "", //artifact.getType(),
                             artifact.getClassifier(),

                             "", //artifact.getScope(),
                             false, //artifact.isOptional(),

                             convertExtension(artifact),

                             null == artifactPath ? null : artifactPath.toFile(),
                             localRepository,

                             null != artifactPath,
                             false /*artifact instanceof CustomMaven3Artifact && ((CustomMaven3Artifact)artifact).isStub()*/);
  }
}
