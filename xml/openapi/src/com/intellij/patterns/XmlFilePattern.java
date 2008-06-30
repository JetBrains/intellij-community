package com.intellij.patterns;

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class XmlFilePattern<Self extends XmlFilePattern<Self>> extends PsiFilePattern<XmlFile, Self>{

  public XmlFilePattern() {
    super(XmlFile.class);
  }

  protected XmlFilePattern(@NotNull final InitialPatternCondition<XmlFile> condition) {
    super(condition);
  }

  public Self withRootTag(final ElementPattern<XmlTag> rootTag) {
    return with(new PatternCondition<XmlFile>("withRootTag") {
      public boolean accepts(@NotNull final XmlFile xmlFile, final ProcessingContext context) {
        XmlDocument document = xmlFile.getDocument();
        return document != null && rootTag.getCondition().accepts(document.getRootTag(), context);
      }
    });
  }

  public static class Capture extends XmlFilePattern<Capture> {
  }
}
