package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.utils.MavenConstants;

import java.io.File;

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
    result.getGroupId().setStringValue(parentId.groupId);
    result.getArtifactId().setStringValue(parentId.artifactId);
    result.getVersion().setStringValue(parentId.version);

    if (pomFile.getParent().getParent() != parentProject.getDirectoryFile()) {
      result.getRelativePath().setValue(PsiManager.getInstance(project).findFile(parentProject.getFile()));
    }

    return result;
  }

  public static <T> T getImmediateParent(ConvertContext context, Class<T> clazz) {
    DomElement parentElement = context.getInvocationElement().getParent();
    return clazz.isInstance(parentElement) ? (T)parentElement : null;
  }
}
