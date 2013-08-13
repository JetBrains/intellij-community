/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.quickfix;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.NetUtils;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URLConnection;
import java.util.Set;

@SuppressWarnings({"StringEquality"})
public abstract class DownloadManager {
  private static final ExternalResourceManager resourceManager = ExternalResourceManager.getInstance();

  private final Project myProject;
  private final ProgressIndicator myProgress;
  private final String myResourcePath;

  public DownloadManager(Project project, ProgressIndicator progress) {
    myProject = project;
    myProgress = progress;

    myResourcePath = PathManager.getSystemPath() + File.separatorChar + "extResources";
    final File dir = new File(myResourcePath);
    dir.mkdirs();
  }

  public void fetch(String location) throws DownloadException {
    if (resourceManager.getResourceLocation(location) == location) {
      myProgress.setText("Downloading " + location);
      downloadAndRegister(location);
    }
  }

  private void downloadAndRegister(final String location) throws DownloadException {
    final ExternalResourceManager resourceManager = ExternalResourceManager.getInstance();

    File file = null;
    try {
      final URLConnection urlConnection = HttpConfigurable.getInstance().openConnection(location);
      urlConnection.connect();
      final InputStream in = urlConnection.getInputStream();

      final OutputStream out;
      try {
        final int total = urlConnection.getContentLength();

        final String name = Integer.toHexString(System.identityHashCode(this)) +
                            "_" +
                            Integer.toHexString(location.hashCode()) +
                            "_" +
                            location.substring(location.lastIndexOf('/') + 1);
        file = new File(myResourcePath, name.lastIndexOf('.') == -1 ? name + ".xml" : name);
        out = new FileOutputStream(file);

        try {
          NetUtils.copyStreamContent(myProgress, in, out, total);
        }
        finally {
          out.close();
        }
      }
      finally {
        in.close();
      }

      try {
        final File _file = file;

        //noinspection unchecked
        final Set<String>[] resourceDependencies = new Set[1];
        new WriteAction() {
          @Override
          protected void run(Result result) throws Throwable {
            final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(_file);
            if (vf != null) {
              final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vf);
              if (psiFile != null && isAccepted(psiFile)) {
                resourceDependencies[0] = getResourceDependencies(psiFile);
                resourceManager.addResource(location, _file.getAbsolutePath());
              }
              else {
                ApplicationManager.getApplication().invokeLater(
                  new Runnable() {
                    @Override
                    public void run() {
                      Messages.showErrorDialog(myProject, "Not a valid file: " + vf.getPresentableUrl(), "Download Problem");
                    }
                  }, myProject.getDisposed());
              }
            }
          }
        }.execute();

        if (resourceDependencies[0] != null) {
          for (String s : resourceDependencies[0]) {
            myProgress.checkCanceled();
            myProgress.setFraction(0);
            fetch(s);
          }
        }
      }
      catch (Error err) {
        Throwable e = err.getCause();
        if  (e instanceof InterruptedException) {
          // OK
        }
        else if (e instanceof InvocationTargetException) {
          final Throwable targetException = ((InvocationTargetException)e).getTargetException();
          if (targetException instanceof RuntimeException) {
            throw (RuntimeException)targetException;
          }
          else if (targetException instanceof IOException) {
            throw (IOException)targetException;
          }
          else if (targetException instanceof InterruptedException) {
            // OK
          }
          else {
            Logger.getInstance(getClass().getName()).error(e);
          }
        }
        else {
          throw err;
        }
      }
    }
    catch (IOException e) {
      throw new DownloadException(location, e);
    }
    finally {
      if (file != null && resourceManager.getResourceLocation(location) == location) {
        // something went wrong. get rid of the file
        file.delete();
      }
    }
  }

  protected abstract boolean isAccepted(PsiFile psiFile);

  protected abstract Set<String> getResourceDependencies(PsiFile psiFile);

  public static class DownloadException extends IOException {
    private final String myLocation;

    public DownloadException(String location, IOException cause) {
      super();
      myLocation = location;
      initCause(cause);
    }

    public String getLocation() {
      return myLocation;
    }
  }
}