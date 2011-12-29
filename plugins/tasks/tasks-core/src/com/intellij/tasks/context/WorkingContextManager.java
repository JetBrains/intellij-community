/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.context;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.tasks.Task;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.zip.JBZipEntry;
import com.intellij.util.io.zip.JBZipFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class WorkingContextManager {

  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.context.WorkingContextManager");
  @NonNls private static final String TASKS_FOLDER = "tasks";

  private final Project myProject;
  @NonNls private static final String TASKS_ZIP_POSTFIX = ".tasks.zip";
  @NonNls private static final String TASK_XML_POSTFIX = ".task.xml";
  private static final String CONTEXT_ZIP_POSTFIX = ".contexts.zip";
  private static final Comparator<JBZipEntry> ENTRY_COMPARATOR = new Comparator<JBZipEntry>() {
    public int compare(JBZipEntry o1, JBZipEntry o2) {
      return (int)(o2.getTime() - o1.getTime());
    }
  };

  public static WorkingContextManager getInstance(Project project) {
    return ServiceManager.getService(project, WorkingContextManager.class);
  }

  public WorkingContextManager(Project project) {
    myProject = project;
  }

  private void loadContext(Element fromElement) {
    for (WorkingContextProvider provider : Extensions.getExtensions(WorkingContextProvider.EP_NAME, myProject)) {
      try {
        Element child = fromElement.getChild(provider.getId());
        if (child != null) {
          provider.loadContext(child);
        }
      }
      catch (InvalidDataException e) {
        LOG.error(e);
      }
    }
  }

  public void saveContext(Element toElement) {
    for (WorkingContextProvider provider : Extensions.getExtensions(WorkingContextProvider.EP_NAME, myProject)) {
      try {
        Element child = new Element(provider.getId());
        provider.saveContext(child);
        toElement.addContent(child);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
      }
    }
  }

  public void clearContext() {
    for (WorkingContextProvider provider : Extensions.getExtensions(WorkingContextProvider.EP_NAME, myProject)) {
      provider.clearContext();
    }
  }

  public void saveContext(Task task) {
    String entryName = task.getId() + TASK_XML_POSTFIX;
    saveContext(entryName, TASKS_ZIP_POSTFIX, task.getSummary());
  }

  public void saveContext(@Nullable String entryName, @Nullable String comment) {
    saveContext(entryName, CONTEXT_ZIP_POSTFIX, comment);
  }

  private void saveContext(@Nullable String entryName, String zipPostfix, @Nullable String comment) {
    try {
      JBZipFile archive = getTasksArchive(zipPostfix);
      if (entryName == null) {
        int i = archive.getEntries().size();
        do {
          entryName = "context" + i++;
        } while (archive.getEntry("/" + entryName) != null);
      }
      JBZipEntry entry = archive.getOrCreateEntry("/" + entryName);
      if (comment != null) {
        entry.setComment(comment);
      }
      Element element = new Element("context");
      saveContext(element);
      String s = new XMLOutputter().outputString(element);
      entry.setData(s.getBytes());
      archive.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private JBZipFile getTasksArchive(String postfix) throws IOException {
    String configPath = PathManager.getConfigPath(true);
    File tasksFolder = new File(configPath, TASKS_FOLDER);
    if (!tasksFolder.exists()) {
      //noinspection ResultOfMethodCallIgnored
      tasksFolder.mkdir();
    }
    String projectName = myProject.getName();
    return new JBZipFile(new File(tasksFolder, projectName + postfix));
  }

  public void restoreContext(@NotNull Task task) {
    loadContext(TASKS_ZIP_POSTFIX, task.getId() + TASK_XML_POSTFIX);
  }

  private boolean loadContext(String zipPostfix, String entryName) {
    try {
      JBZipFile archive = getTasksArchive(zipPostfix);
      JBZipEntry entry = archive.getEntry(entryName.startsWith("/") ? entryName : "/" + entryName);
      if (entry != null) {
        byte[] bytes = entry.getData();
        Document document = JDOMUtil.loadDocument(new String(bytes));
        Element rootElement = document.getRootElement();
        loadContext(rootElement);
        archive.close();
        return true;
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return false;
  }

  public List<ContextInfo> getContextHistory() {
    return getContextHistory(CONTEXT_ZIP_POSTFIX);
  }

  private List<ContextInfo> getContextHistory(String zipPostfix) {
    try {
      JBZipFile archive = getTasksArchive(zipPostfix);
      List<JBZipEntry> entries = archive.getEntries();
      return ContainerUtil.mapNotNull(entries, new NullableFunction<JBZipEntry, ContextInfo>() {
        public ContextInfo fun(JBZipEntry entry) {
          return entry.getName().startsWith("/context") ? new ContextInfo(entry.getName(), entry.getTime(), entry.getComment()) : null;
        }
      });
    }
    catch (IOException e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }

  public boolean loadContext(String name) {
    return loadContext(CONTEXT_ZIP_POSTFIX, name);
  }

  public void removeContext(String name) {
    removeContext(name, CONTEXT_ZIP_POSTFIX);
  }

  public void removeContext(Task task) {
    removeContext(task.getId(), TASKS_ZIP_POSTFIX);
  }

  private void removeContext(String name, String postfix) {
    try {
      JBZipFile archive = getTasksArchive(postfix);
      JBZipEntry entry = archive.getEntry(name);
      if (entry != null) {
        archive.eraseEntry(entry);
      }
      archive.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void pack(int max, int delta) {
    pack(max, delta, CONTEXT_ZIP_POSTFIX);
    pack(max, delta, TASKS_ZIP_POSTFIX);
  }

  private void pack(int max, int delta, String zipPostfix) {
    try {
      JBZipFile archive = getTasksArchive(zipPostfix);
      List<JBZipEntry> entries = archive.getEntries();
      if (entries.size() > max + delta) {
        JBZipEntry[] array = entries.toArray(new JBZipEntry[entries.size()]);
        Arrays.sort(array, ENTRY_COMPARATOR);
        for (int i = array.length - 1; i >= max; i--) {
          archive.eraseEntry(array[i]);
        }
      }
      archive.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }

  }
}
