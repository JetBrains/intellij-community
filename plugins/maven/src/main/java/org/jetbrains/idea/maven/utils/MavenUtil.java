package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.idea.maven.dom.model.MavenModel;

import java.io.File;

public class MavenUtil {
  public static void invokeLater(final Project p, final Runnable r) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      r.run();
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (p.isDisposed()) return;
        r.run();
      }
    });
  }

  public static File getPluginSystemDir(String folder) {
    // PathManager.getSystemPath() may return relative path
    return new File(PathManager.getSystemPath(), "Maven" + "/" + folder).getAbsoluteFile();
  }

  public static String makeFileContent(MavenId projectId) {
    return makeFileContent(projectId, false, false);
  }

  public static String makeFileContent(MavenId projectId, boolean inheritGroupId, boolean inheritVersion) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
           "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
           "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
           "    <modelVersion>4.0.0</modelVersion>\n" +
           (inheritGroupId ? "" : "    <groupId>" + projectId.groupId + "</groupId>\n") +
           "    <artifactId>" + projectId.artifactId + "</artifactId>\n" +
           (inheritVersion ? "" : "    <version>" + projectId.version + "</version>\n") +
           "</project>";
  }

  public static MavenModel getMavenModel(Project p, VirtualFile f) {
    PsiFile psiFile = PsiManager.getInstance(p).findFile(f);
    DomFileElement<MavenModel> root = DomManager.getDomManager(p).getFileElement((XmlFile)psiFile, MavenModel.class);
    return root.getRootElement();
  }
}
