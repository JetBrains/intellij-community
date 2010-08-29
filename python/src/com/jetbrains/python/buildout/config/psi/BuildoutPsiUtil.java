package com.jetbrains.python.buildout.config.psi;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.buildout.BuildoutFacet;
import com.jetbrains.python.buildout.config.BuildoutCfgLanguage;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgFile;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgOption;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * @author traff
 */
public class BuildoutPsiUtil {
  private static final String RECIPE = "recipe";
  private static final String DJANGO_RECIPE = "djangorecipe";

  @Nullable
  public static BuildoutCfgSection getDjangoSection(@NotNull BuildoutCfgFile configFile) {
    List<String> parts = configFile.getParts();
    for (String part : parts) {
      final BuildoutCfgSection section = configFile.findSectionByName(part);
      if (section != null && DJANGO_RECIPE.equals(section.getOptionValue(RECIPE))) {
        return section;
      }
    }
    return null;
  }

  @Nullable
  public static BuildoutCfgFile readBuildOutCfgFile(BuildoutFacet buildoutFacet) {
    File cfg = buildoutFacet.getConfigFile();
    if (cfg != null && cfg.exists()) {
      try {
        char[] scriptText = FileUtil.loadFileText(cfg);
        final PsiFile configFile = PsiFileFactory
          .getInstance(buildoutFacet.getModule().getProject()).createFileFromText("buildout.cfg",
                                                                                  BuildoutCfgLanguage.INSTANCE, new String(scriptText));
        if (configFile != null && configFile instanceof BuildoutCfgFile) {
          return (BuildoutCfgFile)configFile;
        }
      }
      catch (Exception e) {
      }
    }
    return null;
  }

  public static boolean isInBuildoutSection(@NotNull PsiElement element) {
    BuildoutCfgSection section = PsiTreeUtil.getParentOfType(element, BuildoutCfgSection.class);
    return section != null && "buildout".equals(section.getHeaderName());
  }

  public static boolean isAssignedTo(@NotNull PsiElement element, @NotNull String name) {
    BuildoutCfgOption option = PsiTreeUtil.getParentOfType(element, BuildoutCfgOption.class);
    return (option != null && name.equals(option.getKey()));
  }


}

