/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing;

import com.intellij.facet.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.ArrayList;
import java.util.List;

public abstract class FacetImporterTestCase<FACET_TYPE extends Facet> extends MavenImportingTestCase {
  protected abstract FacetTypeId<FACET_TYPE> getFacetTypeId();

  protected void doAssertSourceRoots(List<String> actualRoots, String... roots) {
    List<String> expectedRootUrls = new ArrayList<>();

    for (String r : roots) {
      String url = VfsUtilCore.pathToUrl(getProjectPath() + "/" + r);
      expectedRootUrls.add(url);
    }

    assertUnorderedPathsAreEqual(actualRoots, expectedRootUrls);
  }

  protected FACET_TYPE getFacet(String module) {
    return getFacet(module, getFacetType());
  }

  protected FACET_TYPE findFacet(String module) {
    return findFacet(module, getFacetType());
  }

  protected <T extends Facet> T findFacet(String module, FacetType<T, ?> type) {
    return findFacet(module, type, getDefaultFacetName());
  }

  protected <T extends Facet> T findFacet(String module, FacetType<T, ?> type, String facetName) {
    FacetManager manager = FacetManager.getInstance(getModule(module));
    return manager.findFacet(type.getId(), facetName);
  }

  @NotNull
  protected <T extends Facet> T getFacet(String module, FacetType<T, ?> type) {
    T result = findFacet(module, type);
    assertNotNull("facet '" + type + "' not found", result);
    return result;
  }

  protected <T extends Facet> T getFacet(String module, FacetType<T, ?> type, String facetName) {
    T result = findFacet(module, type, facetName);
    assertNotNull("facet '" + type + ":" + facetName + "' not found", result);
    return result;
  }

  private FacetType<FACET_TYPE, ?> getFacetType() {
    return FacetTypeRegistry.getInstance().findFacetType(getFacetTypeId());
  }

  protected String getDefaultFacetName() {
    return getFacetType().getDefaultFacetName();
  }
}
