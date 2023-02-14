// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.search;

import com.intellij.ide.actions.searcheverywhere.ContributorSearchResult;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.navigation.YAMLKeyNavigationItem;
import org.jetbrains.yaml.navigation.YAMLKeysSearchEverywhereContributor;
import org.jetbrains.yaml.psi.YAMLPsiElement;

import java.io.IOException;
import java.util.List;

public class YAMLSearchEverywhereTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/search/data/";
  }

  public void testSearch() throws IOException {
    myFixture.configureByFile("test.yml");
    List<String> requests = FileUtil.loadLines(getTestDataPath() + "requests.txt");
    YAMLKeysSearchEverywhereContributor contributor = new YAMLKeysSearchEverywhereContributor(myFixture.getProject());

    String searchResults = collectSearchResults(requests, contributor);
    assertSameLinesWithFile(getTestDataPath() + "result.txt", searchResults);
  }

  public void testDumbMode() throws IOException {
    myFixture.configureByFile("test.yml");
    List<String> requests = FileUtil.loadLines(getTestDataPath() + "requests.txt");
    YAMLKeysSearchEverywhereContributor contributor = new YAMLKeysSearchEverywhereContributor(myFixture.getProject());

    DumbService service = DumbService.getInstance(myFixture.getProject());
    assert service instanceof DumbServiceImpl;
    DumbServiceImpl impl = (DumbServiceImpl)service;
    impl.setDumb(true);
    try {
      String searchResults = collectSearchResults(requests, contributor);

      StringBuilder builder = new StringBuilder();
      for (String request : requests) {
        addRequestToResult(builder, request);
      }
      assertSameLines(builder.toString(), searchResults);
    } finally {
      // dumb state lives between tests
      impl.setDumb(false);
    }
  }

  @NotNull
  private String collectSearchResults(@NotNull List<String> requests, @NotNull YAMLKeysSearchEverywhereContributor contributor) {
    StringBuilder builder = new StringBuilder();
    for (String request : requests) {
      addRequestToResult(builder, request);
      ContributorSearchResult<YAMLKeyNavigationItem> result = contributor.search(request, new MockProgressIndicator(), 15);
      ContributorSearchResult<YAMLKeyNavigationItem> result2 = contributor.search(request, new MockProgressIndicator(), 15);
      assertEquals(result2.getItems(), result.getItems());
      for (YAMLKeyNavigationItem item : result.getItems()) {
        item.navigate(true);
        PsiElement element = myFixture.getElementAtCaret();
        assert(element instanceof YAMLPsiElement);

        String fullName = YAMLUtil.getConfigFullName((YAMLPsiElement)element);
        builder.append(fullName);
        builder.append('\n');
      }
    }
    return builder.toString();
  }

  private static void addRequestToResult(@NotNull StringBuilder builder, @NotNull String request) {
    builder.append("--- ");
    builder.append(request);
    builder.append(" ---\n");
  }
}
