package org.intellij.lang.xpath.xslt.validation.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltNamedElement;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.validation.DeclarationChecker;
import org.jetbrains.annotations.NotNull;

public class XsltDeclarationInspection extends XsltInspection {
    private XsltElementFactory myXsltElementFactory;
    private NamesValidator myNamesValidator;

    @NotNull
    public String getDisplayName() {
        return "Declaration Problems";
    }

    @NotNull
    public String getShortName() {
        return "XsltDeclarations";
    }

    @NotNull
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }

    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
        if (!(holder.getFile() instanceof XmlFile)) return PsiElementVisitor.EMPTY_VISITOR;
        return new XmlElementVisitor() {
            @Override
            public void visitXmlTag(final XmlTag tag) {
                final XmlAttribute nameAttr = tag.getAttribute("name", null);
                if (nameAttr == null || PsiTreeUtil.hasErrorElements(nameAttr)) return;

              if (XsltSupport.isVariableOrParam(tag)) {
                final XsltNamedElement instance = getXsltElementFactory().wrapElement(tag, XsltNamedElement.class);
                checkDeclaration(instance, nameAttr.getValue(), holder);
              } else if (XsltSupport.isTemplate(tag)) {
                final XsltTemplate tmpl = getXsltElementFactory().wrapElement(tag, XsltTemplate.class);
                checkDeclaration(tmpl, nameAttr.getValue(), holder);
              }
            }

            private void checkDeclaration(final XsltNamedElement element, final String name, ProblemsHolder holder) {
                final XmlTag tag = element.getTag();

                final PsiElement token = element.getNameIdentifier();
                if (name == null || name.length() == 0) {
                    if (token != null) {
                        holder.registerProblem(token, "Empty name not permitted");
                    } else {
                        final XmlAttribute attribute = element.getNameAttribute();
                        if (attribute != null) {
                            final XmlAttributeValue e = attribute.getValueElement();
                            if (e != null) {
                                holder.registerProblem(e, "Empty name not permitted");
                            }
                        }
                    }
                } else if (!isLegalName(name, holder.getManager().getProject())) {
                    assert token != null;
                    holder.registerProblem(token, "Illegal name");
                } else {
                    assert token != null;
                    final XmlFile file = (XmlFile)tag.getContainingFile();
                    final XmlTag duplicatedSymbol = DeclarationChecker.getInstance(file).getDuplicatedSymbol(tag);
                    if (duplicatedSymbol != null) {
                      if (duplicatedSymbol.getContainingFile() == file) {
                        holder.registerProblem(token, "Duplicate declaration");
                      } else {
                        holder.registerProblem(token, "Duplicates declaration from '" + duplicatedSymbol.getContainingFile().getName() + "'");
                      }
                    }
                }
            }

            private boolean isLegalName(String value, Project project) {
                return getNamesValidator().isIdentifier(value, project);
            }
        };
    }

    public XsltElementFactory getXsltElementFactory() {
        if (myXsltElementFactory == null) {
            myXsltElementFactory = XsltElementFactory.getInstance();
        }
        return myXsltElementFactory;
    }

    public NamesValidator getNamesValidator() {
        if (myNamesValidator == null) {
            myNamesValidator = LanguageNamesValidation.INSTANCE.forLanguage(XPathFileType.XPATH.getLanguage());
        }
        return myNamesValidator;
    }
}