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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.io.HttpRequests;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

public abstract class DownloadManager {
  private final Project project;
  private final ProgressIndicator progress;
  private final String resourcePath;

  public DownloadManager(Project project, ProgressIndicator progress) {
    this.project = project;
    this.progress = progress;

    resourcePath = PathManager.getSystemPath() + File.separatorChar + "extResources";
    //noinspection ResultOfMethodCallIgnored
    new File(resourcePath).mkdirs();
  }

  public void fetch(@NotNull String location) throws DownloadException {
    if (!location.equals(ExternalResourceManager.getInstance().getResourceLocation(location, project))) {
      return;
    }

    progress.setText(XPathBundle.message("progress.text.downloading", location));

    File file = null;
    try {
      file = HttpRequests.request(location).connect(new HttpRequests.RequestProcessor<>() {
        @Override
        public File process(@NotNull HttpRequests.Request request) throws IOException {
          String name = Integer.toHexString(System.identityHashCode(this)) + "_" +
                        Integer.toHexString(location.hashCode()) + "_" +
                        location.substring(location.lastIndexOf('/') + 1);
          return request.saveToFile(new File(resourcePath, name.lastIndexOf('.') == -1 ? name + ".xml" : name), progress);
        }
      });

      try {
        //noinspection unchecked
        Set<String>[] resourceDependencies = new Set[1];
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (vf != null) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
          if (psiFile != null && isAccepted(psiFile)) {
            resourceDependencies[0] = getResourceDependencies(psiFile);
            ExternalResourceManager.getInstance().addResource(location, file.getAbsolutePath());
          }
          else {
            ApplicationManager.getApplication().invokeLater(() -> {
              Messages.showErrorDialog(project,
                                       XPathBundle.message("dialog.message.not.valid.file", vf.getPresentableUrl()),
                                       XPathBundle.message("dialog.title.download.problem"));
            }, project.getDisposed());
          }
        }

        if (resourceDependencies[0] != null) {
          for (String s : resourceDependencies[0]) {
            progress.checkCanceled();
            progress.setFraction(0);
            fetch(s);
          }
        }
      }
      catch (Error err) {
        Throwable e = err.getCause();
        if (e instanceof InvocationTargetException) {
          Throwable targetException = ((InvocationTargetException)e).getTargetException();
          ExceptionUtil.rethrowUnchecked(targetException);
          if (targetException instanceof IOException) {
            throw (IOException)targetException;
          }
          if (!(targetException instanceof InterruptedException)) {
            Logger.getInstance(getClass().getName()).error(e);
          }
        }
        else if (!(e instanceof InterruptedException)) {
          throw err;
        }
      }
    }
    catch (IOException e) {
      throw new DownloadException(location, e);
    }
    finally {
      if (file != null && location.equals(ExternalResourceManager.getInstance().getResourceLocation(location, project))) {
        // something went wrong. get rid of the file
        FileUtil.delete(file);
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