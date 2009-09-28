package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture;

public abstract class MavenDomWithIndicesTestCase extends MavenDomTestCase {
  protected MavenIndicesTestFixture myIndicesFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIndicesFixture = createIndicesFixture();
    myIndicesFixture.setUp();
  }

  protected MavenIndicesTestFixture createIndicesFixture() {
    return new MavenIndicesTestFixture(myDir, myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    myIndicesFixture.tearDown();
    super.tearDown();
  }
}