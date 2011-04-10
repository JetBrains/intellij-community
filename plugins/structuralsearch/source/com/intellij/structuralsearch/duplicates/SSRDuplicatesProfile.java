package com.intellij.structuralsearch.duplicates;

import com.intellij.dupLocator.DupInfo;
import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocateVisitor;
import com.intellij.dupLocator.DuplocatorSettings;
import com.intellij.dupLocator.resultUI.*;
import com.intellij.dupLocator.treeHash.DuplocatorHashCallback;
import com.intellij.dupLocator.util.DuplocatorSettingsEditor;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class SSRDuplicatesProfile extends DuplicatesProfile {
  @NotNull
  @Override
  public DuplocateVisitor createVisitor(@NotNull DuplocatorHashCallback collector) {
    return new SSRNodeSpecificHasher(DuplocatorSettings.getInstance(), collector);
  }

  @Override
  protected boolean isMyLanguage(@NotNull Language language) {
    return true;
  }

  @Override
  public CodeFragmentType getType(@NotNull CodeNode node) throws InvalidatedException {
    return new FileBasedCodeFragmentType(node.getVirtualFile());
  }

  @Override
  public DuplocatorSettingsEditor createEditor() {
    return null;
  }

  @NotNull
  @Override
  public DuplicatesView createView(@NotNull Project project) {
    return new BaseDuplicatesView(project);
  }

  @Override
  public boolean isMyDuplicate(@NotNull DupInfo info, int index) {
    return true;
  }
}
