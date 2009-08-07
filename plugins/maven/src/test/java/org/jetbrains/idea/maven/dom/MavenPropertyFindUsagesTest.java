package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MavenPropertyFindUsagesTest extends MavenDomTestCase {
  private VirtualFile myModule1Pom;
  private VirtualFile myModule2Pom;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    myModule1Pom = createModulePom("module1",
                                   "<groupId>test</groupId>" +
                                   "<artifactId>module1</artifactId>" +
                                   "<version>1</version>");

    myModule2Pom = createModulePom("module2",
                                   "<groupId>test</groupId>" +
                                   "<artifactId>module2</artifactId>" +
                                   "<version>1</version>");

    importProjects(myModule1Pom, myModule2Pom);
  }

  public void testFindModelPropertyFromReference() throws Exception {
    createModulePom("module1",
                    "<groupId>test</groupId>" +
                    "<artifactId>module1</artifactId>" +
                    "<version>1</version>" +

                    "<name>${<caret>project.version}</name>" +
                    "<description>${project.version}</description>");


    assertSearchResult(myModule1Pom,
                       findTag(myModule1Pom, "project.name"),
                       findTag(myModule1Pom, "project.description"));
  }

  public void testFindModelPropertyFromReferenceWithDifferentQualifiers() throws Exception {
    createModulePom("module1",
                    "<groupId>test</groupId>" +
                    "<artifactId>module1</artifactId>" +
                    "<version>1</version>" +

                    "<name>${<caret>version}</name>" +
                    "<description>${pom.version}</description>");

    assertSearchResult(myModule1Pom,
                       findTag(myModule1Pom, "project.name"),
                       findTag(myModule1Pom, "project.description"));
  }

  public void testFindUsagesFromTag() throws Exception {
    createModulePom("module1",
                    "<groupId>test</groupId>" +
                    "<artifactId>module1</artifactId>" +
                    "<<caret>version>1</version>" +

                    "<name>${project.version}</name>" +
                    "<description>${version}</description>");

    assertSearchResult(myModule1Pom,
                       findTag(myModule1Pom, "project.name"),
                       findTag(myModule1Pom, "project.description"));
  }

  public void testFindUsagesFromTagValue() throws Exception {
    createModulePom("module1",
                    "<groupId>test</groupId>" +
                    "<artifactId>module1</artifactId>" +
                    "<version>1<caret>1</version>" +

                    "<name>${project.version}</name>");

    assertSearchResult(myModule1Pom, findTag(myModule1Pom, "project.name"));
  }

  protected void assertSearchResult(VirtualFile file, PsiElement... expected) throws IOException {
    myCodeInsightFixture.configureFromExistingVirtualFile(file);
    PsiElement target = TargetElementUtilBase.findTargetElement(myCodeInsightFixture.getEditor(),
                                                                TargetElementUtilBase.ELEMENT_NAME_ACCEPTED |
                                                                TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    List<PsiReference> result = new ArrayList<PsiReference>(ReferencesSearch.search(target).findAll());
    List<PsiElement> actualElements = ContainerUtil.map(result, new Function<PsiReference, PsiElement>() {
      public PsiElement fun(PsiReference psiReference) {
        return psiReference.getElement();
      }
    });
    assertUnorderedElementsAreEqual(actualElements, expected);
  }
}
