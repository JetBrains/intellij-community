package com.intellij.lang.properties.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2005
 * Time: 12:29:06 AM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesParser implements PsiParser {
  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    final PsiBuilder.Marker rootMarker = builder.mark();
    final PsiBuilder.Marker propertiesList = builder.mark();
    while (!builder.eof()) {
      Parsing.parseProperty(builder);
    }
    propertiesList.done(PropertiesElementTypes.PROPERTIES_LIST);
    rootMarker.done(root);
    return builder.getTreeBuilt();
  }
}
