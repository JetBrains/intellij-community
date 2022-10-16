package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigCleaner;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigFile;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LombokConfigUtil {

  private static final LombokConfigProperty[] EMPTY_LOMBOK_CONFIG_PROPERTIES = new LombokConfigProperty[0];
  private static final LombokConfigCleaner[] EMPTY_LOMBOK_CONFIG_CLEANERS = new LombokConfigCleaner[0];

  public static LombokConfigProperty @NotNull [] getLombokConfigProperties(@Nullable LombokConfigFile lombokConfigFile) {
    LombokConfigProperty[] result = PsiTreeUtil.getChildrenOfType(lombokConfigFile, LombokConfigProperty.class);
    return null == result ? EMPTY_LOMBOK_CONFIG_PROPERTIES : result;
  }

  public static LombokConfigCleaner @NotNull [] getLombokConfigCleaners(@Nullable LombokConfigFile lombokConfigFile) {
    LombokConfigCleaner[] result = PsiTreeUtil.getChildrenOfType(lombokConfigFile, LombokConfigCleaner.class);
    return null == result ? EMPTY_LOMBOK_CONFIG_CLEANERS : result;
  }
}
