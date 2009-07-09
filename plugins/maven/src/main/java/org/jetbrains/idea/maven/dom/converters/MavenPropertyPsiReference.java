package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import gnu.trove.THashSet;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.vfs.MavenPropertiesVirtualFileSystem;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MavenPropertyPsiReference extends MavenPsiReference {
  private final Project myProject;
  private final VirtualFile myVirtualFile;
  private final MavenProjectsManager myProjectsManager;

  public MavenPropertyPsiReference(PsiElement element, String text, int from) {
    super(element, text, TextRange.from(from, text.length()));

    myProject = myElement.getProject();
    myVirtualFile = PsiUtil.getVirtualFile(myElement);
    myProjectsManager = MavenProjectsManager.getInstance(myProject);
  }

  public PsiElement resolve() {
    PsiElement result = doResolve();
    if (result == null) return result;

    if (result instanceof XmlTag) {
      XmlTagChild[] children = ((XmlTag)result).getValue().getChildren();
      if (children.length != 1) return result;
      return new PsiElementWrapper(result, children[0]);
    }

    return result;
  }

  // precedence
  // 1. user/system
  // 2. settings.xml
  // 3. profiles.xml
  // 4. profiles in pom.xml
  // 5. pom.xml
  // 6. parent profiles.xml
  // 7. profiles in parent pom.xml
  // 8. parent pom.xml
  private PsiElement doResolve() {
    PsiElement result = resolveSystemPropety();
    if (result != null) return result;

    DomElement domElement = DomUtil.getDomElement(myElement);
    DomFileElement<DomElement> fileElement = DomUtil.getFileElement(domElement);
    MavenDomProjectModel domProject = (MavenDomProjectModel)fileElement.getRootElement(); // todo hard-cast for now

    if (myText.startsWith("project.") || myText.startsWith("pom.")) {
      String path = myText.startsWith("pom.")
                    ? "project." + myText.substring("pom.".length())
                    : myText;
      return resolveModelProperty(domProject, path, new THashSet<DomElement>());
    }

    if (myText.startsWith("settings.")) {
      for (VirtualFile each : myProjectsManager.getGeneralSettings().getEffectiveSettingsFiles()) {
        MavenDomSettingsModel settingsDom = MavenDomUtil.getMavenDomModel(myProject, each, MavenDomSettingsModel.class);
        if (settingsDom == null) continue;
        result = MavenDomUtil.findTag(settingsDom, myText);
        if (result != null) return result;
      }
    }

    return resolveProperty(domProject);
  }

  private PsiElement resolveSystemPropety() {
    if (true) return null;
    if (System.getProperty(myText) == null) return null;

    StringBuilder builder = new StringBuilder();
    for (Map.Entry<Object, Object> each : System.getProperties().entrySet()) {
      builder.append(each.getKey());
      builder.append("=");
      builder.append(each.getValue());
      builder.append("\n");
    }

    VirtualFileSystem fs = VirtualFileManager.getInstance().getFileSystem(MavenPropertiesVirtualFileSystem.PROTOCOL);
    final VirtualFile propertiesFile = fs.findFileByPath("System.properties");
    try {
      VfsUtil.saveText(propertiesFile, builder.toString());
    }
    catch (IOException e) {
      MavenLog.LOG.error(e);
      return null;
    }
    PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(myProject);
    //FileManager fileManager = psiManager.getFileManager();
    //fileManager.setViewProvider(propertiesFile, fileManager.createFileViewProvider(propertiesFile, false));
    return psiManager.findFile(propertiesFile);
  }

  private PsiElement resolveModelProperty(MavenDomProjectModel projectDom,
                                          final String path,
                                          final Set<DomElement> recursionGuard) {
    if (recursionGuard.contains(projectDom)) return null;
    recursionGuard.add(projectDom);

    XmlTag result = MavenDomUtil.findTag(projectDom, path);
    if (result != null) return result;

    if (path.equals("project.groupId") || path.equals("project.version")) {
      return MavenDomUtil.findTag(projectDom, path.replace("project.", "project.parent."));
    }

    return new MyMavenParentProjectFileProcessor<PsiElement>() {
      protected PsiElement doProcessParent(VirtualFile parentFile) {
        MavenDomProjectModel parentProjectDom = MavenDomUtil.getMavenDomProjectModel(myProject, parentFile);
        return resolveModelProperty(parentProjectDom, path, recursionGuard);
      }
    }.process(projectDom);
  }

  private PsiElement resolveProperty(MavenDomProjectModel projectDom) {
    PsiElement result;
    MavenProject mavenProject = MavenDomUtil.findProject(projectDom);

    result = resolveSettingsXmlProperty(mavenProject);
    if (result != null) return result;

    result = resolvePropertyForProject(myVirtualFile, projectDom, mavenProject);
    if (result != null) return result;

    return new MyMavenParentProjectFileProcessor<PsiElement>() {
      protected PsiElement doProcessParent(VirtualFile parentFile) {
        MavenDomProjectModel parentProjectDom = MavenDomUtil.getMavenDomProjectModel(myProject, parentFile);
        MavenProject parentMavenProject = MavenDomUtil.findProject(parentProjectDom);
        return resolvePropertyForProject(parentFile, parentProjectDom, parentMavenProject);
      }
    }.process(projectDom);
  }

  private PsiElement resolveSettingsXmlProperty(MavenProject mavenProject) {
    MavenGeneralSettings settings = myProjectsManager.getGeneralSettings();
    PsiElement result;

    for (VirtualFile each : settings.getEffectiveSettingsFiles()) {
      MavenDomSettingsModel settingsDom = MavenDomUtil.getMavenDomModel(myProject, each, MavenDomSettingsModel.class);
      if (settingsDom == null) continue;

      result = resolveProfilesProperty(settingsDom.getProfiles(), mavenProject);
      if (result != null) return result;
    }
    return null;
  }

  private PsiElement resolvePropertyForProject(VirtualFile file, MavenDomProjectModel projectDom, MavenProject mavenProject) {
    PsiElement result;

    result = resolveProfilesXmlProperty(file, mavenProject);
    if (result != null) return result;

    result = resolveProfilesProperty(projectDom.getProfiles(), mavenProject);
    if (result != null) return result;

    return resolveProperty(projectDom.getProperties());
  }

  private PsiElement resolveProfilesXmlProperty(VirtualFile projectFile, MavenProject mavenProject) {
    VirtualFile profilesFile = MavenUtil.findProfilesXmlFile(projectFile);
    if (profilesFile == null) return null;

    MavenDomProfiles profiles = MavenDomUtil.getMavenDomProfilesModel(myProject, profilesFile);
    if (profiles == null) return null;

    return resolveProfilesProperty(profiles, mavenProject);
  }

  private PsiElement resolveProfilesProperty(MavenDomProfiles profilesDom, MavenProject mavenProject) {
    List<String> activePropfiles = mavenProject == null ? null : mavenProject.getActiveProfilesIds();
    for (MavenDomProfile each : profilesDom.getProfiles()) {
      XmlTag idTag = each.getId().getXmlTag();
      if (idTag == null) continue;
      if (activePropfiles != null && !activePropfiles.contains(idTag.getValue().getText())) continue;

      PsiElement result = resolveProperty(each.getProperties());
      if (result != null) return result;
    }
    return null;
  }

  private PsiElement resolveProperty(MavenDomProperties propertiesDom) {
    XmlTag propertiesTag = propertiesDom.getXmlTag();
    if (propertiesTag != null) {
      for (XmlTag each : propertiesTag.getSubTags()) {
        if (each.getName().equals(myText)) return each;
      }
    }
    return null;
  }

  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }

  private abstract class MyMavenParentProjectFileProcessor<T> extends MavenParentProjectFileProcessor<T> {
    protected VirtualFile findManagedFile(MavenId id) {
      MavenProject project = myProjectsManager.findProject(id);
      return project == null ? null : project.getFile();
    }

    public T process(MavenDomProjectModel projectDom) {
      MavenDomParent parent = projectDom.getMavenParent();
      if (!DomUtil.hasXml(parent)) return null;

      String parentGroupId = parent.getGroupId().getStringValue();
      String parentArtifactId = parent.getArtifactId().getStringValue();
      String parentVersion = parent.getVersion().getStringValue();
      String parentRelativePath = parent.getRelativePath().getStringValue();
      if (StringUtil.isEmptyOrSpaces(parentRelativePath)) parentRelativePath = "../pom.xml";
      MavenId parentId = new MavenId(parentGroupId, parentArtifactId, parentVersion);

      return process(myVirtualFile, parentId, parentRelativePath, myProjectsManager.getLocalRepository());
    }
  }
}
