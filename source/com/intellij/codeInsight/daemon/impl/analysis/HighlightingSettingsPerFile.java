package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.lang.Language;
import org.jdom.Element;

import java.util.*;

public class HighlightingSettingsPerFile implements JDOMExternalizable, ModuleComponent{
  private Map<VirtualFile, FileHighlighingSetting[]> myHighlightSettings = new HashMap<VirtualFile, FileHighlighingSetting[]>();
  private Map<Language, FileHighlighingSetting[]> myHighlightDefaults = new HashMap<Language, FileHighlighingSetting[]>();

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

    FileHighlighingSetting[] defaults = myHighlightSettings.get(containingFile.getVirtualFile());
    if(defaults == null) defaults = getDefaults(containingFile.getLanguage()).clone();
    defaults[PsiUtil.getRootIndex(root)] = setting;
    boolean toRemove = true;
    for (FileHighlighingSetting aDefault : defaults) {
      if (aDefault != FileHighlighingSetting.NONE) toRemove = false;
    }
    if(!toRemove) myHighlightSettings.put(containingFile.getVirtualFile(), defaults);
    else myHighlightSettings.remove(containingFile.getVirtualFile());
  }

  public void projectOpened() {}
  public void projectClosed() {}
  public void moduleAdded() {}
  public String getComponentName() {
    return "HighlightingSettingsPerFile";
  }

  public void initComponent() {}
  public void disposeComponent() {}

  public void readExternal(Element element) throws InvalidDataException {
    final List children = element.getChildren("file");
    for (final Object aChildren : children) {
      final Element child = (Element)aChildren;
      final VirtualFile fileByUrl = VirtualFileManager.getInstance().findFileByUrl(child.getAttributeValue("uri"));
      final List<FileHighlighingSetting> settings = new ArrayList<FileHighlighingSetting>();
      int index = 0;
      while (child.getAttributeValue("root" + index) != null) {
        final String attributeValue = child.getAttributeValue("root" + index);
        settings.add(Enum.valueOf(FileHighlighingSetting.class, attributeValue));
      }
      myHighlightSettings.put(fileByUrl, settings.toArray(new FileHighlighingSetting[settings.size()]));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (myHighlightSettings.isEmpty()) throw new WriteExternalException();
    for (Map.Entry<VirtualFile, FileHighlighingSetting[]> entry : myHighlightSettings.entrySet()) {
      final Element child = new Element("setting");
      element.addContent(child);
      child.setAttribute("file", entry.getKey().getUrl());
      for (int i = 0; i < entry.getValue().length; i++) {
        final FileHighlighingSetting fileHighlighingSetting = entry.getValue()[i];
        child.setAttribute("root" + i, fileHighlighingSetting.toString());
      }
    }
  }
}
