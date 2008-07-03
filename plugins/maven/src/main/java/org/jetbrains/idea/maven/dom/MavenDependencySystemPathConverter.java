package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class MavenDependencySystemPathConverter extends ResolvingConverter<PsiFile> {
  public PsiFile fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;

    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(s);
    if (f == null) return null;
    return PsiManager.getInstance(context.getXmlElement().getProject()).findFile(f);
  }

  public String toString(@Nullable PsiFile file, ConvertContext context) {
    if (file == null) return null;
    return file.getVirtualFile().getPath();
  }

  @NotNull
  public Collection<PsiFile> getVariants(ConvertContext context) {
    return Collections.emptyList();
  }
}
