package com.intellij.openapi.project.impl;

import com.intellij.ide.impl.convert.ProjectConversionHelper;
import com.intellij.ide.impl.convert.ProjectConversionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author mike
 */
public class IdeaProjectManagerImpl extends ProjectManagerImpl {
  public IdeaProjectManagerImpl(VirtualFileManagerEx virtualFileManagerEx) {
    super(virtualFileManagerEx);
  }

  @Nullable
  protected Pair<Class, Object> convertProject(final String filePath) throws ProcessCanceledException {
    final String fp = canonicalize(filePath);

    final File f = new File(fp);
    if (f.exists() && f.isFile() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      final ProjectConversionHelper conversionHelper;
      final ProjectConversionUtil.ProjectConversionResult result = ProjectConversionUtil.convertProject(fp);
      if (result.isOpeningCancelled()) {
        throw new ProcessCanceledException();
      }
      conversionHelper = result.getConversionHelper();
      return new Pair<Class, Object>(ProjectConversionHelper.class, conversionHelper);
    }
    return null;
  }
}
