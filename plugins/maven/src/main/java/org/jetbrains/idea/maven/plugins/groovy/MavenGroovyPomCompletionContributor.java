/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.plugins.groovy;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.NegatingComparable;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.icons.AllIcons;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.impl.GenericDomValueReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenVersionComparable;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.library.RepositoryUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 8/30/2016
 */
public class MavenGroovyPomCompletionContributor extends CompletionContributor {
  public static final Key<VirtualFile> ORIGINAL_POM_FILE = Key.create("ORIGINAL_POM_FILE");

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    if (!(position instanceof LeafElement)) return;

    Project project = position.getProject();
    VirtualFile virtualFile = parameters.getOriginalFile().getVirtualFile();
    if(virtualFile == null) return;

    MavenProject mavenProject = MavenProjectsManager.getInstance(project).findProject(virtualFile);
    if (mavenProject == null) return;

    List<String> methodCallInfo = MavenGroovyPomUtil.getGroovyMethodCalls(position);
    if (methodCallInfo.isEmpty()) return;

    StringBuilder buf = new StringBuilder();
    for (String s : methodCallInfo) {
      buf.append('<').append(s).append('>');
    }
    for (String s : ContainerUtil.reverse(methodCallInfo)) {
      buf.append('<').append(s).append("/>");
    }

    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("pom.xml", XMLLanguage.INSTANCE, buf);
    psiFile.putUserData(ORIGINAL_POM_FILE, virtualFile);
    List<Object> variants = ContainerUtil.newArrayList();


    String lastMethodCall = ContainerUtil.getLastItem(methodCallInfo);
    Ref<Boolean> completeDependency = Ref.create(false);
    Ref<Boolean> completeVersion = Ref.create(false);
    psiFile.accept(new PsiRecursiveElementVisitor(true) {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        if (!completeDependency.get() && element.getParent() instanceof XmlTag &&
            "dependency".equals(((XmlTag)element.getParent()).getName())) {
          if ("artifactId".equals(lastMethodCall) || "groupId".equals(lastMethodCall)) {
            completeDependency.set(true);
          }
          else if ("version".equals(lastMethodCall) || "dependency".equals(lastMethodCall)) {
            completeVersion.set(true);
            //completeDependency.set(true);
          }
        }

        if (!completeDependency.get() && !completeVersion.get()) {
          PsiReference[] references = getReferences(element);
          for (PsiReference each : references) {
            if (each instanceof GenericDomValueReference) {
              Collections.addAll(variants, each.getVariants());
            }
          }
        }
      }
    });
    for (Object variant : variants) {
      if (variant instanceof LookupElement) {
        result.addElement((LookupElement)variant);
      }
      else {
        result.addElement(LookupElementBuilder.create(variant));
      }
    }

    if (completeDependency.get()) {
      MavenProjectIndicesManager indicesManager = MavenProjectIndicesManager.getInstance(project);

      for (String groupId : indicesManager.getGroupIds()) {
        for (String artifactId : indicesManager.getArtifactIds(groupId)) {
          LookupElement builder = LookupElementBuilder.create(groupId + ':' + artifactId)
            .withIcon(AllIcons.Nodes.PpLib).withInsertHandler(MavenDependencyInsertHandler.INSTANCE);
          result.addElement(builder);
        }
      }
    }

    if (completeVersion.get()) {
      consumeDependencyElement(position, closableBlock -> {

        String groupId = null;
        String artifactId = null;
        for (GrMethodCall methodCall : PsiTreeUtil.findChildrenOfType(closableBlock, GrMethodCall.class)) {
          GroovyPsiElement[] arguments = methodCall.getArgumentList().getAllArguments();
          if (arguments.length != 1) continue;
          PsiReference reference = arguments[0].getReference();
          if (reference == null) continue;

          String callExpression = methodCall.getInvokedExpression().getText();
          String argumentValue = reference.getCanonicalText();
          if ("groupId".equals(callExpression)) {
            groupId = argumentValue;
          }
          else if ("artifactId".equals(callExpression)) {
            artifactId = argumentValue;
          }
        }
        completeVersions(result, project, groupId, artifactId, "");
      }, element -> {
        if (element.getParent() instanceof PsiLiteral) {
          Object value = ((PsiLiteral)element.getParent()).getValue();
          if (value == null) return;

          String[] mavenCoordinates = value.toString().split(":");
          if (mavenCoordinates.length < 3) return;

          String prefix = mavenCoordinates[0] + ':' + mavenCoordinates[1] + ':';
          completeVersions(result, project, mavenCoordinates[0], mavenCoordinates[1], prefix);
        }
      });
    }
  }

  private static void completeVersions(@NotNull CompletionResultSet completionResultSet,
                                       @NotNull Project project,
                                       @Nullable String groupId,
                                       @Nullable String artifactId,
                                       @NotNull String prefix) {
    if (StringUtil.isEmptyOrSpaces(artifactId)) return;
    CompletionResultSet newResultSet = completionResultSet.withRelevanceSorter(CompletionService.getCompletionService().emptySorter().weigh(
      new LookupElementWeigher("mavenVersionWeigher") {
        @Nullable
        @Override
        public Comparable weigh(@NotNull LookupElement element) {
          return new NegatingComparable(new MavenVersionComparable(StringUtil.trimStart(element.getLookupString(), prefix)));
        }
      }));

    MavenProjectIndicesManager indicesManager = MavenProjectIndicesManager.getInstance(project);

    Set<String> versions;

    if (StringUtil.isEmptyOrSpaces(groupId)) {
      versions = Collections.emptySet();
      //if (!(coordinates instanceof MavenDomPlugin)) return;
      //
      //versions = indicesManager.getVersions(MavenArtifactUtil.DEFAULT_GROUPS[0], artifactId);
      //for (int i = 0; i < MavenArtifactUtil.DEFAULT_GROUPS.length; i++) {
      //  versions = Sets.union(versions, indicesManager.getVersions(MavenArtifactUtil.DEFAULT_GROUPS[i], artifactId));
      //}
    }
    else {
      versions = indicesManager.getVersions(groupId, artifactId);
    }

    for (String version : versions) {
      newResultSet.addElement(LookupElementBuilder.create(prefix + version));
    }
    newResultSet.addElement(LookupElementBuilder.create(prefix + RepositoryUtils.ReleaseVersionId));
    newResultSet.addElement(LookupElementBuilder.create(prefix + RepositoryUtils.LatestVersionId));
  }

  @NotNull
  private static PsiReference[] getReferences(PsiElement psiElement) {
    return psiElement instanceof XmlText ? psiElement.getParent().getReferences() : psiElement.getReferences();
  }


  private static void consumeDependencyElement(PsiElement psiElement,
                                               Consumer<GrClosableBlock> closureNotationConsumer,
                                               Consumer<PsiElement> stringNotationConsumer) {
    final GrClosableBlock owner = PsiTreeUtil.getParentOfType(psiElement, GrClosableBlock.class);
    if (owner != null && owner.getParent() instanceof GrMethodCallExpression) {
      String invokedExpressionText = ((GrMethodCallExpression)owner.getParent()).getInvokedExpression().getText();
      if ("dependency".equals(invokedExpressionText)) {
        closureNotationConsumer.consume(owner);
      }
      if ("dependencies".equals(invokedExpressionText)) {
        GrMethodCall methodCall = PsiTreeUtil.getParentOfType(psiElement, GrMethodCall.class);
        if (methodCall != null && "dependency".equals(methodCall.getInvokedExpression().getText())) {
          stringNotationConsumer.consume(psiElement);
        }
      }
    }
  }

  private static class MavenDependencyInsertHandler implements InsertHandler<LookupElement> {

    private static final InsertHandler<LookupElement> INSTANCE = new MavenDependencyInsertHandler();

    @Override
    public void handleInsert(final InsertionContext context, LookupElement item) {
      String s = item.getLookupString();
      int idx = s.indexOf(':');
      String groupId = s.substring(0, idx);
      String artifactId = s.substring(idx + 1);

      int startOffset = context.getStartOffset();
      PsiFile psiFile = context.getFile();
      PsiElement psiElement = psiFile.findElementAt(startOffset);

      consumeDependencyElement(psiElement, closableBlock -> {
        int textOffset = closableBlock.getTextOffset();
        String value = "{groupId '" + groupId + "'\n" +
                       "artifactId '" + artifactId + "'\n" +
                       "version ''}";
        context.getDocument().replaceString(textOffset, textOffset + closableBlock.getTextLength(), value);
        context.getEditor().getCaretModel().moveToOffset(textOffset + value.length() - 2);

        context.commitDocument();
        new ReformatCodeProcessor(psiFile.getProject(), psiFile, closableBlock.getTextRange(), false).run();

        MavenDependencyCompletionUtil.invokeCompletion(context, CompletionType.BASIC);
      }, element -> {
        int textOffset = element.getTextOffset();
        String value = '\'' + groupId + ":" + artifactId + ":'";
        context.getDocument().replaceString(textOffset, textOffset + element.getTextLength(), value);
        context.getEditor().getCaretModel().moveToOffset(textOffset + value.length() - 1);

        MavenDependencyCompletionUtil.invokeCompletion(context, CompletionType.BASIC);
      });
    }
  }
}

