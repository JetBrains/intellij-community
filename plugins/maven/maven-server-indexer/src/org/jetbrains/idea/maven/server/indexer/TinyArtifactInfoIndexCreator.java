// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.indexer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.IndexerField;
import org.apache.maven.index.IndexerFieldVersion;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Collection;

@Singleton
@Named(TinyArtifactInfoIndexCreator.ID)
public class TinyArtifactInfoIndexCreator extends MinimalArtifactInfoIndexCreator {

  public static final String ID = "idea-tiny";

  private static final FieldType STORED_NOT_INDEXED = new FieldType();

  static {
    STORED_NOT_INDEXED.setIndexOptions(IndexOptions.NONE);
    STORED_NOT_INDEXED.setStored(true);
    STORED_NOT_INDEXED.setTokenized(false);
    STORED_NOT_INDEXED.freeze();
  }

  private static final IndexerField FLD_PACKAGING_NOT_INDEXED =
    new IndexerField(MAVEN.PACKAGING, IndexerFieldVersion.V1, ArtifactInfo.PACKAGING, "Artifact Packaging (not indexed, stored)",
                     STORED_NOT_INDEXED);

  private static final IndexerField FLD_DESCRIPTION_NOT_INDEXED =
    new IndexerField(MAVEN.DESCRIPTION, IndexerFieldVersion.V1, ArtifactInfo.DESCRIPTION, "Artifact description (not indexed, stored)",
                     STORED_NOT_INDEXED);

  @Override
  public void updateDocument(ArtifactInfo ai, Document doc) {
    if (ai.getPackaging() != null) {
      doc.add(FLD_PACKAGING_NOT_INDEXED.toField(ai.getPackaging()));
    }

    if ("maven-archetype".equals(ai.getPackaging()) && ai.getDescription() != null) {
      doc.add(FLD_DESCRIPTION_NOT_INDEXED.toField(ai.getDescription()));
    }
  }

  @Override
  public Collection<IndexerField> getIndexerFields() {
    return Arrays.asList(FLD_PACKAGING_NOT_INDEXED, FLD_DESCRIPTION_NOT_INDEXED);
  }
}