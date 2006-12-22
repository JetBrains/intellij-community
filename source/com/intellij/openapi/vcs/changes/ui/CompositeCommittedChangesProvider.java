/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author yole
 */
public class CompositeCommittedChangesProvider implements CommittedChangesProvider<CommittedChangeList, CompositeCommittedChangesProvider.CompositeChangeBrowserSettings> {
  private List<AbstractVcs> myBaseVcss = new ArrayList<AbstractVcs>();

  public CompositeCommittedChangesProvider(final AbstractVcs... baseVcss) {
    myBaseVcss = new ArrayList<AbstractVcs>();
    Collections.addAll(myBaseVcss, baseVcss);
  }

  public CompositeCommittedChangesProvider.CompositeChangeBrowserSettings createDefaultSettings() {
    Map<AbstractVcs, ChangeBrowserSettings> map = new HashMap<AbstractVcs, ChangeBrowserSettings>();
    for(AbstractVcs vcs: myBaseVcss) {
      final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
      assert provider != null;
      map.put(vcs, provider.createDefaultSettings());
    }
    return new CompositeChangeBrowserSettings(map);
  }

  public ChangesBrowserSettingsEditor<CompositeCommittedChangesProvider.CompositeChangeBrowserSettings> createFilterUI() {
    return new CompositeChangesBrowserSettingsEditor();
  }

  public List<CommittedChangeList> getAllCommittedChanges(CompositeCommittedChangesProvider.CompositeChangeBrowserSettings settings, final int maxCount) throws VcsException {
    ArrayList<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
    for(AbstractVcs vcs: myBaseVcss) {
      CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
      assert provider != null;
      //noinspection unchecked
      result.addAll(provider.getAllCommittedChanges(settings.get(vcs), maxCount));
    }
    return result;
  }

  public List<CommittedChangeList> getCommittedChanges(CompositeCommittedChangesProvider.CompositeChangeBrowserSettings settings,
                                                       VirtualFile root, final int maxCount) throws VcsException {
    throw new UnsupportedOperationException();
  }

  public ChangeListColumn[] getColumns() {
    Set<ChangeListColumn> columns = new LinkedHashSet<ChangeListColumn>();
    for(AbstractVcs vcs: myBaseVcss) {
      final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
      assert provider != null;
      ChangeListColumn[] providerColumns = provider.getColumns();
      for(ChangeListColumn col: providerColumns) {
        if (col == ChangeListColumn.DATE || col == ChangeListColumn.DESCRIPTION || col == ChangeListColumn.NAME || col == ChangeListColumn.NUMBER) {
          columns.add(col);
        }
      }
    }
    return columns.toArray(new ChangeListColumn[columns.size()]);
  }

  public static class CompositeChangeBrowserSettings extends ChangeBrowserSettings {
    private final Map<AbstractVcs, ChangeBrowserSettings> myMap;

    public CompositeChangeBrowserSettings(final Map<AbstractVcs, ChangeBrowserSettings> map) {
      myMap = map;
    }

    public void put(final AbstractVcs vcs, final ChangeBrowserSettings settings) {
      myMap.put(vcs, settings);
    }

    public ChangeBrowserSettings get(final AbstractVcs vcs) {
      return myMap.get(vcs);
    }
  }

  private class CompositeChangesBrowserSettingsEditor implements ChangesBrowserSettingsEditor<CompositeChangeBrowserSettings> {
    private JTabbedPane myTabbedPane = new JTabbedPane();
    private CompositeChangeBrowserSettings mySettings;
    private Map<AbstractVcs, ChangesBrowserSettingsEditor> myEditors = new HashMap<AbstractVcs, ChangesBrowserSettingsEditor>();

    public CompositeChangesBrowserSettingsEditor() {
      for(AbstractVcs vcs: myBaseVcss) {
        final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
        assert provider != null;
        final ChangesBrowserSettingsEditor editor = provider.createFilterUI();
        myEditors.put(vcs, editor);
        myTabbedPane.addTab(vcs.getDisplayName(), editor.getComponent());
      }
    }

    public JComponent getComponent() {
      return myTabbedPane;
    }

    public CompositeChangeBrowserSettings getSettings() {
      for(AbstractVcs vcs: myEditors.keySet()) {
        ChangeBrowserSettings settings = myEditors.get(vcs).getSettings();
        mySettings.put(vcs, settings);
      }
      return mySettings;
    }

    public void setSettings(CompositeChangeBrowserSettings settings) {
      mySettings = settings;
      for(AbstractVcs vcs: myEditors.keySet()) {
        //noinspection unchecked
        myEditors.get(vcs).setSettings(mySettings.get(vcs));
      }
    }

    @Nullable
    public String validateInput() {
      for(ChangesBrowserSettingsEditor editor: myEditors.values()) {
        String result = editor.validateInput();
        if (result != null) return result;
      }
      return null;
    }
  }
}