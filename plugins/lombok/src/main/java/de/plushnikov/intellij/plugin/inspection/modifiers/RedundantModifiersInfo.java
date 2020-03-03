package de.plushnikov.intellij.plugin.inspection.modifiers;

import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedundantModifiersInfo {

  private final RedundantModifiersInfoType redundantModifiersInfoType;
  private final String[] modifiers;
  private final String description;
  private final String dontRunOnModifier;

  public RedundantModifiersInfo(@NotNull RedundantModifiersInfoType redundantModifiersInfoType,
                                @PsiModifier.ModifierConstant @Nullable String dontRunOnModifier,
                                @NotNull String description,
                                @PsiModifier.ModifierConstant @NotNull String... modifiers) {
    this.redundantModifiersInfoType = redundantModifiersInfoType;
    this.description = description;
    this.dontRunOnModifier = dontRunOnModifier;
    this.modifiers = modifiers;
  }

  @PsiModifier.ModifierConstant
  public String[] getModifiers() {
    return modifiers;
  }

  public String getDescription() {
    return description;
  }

  @PsiModifier.ModifierConstant
  public String getDontRunOnModifier() {
    return dontRunOnModifier;
  }

  public RedundantModifiersInfoType getRedundantModifiersInfoType() {
    return redundantModifiersInfoType;
  }
}
