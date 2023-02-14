package de.plushnikov.intellij.plugin.inspection.modifiers;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedundantModifiersInfo {

  private final RedundantModifiersInfoType redundantModifiersInfoType;
  private final String[] modifiers;
  @InspectionMessage private final String description;
  private final String dontRunOnModifier;

  public RedundantModifiersInfo(@NotNull RedundantModifiersInfoType redundantModifiersInfoType,
                                @PsiModifier.ModifierConstant @Nullable String dontRunOnModifier,
                                @InspectionMessage @NotNull String description,
                                @PsiModifier.ModifierConstant String @NotNull... modifiers) {
    this.redundantModifiersInfoType = redundantModifiersInfoType;
    this.description = description;
    this.dontRunOnModifier = dontRunOnModifier;
    this.modifiers = modifiers;
  }

  @PsiModifier.ModifierConstant
  public String[] getModifiers() {
    return modifiers;
  }

  @InspectionMessage
  public String getDescription() {
    return description;
  }

  @PsiModifier.ModifierConstant
  public String getDontRunOnModifier() {
    return dontRunOnModifier;
  }

  public RedundantModifiersInfoType getType() {
    return redundantModifiersInfoType;
  }

  public boolean shouldCheck(PsiModifierListOwner psiModifierListOwner) {
    return true;
  }
}
