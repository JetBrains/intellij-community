// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.ignore;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.properties.PropertyValue;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public final class SvnPropertyService {

  private SvnPropertyService() {
  }

  public static void doAddToIgnoreProperty(@NotNull SvnVcs vcs, boolean useCommonExtension, VirtualFile[] file, IgnoreInfoGetter getter)
    throws VcsException {
    final IgnorePropertyAdder adder = new IgnorePropertyAdder(vcs, useCommonExtension);
    adder.execute(file, getter);
  }

  public static void doRemoveFromIgnoreProperty(@NotNull SvnVcs vcs,
                                                boolean useCommonExtension,
                                                VirtualFile[] file,
                                                IgnoreInfoGetter getter) throws VcsException {
    final IgnorePropertyRemover remover = new IgnorePropertyRemover(vcs, useCommonExtension);
    remover.execute(file, getter);
  }

  public static void doCheckIgnoreProperty(@NotNull SvnVcs vcs,
                                           VirtualFile[] file,
                                           IgnoreInfoGetter getter,
                                           String extensionPattern,
                                           Ref<? super Boolean> filesOk,
                                           Ref<? super Boolean> extensionOk) {
    final IgnorePropertyChecker checker = new IgnorePropertyChecker(vcs, extensionPattern);
    try {
      checker.execute(file, getter);
    } catch (VcsException e) {
      // ignore - actually never thrown inside
    }
    filesOk.set(checker.filesOk());
    extensionOk.set(checker.extensionOk());
  }

  private static abstract class IgnorePropertyWorkTemplate {
    @NotNull protected final SvnVcs myVcs;
    protected final boolean myUseCommonExtension;
    protected final boolean myCanUseCachedProperty;

    protected abstract void processFolder(final VirtualFile folder, final File folderDir, final Set<String> data,
                                          final PropertyValue propertyValue) throws VcsException;

    protected abstract void onAfterProcessing(final VirtualFile[] file) throws VcsException;

    protected abstract void onSVNException(Exception e);

    protected abstract boolean stopIteration();

    private IgnorePropertyWorkTemplate(@NotNull SvnVcs vcs, boolean useCommonExtension, boolean canUseCachedProperty) {
      myVcs = vcs;
      myCanUseCachedProperty = canUseCachedProperty;
      myUseCommonExtension = useCommonExtension;
    }

    public void execute(final VirtualFile[] file, final IgnoreInfoGetter getter) throws VcsException {
      final Map<VirtualFile, Set<String>> foldersInfo = getter.getInfo(myUseCommonExtension);
      for (final Map.Entry<VirtualFile, Set<String>> entry : foldersInfo.entrySet()) {
        if (stopIteration()) {
          break;
        }
        final File dir = virtualToIoFile(entry.getKey());
        try {
          final PropertyValue value;
          if (myCanUseCachedProperty) {
            value = myVcs.getPropertyWithCaching(entry.getKey(), SvnPropertyKeys.SVN_IGNORE);
          } else {
            value = myVcs.getFactory(dir).createPropertyClient()
              .getProperty(Target.on(dir), SvnPropertyKeys.SVN_IGNORE, false, Revision.WORKING);
          }
          processFolder(entry.getKey(), dir, entry.getValue(), value);
        }
        catch (VcsException e) {
          onSVNException(e);
        }
      }
      onAfterProcessing(file);
    }
  }

  private static final class IgnorePropertyChecker extends IgnorePropertyWorkTemplate {
    private final String myExtensionPattern;
    private boolean myFilesOk;
    private boolean myExtensionOk;

    private IgnorePropertyChecker(@NotNull SvnVcs vcs, String extensionPattern) {
      super(vcs, false, true);
      myExtensionPattern = extensionPattern;
      myExtensionOk = true;
      myFilesOk = true;
    }

    @Override
    protected boolean stopIteration() {
      return (! myFilesOk) && (! myExtensionOk);
    }

    @Override
    protected void processFolder(final VirtualFile folder, final File folderDir, final Set<String> data, final PropertyValue propertyValue) {
      if (propertyValue == null) {
        myFilesOk = false;
        myExtensionOk = false;
        return;
      }
      final Set<String> ignorePatterns = new HashSet<>();
      final StringTokenizer st = new StringTokenizer(PropertyValue.toString(propertyValue), "\r\n ");
      while (st.hasMoreElements()) {
        final String ignorePattern = (String)st.nextElement();
        ignorePatterns.add(ignorePattern);
      }

      myExtensionOk &= ignorePatterns.contains(myExtensionPattern);
      for (final String fileName : data) {
        if (!ignorePatterns.contains(fileName)) {
          myFilesOk = false;
          break;
        }
      }
    }

    @Override
    protected void onAfterProcessing(final VirtualFile[] file) {
    }

    @Override
    protected void onSVNException(final Exception e) {
      myFilesOk = false;
      myExtensionOk = false;
    }

    public boolean filesOk() {
      return myFilesOk;
    }

    public boolean extensionOk() {
      return myExtensionOk;
    }
  }

  private abstract static class IgnorePropertyAddRemoveTemplate extends IgnorePropertyWorkTemplate {
    private final Collection<String> exceptions;
    private final VcsDirtyScopeManager dirtyScopeManager;

    private IgnorePropertyAddRemoveTemplate(@NotNull SvnVcs vcs, boolean useCommonExtension) {
      super(vcs, useCommonExtension, false);
      exceptions = new ArrayList<>();
      dirtyScopeManager = VcsDirtyScopeManager.getInstance(vcs.getProject());
    }

    @Override
    protected boolean stopIteration() {
      return false;
    }

    protected abstract String getNewPropertyValue(final Set<String> data, final PropertyValue propertyValue);

    @Override
    protected void processFolder(final VirtualFile folder, final File folderDir, final Set<String> data, final PropertyValue propertyValue)
      throws VcsException {
      String newValue = getNewPropertyValue(data, propertyValue);
      newValue = (newValue.trim().isEmpty()) ? null : newValue;
      myVcs.getFactory(folderDir).createPropertyClient()
        .setProperty(folderDir, SvnPropertyKeys.SVN_IGNORE, PropertyValue.create(newValue), Depth.EMPTY, false);

      if (myUseCommonExtension) {
        dirtyScopeManager.dirDirtyRecursively(folder);
      }
    }

    @Override
    protected void onAfterProcessing(final VirtualFile[] file) throws VcsException {
      if (! myUseCommonExtension) {
        for (VirtualFile virtualFile : file) {
          dirtyScopeManager.fileDirty(virtualFile);
        }
      }

      if (!exceptions.isEmpty()) {
        throw new VcsException(exceptions);
      }
    }

    @Override
    protected void onSVNException(final Exception e) {
      exceptions.add(e.getMessage());
    }
  }

  private static final class IgnorePropertyRemover extends IgnorePropertyAddRemoveTemplate {
    private IgnorePropertyRemover(@NotNull SvnVcs vcs, boolean useCommonExtension) {
      super(vcs, useCommonExtension);
    }

    @Override
    protected String getNewPropertyValue(final Set<String> data, final PropertyValue propertyValue) {
      if (propertyValue != null) {
        return getNewPropertyValueForRemove(data, PropertyValue.toString(propertyValue));
      }
      return "";
    }
  }

  private static String getNewPropertyValueForRemove(final Collection<String> data, @NotNull final String propertyValue) {
    final StringBuilder sb = new StringBuilder();
    final StringTokenizer st = new StringTokenizer(propertyValue, "\r\n ");
    while (st.hasMoreElements()) {
      final String ignorePattern = (String)st.nextElement();
      if (! data.contains(ignorePattern)) {
        sb.append(ignorePattern).append('\n');
      }
    }
    return sb.toString();
  }

  private static final class IgnorePropertyAdder extends IgnorePropertyAddRemoveTemplate {
    private IgnorePropertyAdder(@NotNull SvnVcs vcs, boolean useCommonExtension) {
      super(vcs, useCommonExtension);
    }

    @Override
    protected String getNewPropertyValue(final Set<String> data, final PropertyValue propertyValue) {
      final String ignoreString;
      if (data.size() == 1) {
        ignoreString = data.iterator().next();
      } else {
        final StringBuilder sb = new StringBuilder();
        for (final String name : data) {
          sb.append(name).append('\n');
        }
        ignoreString = sb.toString();
      }
      return (propertyValue == null) ? ignoreString : (PropertyValue.toString(propertyValue) + '\n' + ignoreString);
    }
  }
}
