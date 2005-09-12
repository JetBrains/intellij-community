package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HighlightingSettingsPerFile implements JDOMExternalizable, ProjectComponent {
  public static HighlightingSettingsPerFile getInstance(Project progect){
    return progect.getComponent(HighlightingSettingsPerFile.class);
  }

  private Map<VirtualFile, FileHighlighingSetting[]> myHighlightSettings = new HashMap<VirtualFile, FileHighlighingSetting[]>();
  private Map<Language, FileHighlighingSetting[]> myHighlightDefaults = new HashMap<Language, FileHighlighingSetting[]>();
  private Map<VirtualFile, Pair<String,Boolean>> myProfileSettings = new HashMap<VirtualFile, Pair<String,Boolean>>();

  public FileHighlighingSetting getHighlightingSettingForRoot(PsiElement root){
    final PsiFile containingFile = root.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    FileHighlighingSetting[] fileHighlighingSettings = myHighlightSettings.get(virtualFile);
    if(fileHighlighingSettings == null){
      fileHighlighingSettings = getDefaults(containingFile.getLanguage());
    }
    return fileHighlighingSettings[PsiUtil.getRootIndex(root)];
  }

  public FileHighlighingSetting[] getDefaults(Language lang){
    if(myHighlightDefaults.containsKey(lang)) return myHighlightDefaults.get(lang);
    final FileHighlighingSetting[] fileHighlighingSettings = new FileHighlighingSetting[PsiUtil.getRootsCount(lang)];
    for (int i = 0; i < fileHighlighingSettings.length; i++) {
      fileHighlighingSettings[i] = FileHighlighingSetting.FORCE_HIGHLIGHTING;
    }
    return fileHighlighingSettings;
  }

  public void setHighlightingSettingForRoot(PsiElement root, FileHighlighingSetting setting){
    final PsiFile containingFile = root.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return;
    FileHighlighingSetting[] defaults = myHighlightSettings.get(virtualFile);
    if(defaults == null) defaults = getDefaults(containingFile.getLanguage()).clone();
    defaults[PsiUtil.getRootIndex(root)] = setting;
    boolean toRemove = true;
    for (FileHighlighingSetting aDefault : defaults) {
      if (aDefault != FileHighlighingSetting.NONE) toRemove = false;
    }
    if(!toRemove) myHighlightSettings.put(virtualFile, defaults);
    else myHighlightSettings.remove(virtualFile);
  }

  public void projectOpened() {}
  public void projectClosed() {}
  public String getComponentName() {
    return "HighlightingSettingsPerFile";
  }

  public void initComponent() {}
  public void disposeComponent() {}

  public void readExternal(Element element) throws InvalidDataException {
    List children = element.getChildren("setting");
    for (final Object aChildren : children) {
      final Element child = (Element)aChildren;
      final String url = child.getAttributeValue("file");
      if (url == null) continue;
      final VirtualFile fileByUrl = VirtualFileManager.getInstance().findFileByUrl(url);
      if (fileByUrl != null) {
        final List<FileHighlighingSetting> settings = new ArrayList<FileHighlighingSetting>();
        int index = 0;
        while (child.getAttributeValue("root" + index) != null) {
          final String attributeValue = child.getAttributeValue("root" + index++);
          settings.add(Enum.valueOf(FileHighlighingSetting.class, attributeValue));
        }
        myHighlightSettings.put(fileByUrl, settings.toArray(new FileHighlighingSetting[settings.size()]));
      }
    }
    children = element.getChildren("profiles");
    for (final Object aChildren : children) {
      final Element child = (Element)aChildren;
      final VirtualFile fileByUrl = VirtualFileManager.getInstance().findFileByUrl(child.getAttributeValue("file"));
      if (fileByUrl != null) {
        final String isActive = child.getAttributeValue("is_active");
        final Boolean second = isActive != null ? Boolean.valueOf(isActive) : Boolean.TRUE;
        myProfileSettings.put(fileByUrl, Pair.create(child.getAttributeValue("profile_name"), second));
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (myHighlightSettings.isEmpty()) throw new WriteExternalException();
    for (Map.Entry<VirtualFile, FileHighlighingSetting[]> entry : myHighlightSettings.entrySet()) {
      final Element child = new Element("setting");

      final VirtualFile vFile = entry.getKey();
      if (!vFile.isValid()) continue;
      child.setAttribute("file", vFile.getUrl());
      for (int i = 0; i < entry.getValue().length; i++) {
        final FileHighlighingSetting fileHighlighingSetting = entry.getValue()[i];
        child.setAttribute("root" + i, fileHighlighingSetting.toString());
      }
      element.addContent(child);
    }
    for (Map.Entry<VirtualFile, Pair<String,Boolean>> entry : myProfileSettings.entrySet()) {
      final Pair<String, Boolean> value = entry.getValue();
      if (value != null) {
        final String name = value.first;
        final Element child = new Element("profiles");
        final VirtualFile vFile = entry.getKey();
        if (vFile == null || !vFile.isValid()) continue;
        child.setAttribute("file", vFile.getUrl());
        child.setAttribute("profile_name", name);
        child.setAttribute("is_active", value.second.toString());
        element.addContent(child);
      }
    }
  }

  @Nullable
  public Pair<String, Boolean> getInspectionProfile(PsiElement psiRoot) {
    final PsiFile containingFile = psiRoot.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    return myProfileSettings.get(virtualFile);
  }

  public void setInspectionProfile(String name, boolean isActive, PsiElement psiRoot){
     if (psiRoot != null){
       final PsiFile file = psiRoot.getContainingFile();
       final VirtualFile vFile = file.getVirtualFile();
       if (vFile != null) {
         myProfileSettings.put(vFile, Pair.create(name, Boolean.valueOf(isActive)));
       }
     }
  }

  public void resetAllFilesToUseGlobalSettings(){
    myProfileSettings.clear();
    myHighlightSettings.clear();
  }
}
