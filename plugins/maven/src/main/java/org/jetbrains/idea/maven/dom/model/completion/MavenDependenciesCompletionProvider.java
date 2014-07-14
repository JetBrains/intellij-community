package org.jetbrains.idea.maven.dom.model.completion;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;

/**
 * @author Sergey Evdokimov
 */
public class MavenDependenciesCompletionProvider extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiElement xmlText = parameters.getPosition().getParent();

    if (!(xmlText instanceof XmlText)) return;

    PsiElement eDependencyTag = xmlText.getParent();
    if (!(eDependencyTag instanceof XmlTag)) return;

    XmlTag dependencyTag = (XmlTag)eDependencyTag;

    if (!"dependency".equals(dependencyTag.getName())) return;

    if (!PsiImplUtil.isLeafElementOfType(xmlText.getPrevSibling(), XmlTokenType.XML_TAG_END)
      || !PsiImplUtil.isLeafElementOfType(xmlText.getNextSibling(), XmlTokenType.XML_END_TAG_START)) {
      return;
    }

    Project project = dependencyTag.getProject();

    DomElement domElement = DomManager.getDomManager(project).getDomElement(dependencyTag);
    if (!(domElement instanceof MavenDomDependency)) {
      return;
    }

    MavenProjectIndicesManager indicesManager = MavenProjectIndicesManager.getInstance(project);

    for (String groupId : indicesManager.getGroupIds()) {
      for (String artifactId : indicesManager.getArtifactIds(groupId)) {
        LookupElement builder = LookupElementBuilder.create(groupId + ':' + artifactId)
          .withIcon(AllIcons.Nodes.PpLib).withInsertHandler(MavenDependencyInsertHandler.INSTANCE);

        result.addElement(builder);
      }
    }
  }

  private static class MavenDependencyInsertHandler implements InsertHandler<LookupElement> {

    public static final InsertHandler<LookupElement> INSTANCE = new MavenDependencyInsertHandler();

    @Override
    public void handleInsert(final InsertionContext context, LookupElement item) {
      String s = item.getLookupString();
      int idx = s.indexOf(':');

      String groupId = s.substring(0, idx);
      String artifactId = s.substring(idx + 1);

      int startOffset = context.getStartOffset();

      PsiFile psiFile = context.getFile();

      DomFileElement<MavenDomProjectModel> domModel = DomManager.getDomManager(context.getProject()).getFileElement((XmlFile)psiFile, MavenDomProjectModel.class);
      if (domModel == null) return;

      boolean shouldInvokeCompletion = false;

      MavenDomDependency managedDependency = MavenDependencyCompletionUtil.findManagedDependency(domModel.getRootElement(),
                                                                                                 context.getProject(), groupId, artifactId);
      if (managedDependency == null) {
        String value = "<groupId>" + groupId + "</groupId>\n" +
                       "<artifactId>" + artifactId + "</artifactId>\n" +
                       "<version></version>";

        context.getDocument().replaceString(startOffset, context.getSelectionEndOffset(), value);

        context.getEditor().getCaretModel().moveToOffset(startOffset + value.length() - 10);

        shouldInvokeCompletion = true;
      }
      else {
        StringBuilder sb = new StringBuilder();
        sb.append("<groupId>").append(groupId).append("</groupId>\n")
          .append("<artifactId>").append(artifactId).append("</artifactId>\n");

        String type = managedDependency.getType().getRawText();
        if (type != null && !type.equals("jar")) {
          sb.append("<type>").append(type).append("</type>\n");
        }

        String classifier = managedDependency.getClassifier().getRawText();
        if (StringUtil.isNotEmpty(classifier)) {
          sb.append("<classifier>").append(classifier).append("</classifier>\n");
        }

        context.getDocument().replaceString(startOffset, context.getSelectionEndOffset(), sb);
      }

      context.commitDocument();

      PsiElement e = psiFile.findElementAt(startOffset);
      while (e != null && (!(e instanceof XmlTag) || !"dependency".equals(((XmlTag)e).getName()))) {
        e = e.getParent();
      }

      if (e != null) {
        new ReformatCodeProcessor(psiFile.getProject(), psiFile, e.getTextRange(), false).run();
      }

      if (shouldInvokeCompletion) {
        MavenDependencyCompletionUtil.invokeCompletion(context, CompletionType.BASIC);
      }
    }
  }
}
