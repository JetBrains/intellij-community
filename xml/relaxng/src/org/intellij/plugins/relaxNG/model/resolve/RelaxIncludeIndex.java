package org.intellij.plugins.relaxNG.model.resolve;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.include.FileIncludeManager;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomManager;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.xml.dom.RngGrammar;
import org.jetbrains.annotations.NotNull;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 09.06.2010
*/
public class RelaxIncludeIndex {
  public static boolean processForwardDependencies(XmlFile file, final PsiElementProcessor<XmlFile> processor) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return processor.execute(file);
    }
    final Project project = file.getProject();
    final VirtualFile[] files = FileIncludeManager.getManager(project).getIncludedFiles(virtualFile, true);

    return processRelatedFiles(file, files, processor);
  }

  public static boolean processBackwardDependencies(@NotNull XmlFile file, PsiElementProcessor<XmlFile> processor) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return processor.execute(file);
    }
    final Project project = file.getProject();
    final VirtualFile[] files = FileIncludeManager.getManager(project).getIncludingFiles(virtualFile, true);

    return processRelatedFiles(file, files, processor);
  }

  private static boolean processRelatedFiles(PsiFile file, VirtualFile[] files, PsiElementProcessor<XmlFile> processor) {
    Project project = file.getProject();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiFile[] psiFiles = ContainerUtil.map2Array(files, PsiFile.class, new NullableFunction<VirtualFile, PsiFile>() {
      public PsiFile fun(VirtualFile file) {
        return psiManager.findFile(file);
      }
    });

    for (final PsiFile psiFile : psiFiles) {
      if (!processFile(psiFile, processor)) {
        return false;
      }
    }
    return true;
  }

  private static boolean processFile(PsiFile psiFile, PsiElementProcessor<XmlFile> processor) {
    final FileType type = psiFile.getFileType();
    if (type == XmlFileType.INSTANCE && isRngFile(psiFile)) {
      if (!processor.execute((XmlFile)psiFile)) {
        return false;
      }
    } else if (type == RncFileType.getInstance()) {
      if (!processor.execute((XmlFile)psiFile)) {
        return false;
      }
    }
    return true;
  }

  static boolean isRngFile(PsiFile psiFile) {
     return psiFile instanceof XmlFile && DomManager.getDomManager(psiFile.getProject()).getFileElement((XmlFile)psiFile, RngGrammar.class) != null;
  }
}
