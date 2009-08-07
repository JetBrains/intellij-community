package org.jetbrains.idea.maven.dom.findUsages;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import com.intellij.util.xml.DomUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.MavenDomUtil;

public class MavenUsageTargetProvider implements UsageTargetProvider {
  public UsageTarget[] getTargetsAtContext(DataProvider context) {
    Editor editor = (Editor)context.getData(DataConstants.EDITOR);
    PsiFile file = (PsiFile)context.getData(DataConstants.PSI_FILE);

    if (editor == null || file == null) return null;
    if (!MavenDomUtil.isMavenFile(file)) return null;

    PsiElement target = TargetElementUtil.findTargetElement(editor, TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    if (target != null && !isSchema(target)) return new UsageTarget[]{new PsiElement2UsageTargetAdapter(target)};

    target = file.findElementAt(editor.getCaretModel().getOffset());
    if (target == null) return null;

    target = PsiTreeUtil.getParentOfType(target, XmlTag.class, false);
    if (target == null) return null;

    if (DomUtil.findDomElement(target, MavenDomElement.class) == null) return null;

    return new UsageTarget[]{new PsiElement2UsageTargetAdapter(target)};
  }

  private boolean isSchema(PsiElement element) {
    return element instanceof XmlTag && XmlUtil.XML_SCHEMA_URI.equals(((XmlTag)element).getNamespace());
  }
}
