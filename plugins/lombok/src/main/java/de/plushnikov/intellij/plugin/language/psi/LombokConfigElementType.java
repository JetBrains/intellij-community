package de.plushnikov.intellij.plugin.language.psi;

import com.intellij.psi.tree.IElementType;
import de.plushnikov.intellij.plugin.language.LombokConfigLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LombokConfigElementType extends IElementType {
  public LombokConfigElementType(@NotNull @NonNls String debugName) {
    super(debugName, LombokConfigLanguage.INSTANCE);
  }
}
