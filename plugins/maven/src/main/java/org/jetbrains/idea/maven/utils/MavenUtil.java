package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.idea.maven.dom.model.MavenModel;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MavenUtil {
  public static void invokeLater(final Project p, final Runnable r) {
    invokeLater(p, ModalityState.defaultModalityState(), r);
  }

  public static void invokeLater(final Project p, final ModalityState state, final Runnable r) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      r.run();
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (p.isDisposed()) return;
        r.run();
      }
    }, state);
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
           "    <artifactId>" +
           projectId.artifactId +
           "</artifactId>\n" +
           (inheritVersion ? "" : "    <version>" + projectId.version + "</version>\n") +
           "</project>";
  }

  public static MavenModel getMavenModel(Project p, VirtualFile f) {
    PsiFile psiFile = PsiManager.getInstance(p).findFile(f);
    DomFileElement<MavenModel> root = DomManager.getDomManager(p).getFileElement((XmlFile)psiFile, MavenModel.class);
    return root.getRootElement();
  }

  public static <T extends Collection<Pattern>> T collectPattern(String text, T result) {
    String antPattern = FileUtil.convertAntToRegexp(text.trim());
    try {
      result.add(Pattern.compile(antPattern));
    }
    catch (PatternSyntaxException ignore) {
    }
    return result;
  }

  public static boolean isIncluded(String relativeName, List<Pattern> includes, List<Pattern> excludes) {
    boolean result = false;
    for (Pattern each : includes) {
      if (each.matcher(relativeName).matches()) {
        result = true;
        break;
      }
    }
    if (!result) return false;
    for (Pattern each : excludes) {
      if (each.matcher(relativeName).matches()) return false;
    }
    return true;
  }
}
