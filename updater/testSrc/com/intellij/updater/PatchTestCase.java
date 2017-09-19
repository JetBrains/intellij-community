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
package com.intellij.updater;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class PatchTestCase extends UpdaterTestCase {
  protected File myNewerDir;
  protected File myOlderDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myOlderDir = getTempFile("oldDir");
    myNewerDir = getTempFile("newDir");
    FileUtil.copyDir(dataDir, myOlderDir);
    FileUtil.copyDir(dataDir, myNewerDir);

    FileUtil.delete(new File(myNewerDir, "bin/idea.bat"));
    FileUtil.writeToFile(new File(myNewerDir, "Readme.txt"), "hello");
    FileUtil.writeToFile(new File(myNewerDir, "newDir/newFile.txt"), "hello");

    FileUtil.delete(new File(myOlderDir, "lib/annotations_changed.jar"));
    FileUtil.delete(new File(myNewerDir, "lib/annotations.jar"));
    FileUtil.rename(new File(myNewerDir, "lib/annotations_changed.jar"),
                    new File(myNewerDir, "lib/annotations.jar"));

    FileUtil.delete(new File(myOlderDir, "lib/bootstrap_deleted.jar"));
    FileUtil.delete(new File(myNewerDir, "lib/bootstrap.jar"));
    FileUtil.rename(new File(myNewerDir, "lib/bootstrap_deleted.jar"),
                    new File(myNewerDir, "lib/bootstrap.jar"));

    FileUtil.delete(new File(myOlderDir, "lib/boot2_changed_with_unchanged_content.jar"));
    FileUtil.delete(new File(myNewerDir, "lib/boot2.jar"));
    FileUtil.rename(new File(myNewerDir, "lib/boot2_changed_with_unchanged_content.jar"),
                    new File(myNewerDir, "lib/boot2.jar"));
  }

  protected Patch createPatch() throws IOException, OperationCancelledException {
    return createPatch(Function.identity());
  }

  protected Patch createPatch(Function<PatchSpec, PatchSpec> tuner) throws IOException, OperationCancelledException {
    PatchSpec spec = tuner.apply(new PatchSpec()
      .setOldFolder(myOlderDir.getAbsolutePath())
      .setNewFolder(myNewerDir.getAbsolutePath()));
    return new Patch(spec, TEST_UI);
  }

  protected void resetNewerDir() throws IOException {
    FileUtil.delete(myNewerDir);
    FileUtil.copyDir(myOlderDir, myNewerDir);
  }

  protected static List<PatchAction> sortActions(List<PatchAction> actions) {
    return sort(actions, a -> a.getClass().getSimpleName().charAt(0), Comparator.comparing(PatchAction::getPath));
  }

  protected static List<ValidationResult> sortResults(List<ValidationResult> results) {
    return sort(results, r -> r.action, Comparator.comparing(r -> r.path));
  }

  private static <T> List<T> sort(List<T> list, Function<T, ?> classifier, Comparator<T> sorter) {
    // splits the list into groups
    Collection<List<T>> groups = list.stream().collect(groupingBy(classifier, LinkedHashMap::new, toList())).values();
    // verifies the list is monotonic
    assertThat(list).isEqualTo(groups.stream().flatMap(Collection::stream).collect(toList()));
    // sorts group elements and concatenates groups into a list
    return groups.stream()
      .map(elements -> elements.stream().sorted(sorter))
      .flatMap(stream -> stream)
      .collect(toList());
  }
}