package org.jetbrains.yaml.psi;

import com.intellij.psi.PsiFile;

import java.util.List;

/**
 * @author oleg
 */
public interface YAMLFile extends PsiFile, YAMLPsiElement {
  List<YAMLDocument> getDocuments();
}
