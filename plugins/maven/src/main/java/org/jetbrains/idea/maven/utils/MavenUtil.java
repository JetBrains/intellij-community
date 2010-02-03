/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.maven.model.Model;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MavenUtil {
  public static void invokeLater(Project p, Runnable r) {
    invokeLater(p, ModalityState.defaultModalityState(), r);
  }

  public static void invokeLater(final Project p, final ModalityState state, final Runnable r) {
    if (isNoBackgroundMode()) {
      r.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (p.isDisposed()) return;
          r.run();
        }
      }, state);
    }
  }

  public static void invokeAndWait(Project p, Runnable r) {
    invokeAndWait(p, ModalityState.defaultModalityState(), r);
  }

  public static void invokeAndWait(final Project p, final ModalityState state, final Runnable r) {
    if (isNoBackgroundMode()) {
      r.run();
    }
    else {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        r.run();
      }
      else {
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          public void run() {
            if (p.isDisposed()) return;
            r.run();
          }
        }, state);
      }
    }
  }

  public static void invokeAndWaitWriteAction(Project p, final Runnable r) {
    invokeAndWait(p, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(r);
      }
    });
  }

  public static void runDumbAware(final Project project, final Runnable r) {
    if (r instanceof DumbAware) {
      r.run();
    }
    else {
      DumbService.getInstance(project).runWhenSmart(new Runnable() {
        public void run() {
          if (project.isDisposed()) return;
          r.run();
        }
      });
    }
  }

  public static void runWhenInitialized(final Project project, final Runnable r) {
    if (project.isDisposed()) return;

    if (isNoBackgroundMode()) {
      r.run();
      return;
    }

    if (!project.isInitialized()) {
      StartupManager.getInstance(project).registerPostStartupActivity(r);
      return;
    }

    runDumbAware(project, r);
  }

  public static boolean isNoBackgroundMode() {
    return ApplicationManager.getApplication().isUnitTestMode()
           || ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  public static boolean isInModalContext() {
    if (isNoBackgroundMode()) return false;
    return LaterInvocator.isInModalContext();
  }

  public static void showError(Project project, String title, Throwable e) {
    MavenLog.LOG.warn(title, e);
    Notifications.Bus.notify(new Notification("Maven", title, e.getMessage(), NotificationType.ERROR), project);
  }

  public static Properties getSystemProperties() {
    Properties result = (Properties)System.getProperties().clone();
    for (String each : new THashSet<String>((Set)result.keySet())) {
      if (each.startsWith("idea.")) {
        result.remove(each);
      }
    }
    return result;
  }

  public static Properties getEnvProperties() {
    Properties reuslt = new Properties();
    for (Map.Entry<String, String> each : System.getenv().entrySet()) {
      if (isMagicalProperty(each.getKey())) continue;
      reuslt.put(each.getKey(), each.getValue());
    }
    return reuslt;
  }

  private static boolean isMagicalProperty(String key) {
    return key.startsWith("=");
  }

  public static File getPluginSystemDir(String folder) {
    // PathManager.getSystemPath() may return relative path
    return new File(PathManager.getSystemPath(), "Maven" + "/" + folder).getAbsoluteFile();
  }

  public static VirtualFile findProfilesXmlFile(VirtualFile pomFile) {
    return pomFile.getParent().findChild(MavenConstants.PROFILES_XML);
  }

  public static File getProfilesXmlIoFile(VirtualFile pomFile) {
    return new File(pomFile.getParent().getPath(), MavenConstants.PROFILES_XML);
  }

  public static <T, U> List<T> collectFirsts(List<Pair<T, U>> pairs) {
    List<T> result = new ArrayList<T>(pairs.size());
    for (Pair<T, ?> each : pairs) {
      result.add(each.first);
    }
    return result;
  }

  public static <T, U> List<U> collectSeconds(List<Pair<T, U>> pairs) {
    List<U> result = new ArrayList<U>(pairs.size());
    for (Pair<T, U> each : pairs) {
      result.add(each.second);
    }
    return result;
  }

  public static List<String> collectPaths(List<VirtualFile> files) {
    return ContainerUtil.map(files, new Function<VirtualFile, String>() {
      public String fun(VirtualFile file) {
        return file.getPath();
      }
    });
  }

  public static List<VirtualFile> collectFiles(Collection<MavenProject> projects) {
    return ContainerUtil.map(projects, new Function<MavenProject, VirtualFile>() {
      public VirtualFile fun(MavenProject project) {
        return project.getFile();
      }
    });
  }

  public static <T> boolean equalAsSets(final Collection<T> collection1, final Collection<T> collection2) {
    return toSet(collection1).equals(toSet(collection2));
  }

  private static <T> Collection<T> toSet(final Collection<T> collection) {
    return (collection instanceof Set ? collection : new THashSet<T>(collection));
  }

  public static <T, U> List<Pair<T, U>> mapToList(Map<T, U> map) {
    return ContainerUtil.map2List(map.entrySet(), new Function<Map.Entry<T, U>, Pair<T, U>>() {
      public Pair<T, U> fun(Map.Entry<T, U> tuEntry) {
        return Pair.create(tuEntry.getKey(), tuEntry.getValue());
      }
    });
  }

  public static String formatHtmlImage(URL url) {
    return "<img src=\"" + url + "\"> ";
  }

  public static void runOrApplyMavenProjectFileTemplate(Project project,
                                                        VirtualFile file,
                                                        MavenId projectId,
                                                        MavenId parentId,
                                                        boolean interactive) throws IOException {
    Properties properties = new Properties();
    Properties conditions = new Properties();
    properties.setProperty("GROUP_ID", projectId.getGroupId());
    properties.setProperty("ARTIFACT_ID", projectId.getArtifactId());
    properties.setProperty("VERSION", projectId.getVersion());
    if (parentId != null) {
      conditions.setProperty("HAS_PARENT", "true");
      properties.setProperty("PARENT_GROUP_ID", parentId.getGroupId());
      properties.setProperty("PARENT_ARTIFACT_ID", parentId.getArtifactId());
      properties.setProperty("PARENT_VERSION", parentId.getVersion());
    }
    runOrApplyFileTemplate(project, file, MavenFileTemplateGroupFactory.MAVEN_PROJECT_XML_TEMPLATE, properties, conditions, interactive);
  }

  public static void runFileTemplate(Project project,
                                     VirtualFile file,
                                     String templateName) throws IOException {
    runOrApplyFileTemplate(project, file, templateName, new Properties(), new Properties(), true);
  }

  private static void runOrApplyFileTemplate(Project project,
                                             VirtualFile file,
                                             String templateName,
                                             Properties properties,
                                             Properties conditions,
                                             boolean interactive) throws IOException {
    FileTemplateManager manager = FileTemplateManager.getInstance();
    FileTemplate fileTemplate = manager.getJ2eeTemplate(templateName);
    Properties allProperties = manager.getDefaultProperties();
    if (!interactive) {
      allProperties.putAll(properties);
    }
    allProperties.putAll(conditions);
    String text = fileTemplate.getText(allProperties);
    Pattern pattern = Pattern.compile("\\$\\{(.*)\\}");
    Matcher matcher = pattern.matcher(text);
    StringBuffer builder = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(builder, "\\$" + matcher.group(1).toUpperCase() + "\\$");
    }
    matcher.appendTail(builder);
    text = builder.toString();

    TemplateImpl template = (TemplateImpl)TemplateManager.getInstance(project).createTemplate("", "", text);
    for (int i = 0; i < template.getSegmentsCount(); i++) {
      if (i == template.getEndSegmentNumber()) continue;
      String name = template.getSegmentName(i);
      String value = "\"" + properties.getProperty(name, "") + "\"";
      template.addVariable(name, value, value, true);
    }

    if (interactive) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
      Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      editor.getDocument().setText("");
      TemplateManager.getInstance(project).startTemplate(editor, template);
    }
    else {
      VfsUtil.saveText(file, template.getTemplateText());
    }
  }

  public static <T extends Collection<Pattern>> T collectPattern(String text, T result) {
    String antPattern = FileUtil.convertAntToRegexp(text.trim());
    try {
      result.add(Pattern.compile(antPattern));
    }
    catch (PatternSyntaxException ignore) {
    }
    return result;
  }

  public static boolean isIncluded(String relativeName, List<Pattern> includes, List<Pattern> excludes) {
    boolean result = false;
    for (Pattern each : includes) {
      if (each.matcher(relativeName).matches()) {
        result = true;
        break;
      }
    }
    if (!result) return false;
    for (Pattern each : excludes) {
      if (each.matcher(relativeName).matches()) return false;
    }
    return true;
  }

  public static <T extends Serializable> T cloneObject(T object) {
    try {
      return (T)BeanUtils.cloneBean(object);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch (InstantiationException e) {
      throw new RuntimeException(e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public static void stripDown(Object object) {
    try {
      for (Field each : ReflectionUtil.collectFields(object.getClass())) {
        Class<?> type = each.getType();
        each.setAccessible(true);
        Object value = each.get(object);
        if (shouldStrip(value)) {
          each.set(object, null);
        }
        else {
          if (value != null) {
            Package pack = type.getPackage();
            if (pack != null && Model.class.getPackage().getName().equals(pack.getName())) {
              stripDown(value);
            }
          }
        }
      }
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean shouldStrip(Object value) {
    if (value == null) return false;
    return value.getClass().isArray()
           || value instanceof Collection
           || value instanceof Map
           || value instanceof Xpp3Dom;
  }

  public static void run(Project project, String title, final MavenTask task) throws MavenProcessCanceledException {
    final Exception[] canceledEx = new Exception[1];

    ProgressManager.getInstance().run(new Task.Modal(project, title, true) {
      public void run(@NotNull ProgressIndicator i) {
        try {
          task.run(new MavenProgressIndicator(i));
        }
        catch (MavenProcessCanceledException e) {
          canceledEx[0] = e;
        }
        catch (ProcessCanceledException e) {
          canceledEx[0] = e;
        }
      }
    });
    if (canceledEx[0] instanceof MavenProcessCanceledException) throw (MavenProcessCanceledException)canceledEx[0];
    if (canceledEx[0] instanceof ProcessCanceledException) throw new MavenProcessCanceledException();
  }

  public static MavenTaskHandler runInBackground(final Project project,
                                                 final String title,
                                                 final boolean cancellable,
                                                 final MavenTask task) {
    final MavenProgressIndicator indicator = new MavenProgressIndicator();

    Runnable runnable = new Runnable() {
      public void run() {
        try {
          task.run(indicator);
        }
        catch (MavenProcessCanceledException ignore) {
          indicator.cancel();
        }
        catch (ProcessCanceledException ignore) {
          indicator.cancel();
        }
      }
    };

    if (isNoBackgroundMode()) {
      runnable.run();
      return new MavenTaskHandler() {
        public void waitFor() {
        }
      };
    }
    else {
      final Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(runnable);
      final MavenTaskHandler handler = new MavenTaskHandler() {
        public void waitFor() {
          try {
            future.get();
          }
          catch (InterruptedException e) {
            MavenLog.LOG.error(e);
          }
          catch (ExecutionException e) {
            MavenLog.LOG.error(e);
          }
        }
      };
      invokeLater(project, new Runnable() {
        public void run() {
          if (future.isDone()) return;
          new Task.Backgroundable(project, title, cancellable) {
            public void run(@NotNull ProgressIndicator i) {
              indicator.setIndicator(i);
              handler.waitFor();
            }
          }.queue();
        }
      });
      return handler;
    }
  }

  public interface MavenTaskHandler {
    void waitFor();
  }
}
