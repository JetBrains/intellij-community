package org.jetbrains.plugins.settingsRepository;

import com.intellij.openapi.util.io.FileUtil;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.TestName;
import org.junit.runner.Description;

import java.io.File;
import java.io.IOException;

class GitTestWatcher extends TestName {
  private Repository repository;

  @NotNull
  public Repository getRepository(@NotNull File baseDir) throws IOException {
    if (repository == null) {
      repository = new FileRepositoryBuilder().setWorkTree(new File(baseDir, getMethodName())).build();
      repository.create();
    }
    return repository;
  }

  @Override
  protected void finished(Description description) {
    super.finished(description);

    if (repository != null) {
      FileUtil.delete(repository.getWorkTree());
    }
  }
}