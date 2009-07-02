package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.converters.MavenPropertyResolvingConverter;

import java.util.Collection;
import java.util.Collections;

public class MavenDependencySystemPathConverter extends MavenPropertyResolvingConverter<PsiFile> {
  @Override
  public PsiFile fromResolvedString(@Nullable @NonNls String s, ConvertContext context) {
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
