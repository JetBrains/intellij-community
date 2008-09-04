package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.idea.maven.project.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;

public class MavenDomUtil {
  public static boolean isPomFile(PsiFile file) {
    if (!(file instanceof XmlFile)) return false;
    if (!MavenProjectsManager.getInstance(file.getProject()).isMavenizedProject()) return false;

    String name = file.getName();
    return name.equals(MavenConstants.POM_XML);
  }

  public static String calcRelativePath(VirtualFile parent, VirtualFile child) {
    String result = FileUtil.getRelativePath(new File(parent.getPath()),
                                             new File(child.getPath()));
    return FileUtil.toSystemIndependentName(result);
  }
}
