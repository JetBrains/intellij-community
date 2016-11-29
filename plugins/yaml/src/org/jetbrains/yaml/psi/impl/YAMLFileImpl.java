package org.jetbrains.yaml.psi.impl;

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
 * @author oleg
 */
public class YAMLFileImpl extends PsiFileBase implements YAMLFile {
  public YAMLFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, YAMLLanguage.INSTANCE);
  }

  @NotNull
  public FileType getFileType() {
    return YAMLFileType.YML;
  }

  @Override
  public String toString() {
    return "YAML file";
  }

  public List<YAMLDocument> getDocuments() {
    final ArrayList<YAMLDocument> result = new ArrayList<>();
    for (ASTNode node : getNode().getChildren(TokenSet.create(YAMLElementTypes.DOCUMENT))) {
     result.add((YAMLDocument) node.getPsi());
    }
    return result;
  }

  public List<YAMLPsiElement> getYAMLElements() {
    final ArrayList<YAMLPsiElement> result = new ArrayList<>();
    for (ASTNode node : getNode().getChildren(null)) {
      final PsiElement psi = node.getPsi();
      if (psi instanceof YAMLPsiElement){
        result.add((YAMLPsiElement) psi);
      }
    }
    return result;
  }
}
