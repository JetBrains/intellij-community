package org.jetbrains.idea.maven.plugins.groovy;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomProjectModelDescription;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.util.dynamicMembers.DynamicMemberUtils;

/**
 * @author Sergey Evdokimov
 */
public class MavenGroovyPomScriptMemberContributor extends NonCodeMembersContributor {

  private static final String CLASS_SOURCE = "class PomElements {\n" +
                                             "  org.apache.maven.project.MavenProject project;\n" +
                                             "  org.apache.maven.project.MavenProject pom;\n" +
                                             "  org.apache.maven.execution.MavenSession session;\n" +
                                             "  org.apache.maven.settings.Settings settings;\n" +
                                             "  org.slf4j.Logger log;\n" +
                                             "  groovy.util.AntBuilder ant;\n" +
                                             "  public void fail() {}" +
                                             "}";

  @Nullable
  @Override
  protected String getParentClassName() {
    return "pom";
  }

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     ResolveState state) {
    PsiElement pomElement = aClass.getContainingFile().getContext();
    if (pomElement == null) return;

    PsiFile pomFile = pomElement.getContainingFile();
    if (!(pomFile instanceof XmlFile)) return;

    DomManager domManager = DomManager.getDomManager(pomElement.getProject());
    if (!(domManager.getDomFileDescription((XmlFile)pomFile) instanceof MavenDomProjectModelDescription)) {
      return;
    }

    DynamicMemberUtils.process(processor, false, place, CLASS_SOURCE);
  }
}
