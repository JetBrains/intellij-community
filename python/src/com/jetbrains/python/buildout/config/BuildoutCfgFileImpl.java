package com.jetbrains.python.buildout.config;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLPsiElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author traff
 */
public class BuildoutCfgFileImpl extends PsiFileBase {
  public BuildoutCfgFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, BuildoutCfgLanguage.INSTANCE);
  }

  @NotNull
  public FileType getFileType() {
    return BuildoutCfgFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "buildout.cfg file";
  }
}
