package org.jetbrains.idea.maven.server;

import junit.framework.TestCase;

public class Maven3ServerEmbedderImplTest extends TestCase {
  public void testName() throws Exception {
    Maven3ServerEmbedderImpl embedder = new Maven3ServerEmbedderImpl(new MavenServerSettings());
    embedder.release();
  }
}
