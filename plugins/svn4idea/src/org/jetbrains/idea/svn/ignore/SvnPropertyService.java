package org.jetbrains.idea.svn.ignore;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.*;

public class SvnPropertyService {
  private SvnPropertyService() {
  }

  public static void doAddToIgnoreProperty(final SvnVcs activeVcs, final Project project, final boolean useCommonExtension,
                                           final VirtualFile[] file, final IgnoreInfoGetter getter) throws VcsException {
    final IgnorePropertyAdder adder = new IgnorePropertyAdder(activeVcs, project, useCommonExtension);
    adder.execute(file, getter);
  }

  public static void doRemoveFromIgnoreProperty(final SvnVcs activeVcs, final Project project, final boolean useCommonExtension,
                                           final VirtualFile[] file, final IgnoreInfoGetter getter) throws VcsException{
    final IgnorePropertyRemover remover = new IgnorePropertyRemover(activeVcs, project, useCommonExtension);
    remover.execute(file, getter);
  }

  public static void doCheckIgnoreProperty(final SvnVcs activeVcs, final Project project, final VirtualFile[] file,
        final IgnoreInfoGetter getter, final String extensionPattern, final Ref<Boolean> filesOk, final Ref<Boolean> extensionOk) {
    final IgnorePropertyChecker checker = new IgnorePropertyChecker(activeVcs, project, extensionPattern);
    try {
      checker.execute(file, getter);
    } catch (VcsException e) {
      // ignore - actually never thrown inside
    }
    filesOk.set(checker.filesOk());
    extensionOk.set(checker.extensionOk());
  }

  private static abstract class IgnorePropertyWorkTemplate {
    protected final SvnVcs myVcs;
    protected final SVNWCClient myClient;
    protected final Project myProject;
    protected final boolean myUseCommonExtension;
    
    protected abstract void processFolder(final VirtualFile folder, final File folderDir, final Set<String> data,
                                          final SVNPropertyValue propertyValue) throws SVNException;
    protected abstract void onAfterProcessing(final VirtualFile[] file) throws VcsException;
    protected abstract void onSVNException(SVNException e);
    protected abstract boolean stopIteration();

    private IgnorePropertyWorkTemplate(final SvnVcs activeVcs, final Project project, final boolean useCommonExtension) {
      myVcs = activeVcs;
      myClient = activeVcs.createWCClient();
      myProject = project;
      myUseCommonExtension = useCommonExtension;
    }

    public void execute(final VirtualFile[] file, final IgnoreInfoGetter getter) throws VcsException {
      final Map<VirtualFile, Set<String>> foldersInfo = getter.getInfo(myUseCommonExtension);
      for (final Map.Entry<VirtualFile, Set<String>> entry : foldersInfo.entrySet()) {
        if (stopIteration()) {
          break;
        }
        final File dir = new File(entry.getKey().getPath());
        try {
          final SVNPropertyValue value = myVcs.getPropertyWithCaching(entry.getKey(), SvnPropertyKeys.SVN_IGNORE);
          processFolder(entry.getKey(), dir, entry.getValue(), value);
        }
        catch (SVNException e) {
          onSVNException(e);
        }
      }
      onAfterProcessing(file);
    }
  }

  private static class IgnorePropertyChecker extends IgnorePropertyWorkTemplate {
    private final String myExtensionPattern;
    private boolean myFilesOk;
    private boolean myExtensionOk;

    private IgnorePropertyChecker(final SvnVcs activeVcs, final Project project, final String extensionPattern) {
      super(activeVcs, project, false);
      myExtensionPattern = extensionPattern;
      myExtensionOk = true;
      myFilesOk = true;
    }

    protected boolean stopIteration() {
      return (! myFilesOk) && (! myExtensionOk);
    }

    protected void processFolder(final VirtualFile folder, final File folderDir, final Set<String> data, final SVNPropertyValue propertyValue)
        throws SVNException {
      if (propertyValue == null) {
        myFilesOk = false;
        myExtensionOk = false;
        return;
      }
      final Set<String> ignorePatterns = new HashSet<String>();
      final StringTokenizer st = new StringTokenizer(SVNPropertyValue.getPropertyAsString(propertyValue), "\r\n ");
      while (st.hasMoreElements()) {
        final String ignorePattern = (String)st.nextElement();
        ignorePatterns.add(ignorePattern);
      }

      myExtensionOk &= ignorePatterns.contains(myExtensionPattern);
      for (final String fileName : data) {
        if (!ignorePatterns.contains(fileName)) {
          myFilesOk = false;
        }
      }
    }

    protected void onAfterProcessing(final VirtualFile[] file) throws VcsException {
    }

    protected void onSVNException(final SVNException e) {
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

    private IgnorePropertyAddRemoveTemplate(final SvnVcs activeVcs, final Project project, final boolean useCommonExtension) {
      super(activeVcs, project, useCommonExtension);
      exceptions = new ArrayList<String>();
      dirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
    }

    protected boolean stopIteration() {
      return false;
    }

    protected abstract String getNewPropertyValue(final Set<String> data, final SVNPropertyValue propertyValue);

    protected void processFolder(final VirtualFile folder, final File folderDir, final Set<String> data, final SVNPropertyValue propertyValue)
        throws SVNException {
      final String newValue = getNewPropertyValue(data, propertyValue);

      myClient.doSetProperty(folderDir, SvnPropertyKeys.SVN_IGNORE, SVNPropertyValue.create(newValue), false, false, null);

      if (myUseCommonExtension) {
        dirtyScopeManager.dirDirtyRecursively(folder);
      }
    }

    protected void onAfterProcessing(final VirtualFile[] file) throws VcsException {
      if (! myUseCommonExtension) {
        for (VirtualFile virtualFile : file) {
          VcsDirtyScopeManager.getInstance(myProject).fileDirty(virtualFile);
        }
      }

      if (!exceptions.isEmpty()) {
        throw new VcsException(exceptions);
      }
    }

    protected void onSVNException(final SVNException e) {
      exceptions.add(e.getMessage());
    }
  }

  private static class IgnorePropertyRemover extends IgnorePropertyAddRemoveTemplate {
    private IgnorePropertyRemover(final SvnVcs activeVcs, final Project project, final boolean useCommonExtension) {
      super(activeVcs, project, useCommonExtension);
    }

    protected String getNewPropertyValue(final Set<String> data, final SVNPropertyValue propertyValue) {
      if (propertyValue != null) {
        final StringBuilder sb = new StringBuilder();
        final StringTokenizer st = new StringTokenizer(SVNPropertyValue.getPropertyAsString(propertyValue), "\r\n ");
        while (st.hasMoreElements()) {
          final String ignorePattern = (String)st.nextElement();
          if (! data.contains(ignorePattern)) {
            sb.append(ignorePattern).append('\n');
          }
        }
        return sb.toString();
      } else {
        return "";
      }
    }
  }

  private static class IgnorePropertyAdder extends IgnorePropertyAddRemoveTemplate {
    private IgnorePropertyAdder(final SvnVcs activeVcs, final Project project, final boolean useCommonExtension) {
      super(activeVcs, project, useCommonExtension);
    }

    protected String getNewPropertyValue(final Set<String> data, final SVNPropertyValue propertyValue) {
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
      return (propertyValue == null) ? ignoreString : (SVNPropertyValue.getPropertyAsString(propertyValue) + '\n' + ignoreString);
    }
  }
}
