package org.jetbrains.idea.maven.indices;

import org.jetbrains.idea.maven.MavenImportingTestCase;

public abstract class MavenIndicesTestCase extends MavenImportingTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    // do not use mirrors by default
    updateSettingsXmlFully("<settings>" +
                           "  <mirrors>" +
                           "  </mirrors>" +
                           "</settings>");
  }
}
