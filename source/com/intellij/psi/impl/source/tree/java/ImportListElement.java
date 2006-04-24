package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.lang.ASTNode;

public class ImportListElement extends RepositoryTreeElement{
  public ImportListElement() {
    super(IMPORT_LIST);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before){
    ChameleonTransforming.transformChildren(this);
    if (before == null){
      if (first == last && (first.getElementType() == ElementType.IMPORT_STATEMENT || first.getElementType() == ElementType.IMPORT_STATIC_STATEMENT)){
        anchor = getDefaultAnchor((PsiImportList)SourceTreeToPsiMap.treeElementToPsi(this),
                                  (PsiImportStatementBase)SourceTreeToPsiMap.treeElementToPsi(first));
        before = Boolean.TRUE;
      }
    }
    return super.addInternal(first, last, anchor, before);
  }

  public static ASTNode getDefaultAnchor(PsiImportList list, PsiImportStatementBase statement) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(list.getProject());
    ImportHelper importHelper = new ImportHelper(settings);
    return importHelper.getDefaultAnchor(list, statement);
  }
}
