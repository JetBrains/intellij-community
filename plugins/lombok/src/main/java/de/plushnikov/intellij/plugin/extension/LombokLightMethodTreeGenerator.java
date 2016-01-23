package de.plushnikov.intellij.plugin.extension;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeGenerator;
import com.intellij.util.CharTable;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public class LombokLightMethodTreeGenerator implements TreeGenerator {

  public LombokLightMethodTreeGenerator() {
  }

  @Nullable
  public TreeElement generateTreeFor(PsiElement original, CharTable table, PsiManager manager) {
    TreeElement result = null;
    if (original instanceof LombokLightMethodBuilder) {
      result = ChangeUtil.copyElement((TreeElement) SourceTreeToPsiMap.psiElementToTree(original), table);
    }
    return result;
  }
}
