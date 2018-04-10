/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.plugins.groovy.MavenGroovyPomCompletionContributor;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenDomUtil {

  private static final Key<Pair<Long, Set<VirtualFile>>> FILTERED_RESOURCES_ROOTS_KEY = Key.create("MavenDomUtil.FILTERED_RESOURCES_ROOTS");

  // see http://maven.apache.org/settings.html
  private static final Set<String> SUBTAGS_IN_SETTINGS_FILE = ContainerUtil.newHashSet("localRepository", "interactiveMode",
                                                                                       "usePluginRegistry", "offline", "pluginGroups",
                                                                                       "servers", "mirrors", "proxies", "profiles",
                                                                                       "activeProfiles");

  public static boolean isMavenFile(PsiFile file) {
    return isProjectFile(file) || isProfilesFile(file) || isSettingsFile(file);
  }

  public static boolean isProjectFile(PsiFile file) {
    if (!(file instanceof XmlFile)) return false;

    XmlTag rootTag = ((XmlFile)file).getRootTag();
    if (rootTag == null || !"project".equals(rootTag.getName())) return false;

    String xmlns = rootTag.getAttributeValue("xmlns");
    if (xmlns != null && xmlns.startsWith("http://maven.apache.org/POM/")) {
      return true;
    }

    return MavenUtil.isPomFileName(file.getName());
  }

  public static boolean isProfilesFile(PsiFile file) {
    if (!(file instanceof XmlFile)) return false;

    return MavenConstants.PROFILES_XML.equals(file.getName());
  }

  public static boolean isSettingsFile(PsiFile file) {
    if (!(file instanceof XmlFile)) return false;

    XmlTag rootTag = ((XmlFile)file).getRootTag();
    if (rootTag == null || !"settings".equals(rootTag.getName())) return false;

    String xmlns = rootTag.getAttributeValue("xmlns");
    if (xmlns != null) {
      return xmlns.contains("maven");
    }

    boolean hasTag = false;

    for (PsiElement e = rootTag.getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof XmlTag) {
        if (SUBTAGS_IN_SETTINGS_FILE.contains(((XmlTag)e).getName())) return true;
        hasTag = true;
      }
    }

    return !hasTag;
  }

  public static boolean isMavenFile(PsiElement element) {
    return isMavenFile(element.getContainingFile());
  }

  @Nullable
  public static Module findContainingMavenizedModule(@NotNull PsiFile psiFile) {
    VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return null;

    Project project = psiFile.getProject();

    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    if (!manager.isMavenizedProject()) return null;

    ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();

    Module module = index.getModuleForFile(file);
    if (module == null || !manager.isMavenizedModule(module)) return null;
    return module;
  }

  public static boolean isMavenProperty(PsiElement target) {
    XmlTag tag = PsiTreeUtil.getParentOfType(target, XmlTag.class, false);
    if (tag == null) return false;
    return DomUtil.findDomElement(tag, MavenDomProperties.class) != null;
  }

  public static String calcRelativePath(VirtualFile parent, VirtualFile child) {
    String result = FileUtil.getRelativePath(parent.getPath(), child.getPath(), '/');
    if (result == null) {
      MavenLog.LOG.warn("cannot calculate relative path for\nparent: " + parent + "\nchild: " + child);
      result = child.getPath();
    }
    return FileUtil.toSystemIndependentName(result);
  }

  public static MavenDomParent updateMavenParent(MavenDomProjectModel mavenModel, MavenProject parentProject) {
    MavenDomParent result = mavenModel.getMavenParent();

    VirtualFile pomFile = DomUtil.getFile(mavenModel).getVirtualFile();
    Project project = mavenModel.getManager().getProject();

    MavenId parentId = parentProject.getMavenId();
    result.getGroupId().setStringValue(parentId.getGroupId());
    result.getArtifactId().setStringValue(parentId.getArtifactId());
    result.getVersion().setStringValue(parentId.getVersion());

    if (!Comparing.equal(pomFile.getParent().getParent(), parentProject.getDirectoryFile())
        || !FileUtil.namesEqual(MavenConstants.POM_XML, parentProject.getFile().getName())) {
      result.getRelativePath().setValue(PsiManager.getInstance(project).findFile(parentProject.getFile()));
    }

    return result;
  }

  public static <T> T getImmediateParent(ConvertContext context, Class<T> clazz) {
    DomElement parentElement = context.getInvocationElement().getParent();
    return clazz.isInstance(parentElement) ? (T)parentElement : null;
  }

  @Nullable
  public static VirtualFile getVirtualFile(@NotNull DomElement element) {
    PsiFile psiFile = DomUtil.getFile(element);
    return getVirtualFile(psiFile);
  }

  @Nullable
  public static VirtualFile getVirtualFile(@NotNull PsiElement element) {
    PsiFile psiFile = element.getContainingFile();
    return getVirtualFile(psiFile);
  }

  @Nullable
  private static VirtualFile getVirtualFile(PsiFile psiFile) {
    if (psiFile == null) return null;
    psiFile = psiFile.getOriginalFile();
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile instanceof LightVirtualFile) {
      virtualFile = ObjectUtils.chooseNotNull(psiFile.getUserData(MavenGroovyPomCompletionContributor.ORIGINAL_POM_FILE), virtualFile);
    }
    return virtualFile;
  }

  @Nullable
  public static MavenProject findProject(@NotNull MavenDomProjectModel projectDom) {
    XmlElement element = projectDom.getXmlElement();
    if (element == null) return null;

    VirtualFile file = getVirtualFile(element);
    if (file == null) return null;
    MavenProjectsManager manager = MavenProjectsManager.getInstance(element.getProject());
    return manager.findProject(file);
  }

  @Nullable
  public static MavenProject findContainingProject(@NotNull DomElement element) {
    PsiElement psi = element.getXmlElement();
    return psi == null ? null : findContainingProject(psi);
  }

  @Nullable
  public static MavenProject findContainingProject(@NotNull PsiElement element) {
    VirtualFile file = getVirtualFile(element);
    if (file == null) return null;
    MavenProjectsManager manager = MavenProjectsManager.getInstance(element.getProject());
    return manager.findContainingProject(file);
  }

  @Nullable
  public static MavenDomProjectModel getMavenDomProjectModel(@NotNull Project project, @NotNull VirtualFile file) {
    return getMavenDomModel(project, file, MavenDomProjectModel.class);
  }

  @Nullable
  public static MavenDomProfiles getMavenDomProfilesModel(@NotNull Project project, @NotNull VirtualFile file) {
    MavenDomProfilesModel model = getMavenDomModel(project, file, MavenDomProfilesModel.class);
    if (model != null) return model.getProfiles();
    return getMavenDomModel(project, file, MavenDomProfiles.class); // try old-style model
  }

  @Nullable
  public static <T extends MavenDomElement> T getMavenDomModel(@NotNull Project project,
                                                               @NotNull VirtualFile file,
                                                               @NotNull Class<T> clazz) {
    if (!file.isValid()) return null;
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) return null;
    return getMavenDomModel(psiFile, clazz);
  }

  @Nullable
  public static <T extends MavenDomElement> T getMavenDomModel(@NotNull PsiFile file, @NotNull Class<T> clazz) {
    DomFileElement<T> fileElement = getMavenDomFile(file, clazz);
    return fileElement == null ? null : fileElement.getRootElement();
  }

  @Nullable
  private static <T extends MavenDomElement> DomFileElement<T> getMavenDomFile(@NotNull PsiFile file, @NotNull Class<T> clazz) {
    if (!(file instanceof XmlFile)) return null;
    return DomManager.getDomManager(file.getProject()).getFileElement((XmlFile)file, clazz);
  }

  @Nullable
  public static XmlTag findTag(@NotNull DomElement domElement, @NotNull String path) {
    List<String> elements = StringUtil.split(path, ".");
    if (elements.isEmpty()) return null;

    Pair<String, Integer> nameAndIndex = translateTagName(elements.get(0));
    String name = nameAndIndex.first;
    Integer index = nameAndIndex.second;

    XmlTag result = domElement.getXmlTag();
    if (result == null || !name.equals(result.getName())) return null;
    result = getIndexedTag(result, index);

    for (String each : elements.subList(1, elements.size())) {
      nameAndIndex = translateTagName(each);
      name = nameAndIndex.first;
      index = nameAndIndex.second;

      result = result.findFirstSubTag(name);
      if (result == null) return null;
      result = getIndexedTag(result, index);
    }
    return result;
  }

  private static final Pattern XML_TAG_NAME_PATTERN = Pattern.compile("(\\S*)\\[(\\d*)\\]\\z");

  private static Pair<String, Integer> translateTagName(String text) {
    String tagName = text.trim();
    Integer index = null;

    Matcher matcher = XML_TAG_NAME_PATTERN.matcher(tagName);
    if (matcher.find()) {
      tagName = matcher.group(1);
      try {
        index = Integer.parseInt(matcher.group(2));
      }
      catch (NumberFormatException e) {
        return null;
      }
    }

    return Pair.create(tagName, index);
  }

  private static XmlTag getIndexedTag(XmlTag parent, Integer index) {
    if (index == null) return parent;

    XmlTag[] children = parent.getSubTags();
    if (index < 0 || index >= children.length) return null;
    return children[index];
  }

  @Nullable
  public static PropertiesFile getPropertiesFile(@NotNull Project project, @NotNull VirtualFile file) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof PropertiesFile)) return null;
    return (PropertiesFile)psiFile;
  }

  @Nullable
  public static IProperty findProperty(@NotNull Project project, @NotNull VirtualFile file, @NotNull String propName) {
    PropertiesFile propertiesFile = getPropertiesFile(project, file);
    return propertiesFile == null ? null : propertiesFile.findPropertyByKey(propName);
  }

  @Nullable
  public static PsiElement findPropertyValue(@NotNull Project project, @NotNull VirtualFile file, @NotNull String propName) {
    IProperty prop = findProperty(project, file, propName);
    return prop == null ? null : prop.getPsiElement().getFirstChild().getNextSibling().getNextSibling();
  }

  private static Set<VirtualFile> getFilteredResourcesRoots(@NotNull MavenProject mavenProject) {
    Pair<Long, Set<VirtualFile>> cachedValue = mavenProject.getCachedValue(FILTERED_RESOURCES_ROOTS_KEY);

    if (cachedValue == null || cachedValue.first != VirtualFileManager.getInstance().getModificationCount()) {
      Set<VirtualFile> set = null;

      for (MavenResource resource : ContainerUtil.concat(mavenProject.getResources(), mavenProject.getTestResources())) {
        if (!resource.isFiltered()) continue;

        VirtualFile resourceDir = LocalFileSystem.getInstance().findFileByPath(resource.getDirectory());
        if (resourceDir == null) continue;

        if (set == null) {
          set = new HashSet<>();
        }

        set.add(resourceDir);
      }

      if (set == null) {
        set = Collections.emptySet();
      }

      cachedValue = Pair.create(VirtualFileManager.getInstance().getModificationCount(), set);
      mavenProject.putCachedValue(FILTERED_RESOURCES_ROOTS_KEY, cachedValue);
    }

    return cachedValue.second;
  }

  public static boolean isFilteredResourceFile(PsiElement element) {
    PsiFile psiFile = element.getContainingFile();
    VirtualFile file = getVirtualFile(psiFile);
    if (file == null) return false;

    MavenProjectsManager manager = MavenProjectsManager.getInstance(psiFile.getProject());
    MavenProject mavenProject = manager.findContainingProject(file);
    if (mavenProject == null) return false;

    Set<VirtualFile> filteredRoots = getFilteredResourcesRoots(mavenProject);

    if (!filteredRoots.isEmpty()) {
      for (VirtualFile f = file.getParent(); f != null; f = f.getParent()) {
        if (filteredRoots.contains(f)) {
          return true;
        }
      }
    }

    return false;
  }

  public static List<DomFileElement<MavenDomProjectModel>> collectProjectModels(Project p) {
    return DomService.getInstance().getFileElements(MavenDomProjectModel.class, p, GlobalSearchScope.projectScope(p));
  }

  public static MavenId describe(PsiFile psiFile) {
    MavenDomProjectModel model = getMavenDomModel(psiFile, MavenDomProjectModel.class);

    String groupId = model.getGroupId().getStringValue();
    String artifactId = model.getArtifactId().getStringValue();
    String version = model.getVersion().getStringValue();

    if (groupId == null) {
      groupId = model.getMavenParent().getGroupId().getStringValue();
    }

    if (version == null) {
      version = model.getMavenParent().getVersion().getStringValue();
    }

    return new MavenId(groupId, artifactId, version);
  }

  @NotNull
  public static MavenDomDependency createDomDependency(MavenDomProjectModel model,
                                                       @Nullable Editor editor,
                                                       @NotNull final MavenId id) {
    return createDomDependency(model.getDependencies(), editor, id);
  }

  @NotNull
  public static MavenDomDependency createDomDependency(MavenDomDependencies dependencies,
                                                       @Nullable Editor editor,
                                                       @NotNull final MavenId id) {
    MavenDomDependency dep = createDomDependency(dependencies, editor);

    dep.getGroupId().setStringValue(id.getGroupId());
    dep.getArtifactId().setStringValue(id.getArtifactId());
    dep.getVersion().setStringValue(id.getVersion());

    return dep;
  }

  @NotNull
  public static MavenDomDependency createDomDependency(@NotNull MavenDomProjectModel model, @Nullable Editor editor) {
    return createDomDependency(model.getDependencies(), editor);
  }

  @NotNull
  public static MavenDomDependency createDomDependency(@NotNull MavenDomDependencies dependencies, @Nullable Editor editor) {
    int index = getCollectionIndex(dependencies, editor);
    if (index >= 0) {
      DomCollectionChildDescription childDescription = dependencies.getGenericInfo().getCollectionChildDescription("dependency");
      if (childDescription != null) {
        DomElement element = childDescription.addValue(dependencies, index);
        if (element instanceof MavenDomDependency) {
          return (MavenDomDependency)element;
        }
      }
    }
    return dependencies.addDependency();
  }


  public static int getCollectionIndex(@NotNull final MavenDomDependencies dependencies, @Nullable final Editor editor) {
    if (editor != null) {
      int offset = editor.getCaretModel().getOffset();

      List<MavenDomDependency> dependencyList = dependencies.getDependencies();

      for (int i = 0; i < dependencyList.size(); i++) {
        MavenDomDependency dependency = dependencyList.get(i);
        XmlElement xmlElement = dependency.getXmlElement();

        if (xmlElement != null && xmlElement.getTextRange().getStartOffset() >= offset) {
          return i;
        }
      }
    }
    return -1;
  }

  @NotNull
  public static String getProjectName(MavenDomProjectModel model) {
    MavenProject mavenProject = findProject(model);
    if (mavenProject != null) {
      return mavenProject.getDisplayName();
    }
    else {
      String name = model.getName().getStringValue();
      if (!StringUtil.isEmptyOrSpaces(name)) {
        return name;
      }
      else {
        return "pom.xml"; // ?
      }
    }
  }
}
