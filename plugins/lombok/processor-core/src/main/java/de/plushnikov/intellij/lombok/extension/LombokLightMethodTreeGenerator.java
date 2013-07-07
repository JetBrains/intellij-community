package de.plushnikov.intellij.lombok.extension;

import org.jetbrains.annotations.Nullable;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeGenerator;
import com.intellij.util.CharTable;
import de.plushnikov.intellij.lombok.psi.LombokLightMethod;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;

/**
 * @author Plushnikov Michail
 */
public class LombokLightMethodTreeGenerator implements TreeGenerator {

  public LombokLightMethodTreeGenerator() {
  }

  @Nullable
  public TreeElement generateTreeFor(PsiElement original, CharTable table, PsiManager manager) {
    TreeElement result = null;
    if (original instanceof LombokLightMethod || original instanceof LombokLightMethodBuilder) {
      result = ChangeUtil.copyElement((TreeElement) SourceTreeToPsiMap.psiElementToTree(original), table);
    }
    return result;
  }
}
