package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenConstants;

import java.io.File;
import java.util.List;

public class MavenDomUtil {
  public static boolean isPomFile(PsiFile file) {
    if (!(file instanceof XmlFile)) return false;
    //if (!MavenProjectsManager.getInstance(file.getProject()).isMavenizedProject()) return false;

    String name = file.getName();
    return name.equals(MavenConstants.POM_XML)
           || name.endsWith("." + MavenConstants.POM_EXTENSION)
           || name.equals(MavenConstants.PROFILES_XML)
           || name.equals(MavenConstants.SETTINGS_XML);
  }

  public static String calcRelativePath(VirtualFile parent, VirtualFile child) {
    String result = FileUtil.getRelativePath(new File(parent.getPath()),
                                             new File(child.getPath()));
    return FileUtil.toSystemIndependentName(result);
  }

  public static MavenDomParent updateMavenParent(MavenDomProjectModel mavenModel, MavenProject parentProject) {
    MavenDomParent result = mavenModel.getMavenParent();

    VirtualFile pomFile = DomUtil.getFile(mavenModel).getVirtualFile();
    Project project = mavenModel.getXmlElement().getProject();

    MavenId parentId = parentProject.getMavenId();
    result.getGroupId().setStringValue(parentId.getGroupId());
    result.getArtifactId().setStringValue(parentId.getArtifactId());
    result.getVersion().setStringValue(parentId.getVersion());

    if (pomFile.getParent().getParent() != parentProject.getDirectoryFile()) {
      result.getRelativePath().setValue(PsiManager.getInstance(project).findFile(parentProject.getFile()));
    }

    return result;
  }

  public static <T> T getImmediateParent(ConvertContext context, Class<T> clazz) {
    DomElement parentElement = context.getInvocationElement().getParent();
    return clazz.isInstance(parentElement) ? (T)parentElement : null;
  }

  public static MavenDomProjectModel getMavenDomProjectModel(Project p, VirtualFile f) {
    DomFileElement<MavenDomProjectModel> fileElement = getMavenDomProjectFile(p, f);
    return fileElement == null ? null : fileElement.getRootElement();
  }

  public static DomFileElement<MavenDomProjectModel> getMavenDomProjectFile(Project p, VirtualFile f) {
    PsiFile psiFile = PsiManager.getInstance(p).findFile(f);
    return getMavenDomProjectFile(psiFile);
  }

  public static DomFileElement<MavenDomProjectModel> getMavenDomProjectFile(PsiFile f) {
    return DomManager.getDomManager(f.getProject()).getFileElement((XmlFile)f, MavenDomProjectModel.class);
  }

  public static MavenDomPluginModel getMavenPluginModel(Project p, VirtualFile pluginXml) {
    PsiFile psiFile = PsiManager.getInstance(p).findFile(pluginXml);
    DomFileElement<MavenDomPluginModel> fileElement = DomManager.getDomManager(p).getFileElement((XmlFile)psiFile, MavenDomPluginModel.class);
    return fileElement == null ? null : fileElement.getRootElement();
  }

  public static XmlTag findTag(DomFileElement<? extends DomElement> domFile, String path) {
    List<String> elements = StringUtil.split(path, ".");
    if (elements.isEmpty()) return null;

    XmlTag result = domFile.getRootTag();
    if (result == null || !elements.get(0).equals(result.getName())) return null;

    for (String each : elements.subList(1, elements.size())) {
      result = result.findFirstSubTag(each);
      if (result == null) return null;
    }
    return result;
  }

  public static List<DomFileElement<MavenDomProjectModel>> collectProjectPoms(final Project p) {
    return DomService.getInstance().getFileElements(MavenDomProjectModel.class,
                                                    p,
                                                    GlobalSearchScope.projectScope(p));
  }

  public static MavenId describe(final DomFileElement<MavenDomProjectModel> pom) {
    MavenDomProjectModel model = pom.getRootElement();

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
}
