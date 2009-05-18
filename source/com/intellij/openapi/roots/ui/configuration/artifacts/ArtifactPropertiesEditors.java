package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public class ArtifactPropertiesEditors {
  private Map<String, JPanel> myMainPanels;
  private final PackagingEditorContext myContext;
  private final Artifact myOriginalArtifact;
  private List<PropertiesEditorInfo> myEditors;

  public ArtifactPropertiesEditors(PackagingEditorContext context, Artifact originalArtifact) {
    myContext = context;
    myOriginalArtifact = originalArtifact;
    myMainPanels = new HashMap<String, JPanel>();
    myEditors = new ArrayList<PropertiesEditorInfo>();
    for (ArtifactPropertiesProvider provider : originalArtifact.getPropertiesProviders()) {
      final PropertiesEditorInfo editorInfo = new PropertiesEditorInfo(provider);
      myEditors.add(editorInfo);
      final String tabName = editorInfo.myEditor.getTabName();
      JPanel panel = myMainPanels.get(tabName);
      if (panel == null) {
        panel = new JPanel(new VerticalFlowLayout());
        myMainPanels.put(tabName, panel);
      }
      panel.add(editorInfo.myEditor.createComponent());
    }
  }

  public void applyProperties() {
    for (PropertiesEditorInfo editor : myEditors) {
      if (editor.isModified()) {
        editor.applyProperties();
      }
    }
  }

  public void addTabs(TabbedPaneWrapper tabbedPane) {
    List<String> sortedTabs = new ArrayList<String>(myMainPanels.keySet());
    Collections.sort(sortedTabs);
    for (String tab : sortedTabs) {
      tabbedPane.addTab(tab, myMainPanels.get(tab));
    }
  }

  public boolean isModified() {
    for (PropertiesEditorInfo editor : myEditors) {
      if (editor.isModified()) {
        return true;
      }
    }
    return false;
  }

  private class PropertiesEditorInfo {
    private ArtifactPropertiesEditor myEditor;
    private ArtifactProperties<?> myProperties;
    private ArtifactPropertiesProvider myProvider;

    private PropertiesEditorInfo(ArtifactPropertiesProvider provider) {
      myProvider = provider;
      myProperties = provider.createProperties(myOriginalArtifact.getArtifactType());
      ArtifactUtil.copyProperties(myOriginalArtifact.getProperties(provider), myProperties);
      myEditor = myProperties.createEditor(myContext);
      myEditor.reset();
    }

    public void applyProperties() {
      myEditor.apply();
      final ModifiableArtifact artifact = myContext.getModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact);
      artifact.setProperties(myProvider, myProperties);
    }

    public boolean isModified() {
      return myEditor.isModified();
    }
  }
}
