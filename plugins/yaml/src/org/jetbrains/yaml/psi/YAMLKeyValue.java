package org.jetbrains.yaml.psi;

import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.stubs.YAMLKeyStub;

/**
 * @author oleg
 */
public interface YAMLKeyValue extends YAMLPsiElement, PsiNamedElement, PomTarget, StubBasedPsiElement<YAMLKeyStub> {
  @Contract(pure = true)
  @Nullable
  PsiElement getKey();

  @Contract(pure = true)
  @NotNull
  String getKeyText();

  @Contract(pure = true)
  @Nullable
  YAMLValue getValue();

  @Contract(pure = true)
  @NotNull
  String getValueText();

  @Contract(pure = true)
  @Nullable
  YAMLMapping getParentMapping();

  void setValue(@NotNull YAMLValue value);

  /**
   * This method return flattened key path (consist of ancestors until document).
   * </p>
   * YAML are frequently used in configure files. Access to child keys are preformed by dot separator.
   * <pre>{@code
   *  top:
   *    next:
   *      list:
   *        - needKey: value
   * }</pre>
   * Flattened {@code needKey} is {@code top.next.list[0].needKey}
   */
  @NotNull
  String getConfigFullPath();
}
