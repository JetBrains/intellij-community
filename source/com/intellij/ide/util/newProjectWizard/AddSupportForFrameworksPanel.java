/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.newProjectWizard;

import com.intellij.facet.impl.ui.FacetTypeFrameworkSupportProvider;
import com.intellij.facet.impl.ui.libraries.*;
import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.facet.ui.libraries.RemoteRepositoryInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class AddSupportForFrameworksPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.newProjectWizard.AddSupportForFrameworksStep");
  private static final int INDENT = 20;
  private static final int SPACE_AFTER_TITLE = 5;
  private static final int VERTICAL_SPACE = 5;
  private JPanel myMainPanel;
  private JPanel myFrameworksTreePanel;
  private JButton myChangeButton;
  private JPanel myDownloadingOptionsPanel;
  private List<FrameworkSupportSettings> myRoots;
  private final LibrariesContainer myLibrariesContainer;
  private final Computable<String> myBaseDirForLibrariesGetter;
  private final List<FrameworkSupportProvider> myProviders;
  private final LibraryDownloadingMirrorsMap myMirrorsMap;

  public AddSupportForFrameworksPanel(final List<FrameworkSupportProvider> providers, final @NotNull LibrariesContainer librariesContainer,
                                      Computable<String> baseDirForLibrariesGetter) {
    myLibrariesContainer = librariesContainer;
    myBaseDirForLibrariesGetter = baseDirForLibrariesGetter;
    myProviders = providers;
    createNodes();
    myMirrorsMap = creatMirrorsMap();

    final JPanel treePanel = new JPanel(new GridBagLayout());
    addSettingsComponents(myRoots, treePanel, 0);
    myFrameworksTreePanel.add(treePanel, BorderLayout.WEST);
    myChangeButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final List<LibraryCompositionSettings> compositionSettingsList = getLibrariesCompositionSettingsList();
        new LibrariesCompositionDialog(myMainPanel, myLibrariesContainer, compositionSettingsList, myMirrorsMap).show();
        updateDownloadingOptionsPanel();
      }
    });
    updateDownloadingOptionsPanel();
  }

  private LibraryDownloadingMirrorsMap creatMirrorsMap() {
    List<RemoteRepositoryInfo> repositoryInfos = getRemoteRepositories();
    return new LibraryDownloadingMirrorsMap(repositoryInfos.toArray(new RemoteRepositoryInfo[repositoryInfos.size()]));
  }

  private List<RemoteRepositoryInfo> getRemoteRepositories() {
    List<RemoteRepositoryInfo> repositoryInfos = new ArrayList<RemoteRepositoryInfo>();
    List<FrameworkSupportSettings> frameworksSettingsList = getFrameworksSettingsList(false);
    for (FrameworkSupportSettings settings : frameworksSettingsList) {
      LibraryInfo[] libraries = settings.getConfigurable().getLibraries();
      for (LibraryInfo library : libraries) {
        LibraryDownloadInfo downloadInfo = library.getDownloadingInfo();
        if (downloadInfo != null) {
          RemoteRepositoryInfo repository = downloadInfo.getRemoteRepository();
          if (repository != null) {
            repositoryInfos.add(repository);
          }
        }
      }
    }
    return repositoryInfos;
  }

  private void updateDownloadingOptionsPanel() {
    @NonNls String card = getLibrariesCompositionSettingsList().isEmpty() ? "empty" : "options";
    ((CardLayout)myDownloadingOptionsPanel.getLayout()).show(myDownloadingOptionsPanel, card);
  }

  private List<LibraryCompositionSettings> getLibrariesCompositionSettingsList() {
    List<LibraryCompositionSettings> list = new ArrayList<LibraryCompositionSettings>();
    List<FrameworkSupportSettings> selected = getFrameworksSettingsList(true);
    for (FrameworkSupportSettings settings : selected) {
      LibraryInfo[] libraries = settings.getConfigurable().getLibraries();
      if (libraries.length > 0) {
        list.add(settings.getLibraryCompositionSettings());
      }
    }
    return list;
  }

  public boolean downloadLibraries() {
    List<LibraryCompositionSettings> list = getLibrariesCompositionSettingsList();
    for (LibraryCompositionSettings compositionSettings : list) {
      if (!compositionSettings.downloadFiles(myMirrorsMap, myLibrariesContainer, myMainPanel)) return false;
    }
    return true;
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
    return new GridBagConstraints(gridx, gridy, gridwidth, gridheight, 1, 1, GridBagConstraints.NORTHWEST,
                                                                        GridBagConstraints.NONE, insets, 0, 0);
  }

  private void createNodes() {
    Map<String, FrameworkSupportSettings> nodes = new HashMap<String, FrameworkSupportSettings>();
    for (FrameworkSupportProvider frameworkSupport : myProviders) {
      createNode(frameworkSupport, nodes);
    }

    myRoots = new ArrayList<FrameworkSupportSettings>();
    for (FrameworkSupportSettings settings : nodes.values()) {
      if (settings.getParentNode() == null) {
        myRoots.add(settings);
      }
    }

    DFSTBuilder<FrameworkSupportProvider> builder = new DFSTBuilder<FrameworkSupportProvider>(GraphGenerator.create(CachingSemiGraph.create(new ProvidersGraph(myProviders))));
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

  private String getBaseModuleDirectoryPath() {
    return myBaseDirForLibrariesGetter.compute();
  }

  @Nullable
  private FrameworkSupportProvider findProvider(@NotNull String id) {
    for (FrameworkSupportProvider provider : myProviders) {
      if (id.equals(provider.getId())) {
        return provider;
      }
    }
    LOG.info("Cannot find framework support provider '" + id + "'");
    return null;
  }

  private static void setDescendantsEnabled(FrameworkSupportSettings frameworkSupport, final boolean enable) {
    for (FrameworkSupportSettings child : frameworkSupport.myChildren) {
      child.setEnabled(enable);
      setDescendantsEnabled(child, enable);
    }
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }

  private List<FrameworkSupportSettings> getFrameworksSettingsList(final boolean selectedOnly) {
    ArrayList<FrameworkSupportSettings> list = new ArrayList<FrameworkSupportSettings>();
    if (myRoots != null) {
      addChildFrameworks(myRoots, list, selectedOnly);
    }
    return list;
  }

  private static void sortByTitle(List<FrameworkSupportProvider> providers) {
    Collections.sort(providers, new Comparator<FrameworkSupportProvider>() {
      public int compare(final FrameworkSupportProvider o1, final FrameworkSupportProvider o2) {
        return getTitleWithoutMnemonic(o2).compareTo(getTitleWithoutMnemonic(o1));
      }
    });
  }

  private static String getTitleWithoutMnemonic(final FrameworkSupportProvider provider) {
    return StringUtil.replace(provider.getTitle(), String.valueOf(UIUtil.MNEMONIC), "");
  }

  private static void addChildFrameworks(final List<FrameworkSupportSettings> list, final ArrayList<FrameworkSupportSettings> selected,
                                         final boolean selectedOnly) {
    for (FrameworkSupportSettings settings : list) {
      if (!selectedOnly || settings.myCheckBox.isSelected()) {
        selected.add(settings);
        addChildFrameworks(settings.myChildren, selected, selectedOnly);
      }
    }
  }

  public void addSupport(final Module module, final ModifiableRootModel rootModel) {
    List<Library> addedLibraries = new ArrayList<Library>();
    List<FrameworkSupportSettings> selectedFrameworks = getFrameworksSettingsList(true);
    for (FrameworkSupportSettings settings : selectedFrameworks) {
      FrameworkSupportConfigurable configurable = settings.getConfigurable();

      Library library = settings.getLibraryCompositionSettings().addLibraries(rootModel, addedLibraries);

      configurable.addSupport(module, rootModel, library);
    }
    for (FrameworkSupportSettings settings : selectedFrameworks) {
      FrameworkSupportProvider provider = settings.myProvider;
      if (provider instanceof FacetTypeFrameworkSupportProvider) {
        ((FacetTypeFrameworkSupportProvider)provider).processAddedLibraries(module, addedLibraries);
      }
    }
  }

  public class FrameworkSupportSettings {
    private final FrameworkSupportProvider myProvider;
    private final FrameworkSupportSettings myParentNode;
    private final FrameworkSupportConfigurable myConfigurable;
    private final JCheckBox myCheckBox;
    private final List<FrameworkSupportSettings> myChildren = new ArrayList<FrameworkSupportSettings>();
    private LibraryCompositionSettings myLibraryCompositionSettings;

    private FrameworkSupportSettings(final FrameworkSupportProvider provider, final FrameworkSupportSettings parentNode) {
      myProvider = provider;
      myParentNode = parentNode;
      myConfigurable = provider.createConfigurable(myLibrariesContainer.getProject());
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

    public String getModuleDirectoryPath() {
      return getBaseModuleDirectoryPath();
    }

    private boolean isObsolete(@NotNull LibraryCompositionSettings settings) {
      return !settings.getBaseDirectoryForDownloadedFiles().equals(getBaseModuleDirectoryPath())
             || !Comparing.equal(settings.getLibraryInfos(), myConfigurable.getLibraries());
    }

    public LibraryCompositionSettings getLibraryCompositionSettings() {
      if (myLibraryCompositionSettings == null || isObsolete(myLibraryCompositionSettings)) {
        final String title = getTitleWithoutMnemonic(myProvider);
        myLibraryCompositionSettings = new LibraryCompositionSettings(myConfigurable.getLibraries(), myConfigurable.getLibraryName(), getBaseModuleDirectoryPath(),
                                                                      title, myProvider.getIcon());
      }
      return myLibraryCompositionSettings;
    }
  }

  private class ProvidersGraph implements GraphGenerator.SemiGraph<FrameworkSupportProvider> {
    private final List<FrameworkSupportProvider> myFrameworkSupportProviders;

    public ProvidersGraph(final List<FrameworkSupportProvider> frameworkSupportProviders) {
      myFrameworkSupportProviders = new ArrayList<FrameworkSupportProvider>(frameworkSupportProviders);
      sortByTitle(myFrameworkSupportProviders);
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
      sortByTitle(dependencies);
      return dependencies.iterator();
    }
  }
}
