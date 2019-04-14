package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class LombokRuntimeVersion {
  public String findCurrentLombokVersion() {
    //"1.18.4" -> @FieldNameConstants redesigned
    //"1.18.2" -> @SuperBuilder added
    //"1.18.0" -> @Flogger added
    //"1.16.22" -> lombok.experimental.Builder and lombok.experimental.Value were removed
    //"1.16.16" -> @Builder.Default added
    //"1.16.12" -> @var added
    //"1.16.10" -> JBoss logger added
    //"1.16.6" -> @Helper added

    return "";
  }

  public Collection<String> getLombokJarsInProject(@NotNull Project project) {
    List<VirtualFile> pathsFiles = ProjectRootManager.getInstance(project).orderEntries().withoutSdk().librariesOnly().getPathsList().getVirtualFiles();
    return pathsFiles.stream()
      .filter(file -> Objects.equals(file.getExtension(), "jar"))
      .filter(file -> file.getNameWithoutExtension().contains("lombok-"))
      .map(VirtualFile::getNameWithoutExtension)
      .collect(Collectors.toSet());
  }
}
