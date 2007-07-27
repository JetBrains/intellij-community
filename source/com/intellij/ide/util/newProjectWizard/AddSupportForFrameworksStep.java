/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.ui.UIUtil;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.facet.impl.ui.libraries.RequiredLibrariesInfo;
import com.intellij.facet.impl.ui.libraries.LibraryDownloader;
import com.intellij.facet.impl.ui.FacetEditorContextBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class AddSupportForFrameworksStep extends ModuleWizardStep {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.newProjectWizard.AddSupportForFrameworksStep");
  private static final int INDENT = 20;
  private static final int SPACE_AFTER_TITLE = 5;
  private static final int VERTICAL_SPACE = 5;
  private JPanel myMainPanel;
  private JPanel myFrameworksTreePanel;
  private JButton myChangeButton;
  private JPanel myDownloadingOptionsPanel;
  private List<FrameworkSupportSettings> myRoots;
  private final ModuleBuilder myBuilder;

  public AddSupportForFrameworksStep(final ModuleBuilder builder) {
    myBuilder = builder;
    createNodes();

    final JPanel treePanel = new JPanel(new GridBagLayout());
    addSettingsComponents(myRoots, treePanel, 0);
    myFrameworksTreePanel.add(treePanel, BorderLayout.WEST);
    myBuilder.addModuleConfigurationUpdater(new MyModuleConfigurationUpdater());
    myChangeButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        new LibraryDownloadingSettingsDialog(myMainPanel, getFrameworkWithLibraries()).show();
        updateDownloadingOptionsPanel();
      }
    });
    updateDownloadingOptionsPanel();
  }

  private void updateDownloadingOptionsPanel() {
    @NonNls String card = getFrameworkWithLibraries().isEmpty() ? "empty" : "options";
    ((CardLayout)myDownloadingOptionsPanel.getLayout()).show(myDownloadingOptionsPanel, card);
  }

  private List<FrameworkSupportSettings> getFrameworkWithLibraries() {
    List<FrameworkSupportSettings> frameworkLibrariesInfos = new ArrayList<FrameworkSupportSettings>();
    List<FrameworkSupportSettings> selected = getSelectedFrameworks();
    for (FrameworkSupportSettings settings : selected) {
      LibraryInfo[] libraries = settings.getConfigurable().getLibraries();
      if (libraries.length > 0) {
        frameworkLibrariesInfos.add(settings);
      }
    }
    return frameworkLibrariesInfos;
  }

  public void _commit(final boolean finishChosen) throws CommitStepException {
    if (finishChosen) {
      List<FrameworkSupportSettings> list = getFrameworkWithLibraries();
      for (FrameworkSupportSettings settings : list) {
        if (settings.isDownloadLibraries()) {
          RequiredLibrariesInfo requiredLibraries = new RequiredLibrariesInfo(settings.getConfigurable().getLibraries());
          RequiredLibrariesInfo.RequiredClassesNotFoundInfo info = requiredLibraries.checkLibraries(settings.getAddedJars().toArray(new VirtualFile[settings.getAddedJars().size()]));
          if (info != null) {
            LibraryDownloadInfo[] downloadingInfos = LibraryDownloader.getDownloadingInfos(info.getLibraryInfos());
            if (downloadingInfos.length > 0) {
              String libraryName = settings.getConfigurable().getLibraryName();
              if (FrameworkSupportConfigurable.DEFAULT_LIBRARY_NAME.equals(libraryName)) {
                libraryName = null;
              }

              LibraryDownloader downloader = new LibraryDownloader(downloadingInfos, null, myMainPanel,
                                                                   settings.getDirectoryForDownloadedLibrariesPath(),
                                                                   libraryName);
              settings.myAddedJars.addAll(Arrays.asList(downloader.download()));
            }
          }
        }
      }
    }
  }

  public static boolean hasProviders(@NotNull ModuleType moduleType) {
    return !getProviders(moduleType).isEmpty();
  }

  private JPanel addSettingsComponents(final List<FrameworkSupportSettings> list, JPanel treePanel, int level) {
    for (FrameworkSupportSettings root : list) {
      addSettingsComponents(root, treePanel, level);
    }
    return treePanel;
  }

  private void addSettingsComponents(final FrameworkSupportSettings frameworkSupport, JPanel parentPanel, int level) {
    if (frameworkSupport.getParentNode() != null) {
      frameworkSupport.setEnabled(false);
    }
    JComponent configurableComponent = frameworkSupport.getConfigurable().getComponent();
    int gridwidth = configurableComponent != null ? 1 : GridBagConstraints.REMAINDER;
    parentPanel.add(frameworkSupport.myCheckBox, createConstraints(0, GridBagConstraints.RELATIVE, gridwidth, 1,
                                                                   new Insets(0, INDENT * level, VERTICAL_SPACE, SPACE_AFTER_TITLE)));
    if (configurableComponent != null) {
      parentPanel.add(configurableComponent, createConstraints(1, GridBagConstraints.RELATIVE, GridBagConstraints.REMAINDER, 1,
                                                               new Insets(0, 0, VERTICAL_SPACE, 0)));
    }

    if (frameworkSupport.myChildren.isEmpty()) {
      return;
    }

    addSettingsComponents(frameworkSupport.myChildren, parentPanel, level + 1);
  }

  private static GridBagConstraints createConstraints(final int gridx, final int gridy, final int gridwidth, final int gridheight,
                                               final Insets insets) {
    return new GridBagConstraints(gridx, gridy, gridwidth, gridheight, 1, 1, GridBagConstraints.WEST,
                                                                        GridBagConstraints.NONE, insets, 0, 0);
  }

  private void createNodes() {
    final List<FrameworkSupportProvider> frameworkSupportProviders = getProviders(myBuilder.getModuleType());
    Map<String, FrameworkSupportSettings> nodes = new HashMap<String, FrameworkSupportSettings>();
    for (FrameworkSupportProvider frameworkSupport : frameworkSupportProviders) {
      createNode(frameworkSupport, nodes);
    }

    myRoots = new ArrayList<FrameworkSupportSettings>();
    for (FrameworkSupportSettings settings : nodes.values()) {
      if (settings.getParentNode() == null) {
        myRoots.add(settings);
      }
    }

    DFSTBuilder<FrameworkSupportProvider> builder = new DFSTBuilder<FrameworkSupportProvider>(GraphGenerator.create(CachingSemiGraph.create(new ProvidersGraph(frameworkSupportProviders))));
    if (!builder.isAcyclic()) {
      Pair<FrameworkSupportProvider,FrameworkSupportProvider> pair = builder.getCircularDependency();
      LOG.error("Circular dependency between providers '" + pair.getFirst().getId() + "' and '" + pair.getSecond().getId() + "' was found.");
    }

    final Comparator<FrameworkSupportProvider> comparator = builder.comparator();
    sortNodes(myRoots, new Comparator<FrameworkSupportSettings>() {
      public int compare(final FrameworkSupportSettings o1, final FrameworkSupportSettings o2) {
        return comparator.compare(o1.getProvider(), o2.getProvider());
      }
    });
  }

  private static void sortNodes(final List<FrameworkSupportSettings> list, final Comparator<FrameworkSupportSettings> comparator) {
    Collections.sort(list, comparator);
    for (FrameworkSupportSettings frameworkSupportSettings : list) {
      sortNodes(frameworkSupportSettings.myChildren, comparator);
    }
  }

  private static List<FrameworkSupportProvider> getProviders(@NotNull ModuleType moduleType) {
    FrameworkSupportProvider[] providers = Extensions.getExtensions(FrameworkSupportProvider.EXTENSION_POINT);
    ArrayList<FrameworkSupportProvider> result = new ArrayList<FrameworkSupportProvider>();
    for (FrameworkSupportProvider provider : providers) {
      if (provider.isEnabledForModuleType(moduleType)) {
        result.add(provider);
      }
    }
    return result;
  }

  @Nullable
  private FrameworkSupportSettings createNode(final FrameworkSupportProvider provider, final Map<String, FrameworkSupportSettings> nodes) {
    FrameworkSupportSettings node = nodes.get(provider.getId());
    if (node == null) {
      String underlyingFrameworkId = provider.getUnderlyingFrameworkId();
      FrameworkSupportSettings parentNode = null;
      if (underlyingFrameworkId != null) {
        FrameworkSupportProvider parentProvider = findProvider(underlyingFrameworkId);
        if (parentProvider == null) {
          LOG.info("Cannot find id = " + underlyingFrameworkId);
          return null;
        }
        parentNode = createNode(parentProvider, nodes);
      }
      node = new FrameworkSupportSettings(provider, parentNode);
      nodes.put(provider.getId(), node);
    }
    return node;
  }

  private String getBaseModuleDirectory() {
    String path = null;
    if (myBuilder instanceof JavaModuleBuilder) {
      path = ((JavaModuleBuilder)myBuilder).getContentEntryPath();
    }
    if (path == null) {
      path = myBuilder.getModuleFileDirectory();
    }
    return path != null ? FileUtil.toSystemIndependentName(path) : "";
  }

  @Nullable
  private FrameworkSupportProvider findProvider(@NotNull String id) {
    for (FrameworkSupportProvider provider : getProviders(myBuilder.getModuleType())) {
      if (id.equals(provider.getId())) {
        return provider;
      }
    }
    LOG.info("Cannot find framework support provider '" + id + "'");
    return null;
  }

  public Icon getIcon() {
    return ICON;
  }

  private static void setDescendantsEnabled(FrameworkSupportSettings frameworkSupport, final boolean enable) {
    for (FrameworkSupportSettings child : frameworkSupport.myChildren) {
      child.setEnabled(enable);
      setDescendantsEnabled(child, enable);
    }
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  public void updateDataModel() {
  }

  private List<FrameworkSupportSettings> getSelectedFrameworks() {
    ArrayList<FrameworkSupportSettings> list = new ArrayList<FrameworkSupportSettings>();
    if (myRoots != null) {
      addSelectedFrameworks(myRoots, list);
    }
    return list;
  }

  private static void addSelectedFrameworks(final List<FrameworkSupportSettings> list, final ArrayList<FrameworkSupportSettings> selected) {
    for (FrameworkSupportSettings settings : list) {
      if (settings.myCheckBox.isSelected()) {
        selected.add(settings);
        addSelectedFrameworks(settings.myChildren, selected);
      }
    }
  }

  public class FrameworkSupportSettings {
    private final FrameworkSupportProvider myProvider;
    private final FrameworkSupportSettings myParentNode;
    private final FrameworkSupportConfigurable myConfigurable;
    private JCheckBox myCheckBox;
    private List<FrameworkSupportSettings> myChildren = new ArrayList<FrameworkSupportSettings>();
    private @NonNls String myDirectoryForDownloadedLibrariesPath;
    private List<VirtualFile> myAddedJars = new ArrayList<VirtualFile>();
    private boolean myDownloadLibraries = true;

    private FrameworkSupportSettings(final FrameworkSupportProvider provider, final FrameworkSupportSettings parentNode) {
      myProvider = provider;
      myParentNode = parentNode;
      myConfigurable = provider.createConfigurable();
      myCheckBox = new JCheckBox(provider.getTitle());
      if (parentNode != null) {
        parentNode.myChildren.add(this);
      }

      myCheckBox.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          setConfigurableComponentEnabled(myCheckBox.isSelected());
          setDescendantsEnabled(FrameworkSupportSettings.this, myCheckBox.isSelected());
        }
      });

      setConfigurableComponentEnabled(false);
    }

    public void setEnabled(final boolean enable) {
      myCheckBox.setEnabled(enable);
      if (!enable) {
        myCheckBox.setSelected(false);
        setConfigurableComponentEnabled(false);
      }
    }

    private void setConfigurableComponentEnabled(final boolean enable) {
      JComponent component = getConfigurable().getComponent();
      if (component != null) {
        UIUtil.setEnabled(component, enable, true);
      }
      updateDownloadingOptionsPanel();
    }

    public FrameworkSupportProvider getProvider() {
      return myProvider;
    }

    public FrameworkSupportSettings getParentNode() {
      return myParentNode;
    }

    public FrameworkSupportConfigurable getConfigurable() {
      return myConfigurable;
    }

    public List<VirtualFile> getAddedJars() {
      return myAddedJars;
    }

    public void setAddedJars(final List<VirtualFile> addedJars) {
      myAddedJars = addedJars;
    }

    public String getDirectoryForDownloadedLibrariesPath() {
      if (myDirectoryForDownloadedLibrariesPath == null) {
        myDirectoryForDownloadedLibrariesPath = getModuleDirectory() + "/lib";
      }
      return myDirectoryForDownloadedLibrariesPath;
    }

    public String getModuleDirectory() {
      return getBaseModuleDirectory();
    }

    public void setDirectoryForDownloadedLibrariesPath(final String directoryForDownloadedLibrariesPath) {
      myDirectoryForDownloadedLibrariesPath = directoryForDownloadedLibrariesPath;
    }

    public void setDownloadLibraries(final boolean downloadLibraries) {
      myDownloadLibraries = downloadLibraries;
    }

    public boolean isDownloadLibraries() {
      return myDownloadLibraries;
    }
  }

  private class MyModuleConfigurationUpdater extends ModuleBuilder.ModuleConfigurationUpdater {
    public void update(final Module module, final ModifiableRootModel rootModel) {
      for (FrameworkSupportSettings settings : getSelectedFrameworks()) {
        if (!settings.myAddedJars.isEmpty()) {
          VirtualFile[] roots = settings.myAddedJars.toArray(new VirtualFile[settings.myAddedJars.size()]);
          LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(module.getProject());
          Library library = FacetEditorContextBase.createLibraryInTable(settings.getConfigurable().getLibraryName(), roots, VirtualFile.EMPTY_ARRAY, table);
          rootModel.addLibraryEntry(library);
        }
        settings.getConfigurable().addSupport(module, rootModel);
      }
    }
  }

  private class ProvidersGraph implements GraphGenerator.SemiGraph<FrameworkSupportProvider> {
    private final List<FrameworkSupportProvider> myFrameworkSupportProviders;

    public ProvidersGraph(final List<FrameworkSupportProvider> frameworkSupportProviders) {
      myFrameworkSupportProviders = frameworkSupportProviders;
    }

    public Collection<FrameworkSupportProvider> getNodes() {
      return myFrameworkSupportProviders;
    }

    public Iterator<FrameworkSupportProvider> getIn(final FrameworkSupportProvider provider) {
      String[] ids = provider.getPrecedingFrameworkProviderIds();
      List<FrameworkSupportProvider> dependencies = new ArrayList<FrameworkSupportProvider>();
      String underlyingId = provider.getUnderlyingFrameworkId();
      if (underlyingId != null) {
        FrameworkSupportProvider underlyingProvider = findProvider(underlyingId);
        if (underlyingProvider != null) {
          dependencies.add(underlyingProvider);
        }
      }
      for (String id : ids) {
        FrameworkSupportProvider dependency = findProvider(id);
        if (dependency != null) {
          dependencies.add(dependency);
        }
      }
      return dependencies.iterator();
    }
  }
}
