// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.maven.testFramework.MavenTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenProjectProblem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class MavenProjectReaderTestCase extends MavenTestCase {
  protected MavenModel readProject(VirtualFile file, String... profiles) {
    MavenProjectReaderResult readResult = readProject(file, new NullProjectLocator(), profiles);
    assertProblems(readResult);
    return readResult.mavenModel;
  }

  protected MavenProjectReaderResult readProject(VirtualFile file,
                                                 MavenProjectReaderProjectLocator locator,
                                                 String... profiles) {
    MavenProjectReaderResult result = new MavenProjectReader(myProject).readProject(getMavenGeneralSettings(),
                                                                                    file,
                                                                                    new MavenExplicitProfiles(Arrays.asList(profiles)),
                                                                                    locator);
    return result;
  }

  protected static void assertProblems(MavenProjectReaderResult readerResult, String... expectedProblems) {
    List<String> actualProblems = new ArrayList<>();
    for (MavenProjectProblem each : readerResult.readingProblems) {
      actualProblems.add(each.getDescription());
    }
    assertOrderedElementsAreEqual(actualProblems, expectedProblems);
  }

  protected static class NullProjectLocator implements MavenProjectReaderProjectLocator {
    @Override
    public VirtualFile findProjectFile(MavenId coordinates) {
      return null;
    }
  }
}
