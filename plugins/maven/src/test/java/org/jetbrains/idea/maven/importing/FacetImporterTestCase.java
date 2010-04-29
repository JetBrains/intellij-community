package org.jetbrains.idea.maven.importing;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.ArrayList;
import java.util.List;

public abstract class FacetImporterTestCase<FACET_TYPE extends Facet, FACET_TYPE_TYPE extends FacetType<FACET_TYPE, ?>> extends MavenImportingTestCase {
  private FacetImporter<FACET_TYPE,?,FACET_TYPE_TYPE> myImporter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myImporter = createImporter();
  }

  protected abstract FacetImporter<FACET_TYPE, ?, FACET_TYPE_TYPE> createImporter();

  protected void doAssertSourceRoots(List<String> actualRoots, String... roots) {
    List<String> expectedRootUrls = new ArrayList<String>();

    for (String r : roots) {
      String url = VfsUtil.pathToUrl(getProjectPath() + "/" + r);
      expectedRootUrls.add(url);
    }

    assertUnorderedElementsAreEqual(actualRoots, ArrayUtil.toStringArray(expectedRootUrls));
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
    return myImporter.getFacetType();
  }

  private String getDefaultFacetName() {
    return myImporter.getDefaultFacetName();
  }
}
