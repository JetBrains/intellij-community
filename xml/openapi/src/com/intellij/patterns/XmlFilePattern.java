package com.intellij.patterns;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlDocument;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class XmlFilePattern<Self extends XmlFilePattern<Self>> extends PsiFilePattern<XmlFile, Self>{

  public XmlFilePattern() {
    super(XmlFile.class);
  }

  protected XmlFilePattern(@NotNull final NullablePatternCondition condition) {
    super(condition);
  }

  public Self withRootTag(final ElementPattern rootTag) {
    return with(new PatternCondition<XmlFile>() {
      public boolean accepts(@NotNull final XmlFile xmlFile,
                                final MatchingContext matchingContext,
                                @NotNull final TraverseContext traverseContext) {
        XmlDocument document = xmlFile.getDocument();
        return document != null && rootTag.getCondition().accepts(document.getRootTag(), matchingContext, traverseContext);
      }
    });
  }

  public static class Capture extends XmlFilePattern<Capture> {
  }
}
