package com.intellij.tasks.generic;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class SelectorBasedResponseHandler extends ResponseHandler {

  protected LinkedHashMap<String, Selector> mySelectors = new LinkedHashMap<String, Selector>();

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  protected SelectorBasedResponseHandler() {
    // empty
  }

  protected SelectorBasedResponseHandler(GenericRepository repository) {
    super(repository);
    // standard selectors
    setSelectors(ContainerUtil.newArrayList(
      // matched against list of tasks at whole downloaded from "taskListUrl"
      new Selector("tasks", ""),

      // matched against single tasks extracted from the list downloaded from "taskListUrl"
      new Selector("id"),
      new Selector("summary"),
      new Selector("description"),
      new Selector("updated"),
      new Selector("created"),
      new Selector("closed"),
      new Selector("issueUrl"),

      // matched against single task downloaded from "singleTaskUrl"
      new Selector("singleTask-id"),
      new Selector("singleTask-summary"),
      new Selector("singleTask-description"),
      new Selector("singleTask-updated"),
      new Selector("singleTask-created"),
      new Selector("singleTask-closed"),
      new Selector("singleTask-issueUrl")
    ));
  }

  public abstract FileType getSelectorFileType();

  @Tag("selectors")
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  @NotNull
  public List<Selector> getSelectors() {
    return new ArrayList<Selector>(mySelectors.values());
  }

  public void setSelectors(@NotNull List<Selector> selectors) {
    mySelectors.clear();
    for (Selector selector : selectors) {
      mySelectors.put(selector.getName(), selector);
    }
  }

  @Nullable
  public Selector getSelector(@NotNull String name) {
    return mySelectors.get(name);
  }

  @Nullable
  public String getSelectorPath(@NotNull String name) {
    Selector s = getSelector(name);
    return s == null ? null : s.getPath();
  }

  @Override
  public JComponent getConfigurationComponent(Project project) {
    HighlightedSelectorsTable table = new HighlightedSelectorsTable(getSelectorFileType(),
                                                                    project,
                                                                    getSelectors());
    return new JBScrollPane(table);
  }

  @Override
  public SelectorBasedResponseHandler clone() {
    SelectorBasedResponseHandler clone = (SelectorBasedResponseHandler)super.clone();
    clone.mySelectors = new LinkedHashMap<String, Selector>(mySelectors.size());
    for (Selector selector : mySelectors.values()) {
      clone.mySelectors.put(selector.getName(), selector.clone());
    }
    return clone;
  }

  @Override
  public boolean isConfigured() {
    Selector idSelector = getSelector("id");
    if (idSelector == null || StringUtil.isEmpty(idSelector.getPath())) return false;
    Selector summarySelector = getSelector("summary");
    if (summarySelector == null || StringUtil.isEmpty(summarySelector.getPath())) return false;
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SelectorBasedResponseHandler)) return false;

    SelectorBasedResponseHandler handler = (SelectorBasedResponseHandler)o;

    if (!mySelectors.equals(handler.mySelectors)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return mySelectors.hashCode();
  }

  @NotNull
  @Override
  public final Task[] parseIssues(String response) throws Exception {
    if (StringUtil.isEmpty(getSelectorPath("tasks")) ||
        StringUtil.isEmpty(getSelectorPath("id")) ||
        StringUtil.isEmpty(getSelectorPath("summary"))) {
      throw new Exception("Selectors 'tasks', 'id' and 'summary' are mandatory");
    }
    return doParseIssues(response);
  }

  protected abstract Task[] doParseIssues(String response) throws Exception;

  @Nullable
  @Override
  public final Task parseIssue(String response) throws Exception {
    if (StringUtil.isEmpty(getSelectorPath("singleTask-id")) ||
        StringUtil.isEmpty(getSelectorPath("singleTask-summary"))) {
      throw new Exception("Selectors 'singleTask-id' and 'singleTask-summary' are mandatory");
    }
    return doParseIssue(response);
  }

  protected abstract Task doParseIssue(String response) throws Exception;
}
