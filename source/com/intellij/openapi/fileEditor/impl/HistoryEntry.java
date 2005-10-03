package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class HistoryEntry{
  @NonNls public static final String TAG = "entry";

  public final VirtualFile myFile;
  /**
   * can be null when read from XML
   */ 
  public FileEditorProvider mySelectedProvider;
  private final HashMap myProvider2State;
  @NonNls public static final String FILE_ATTR = "file";
  @NonNls public static final String PROVIDER_ATTR = "provider";
  @NonNls public static final String EDITOR_TYPE_ID_ATTR = "editor-type-id";
  @NonNls public static final String SELECTED_ATTR_VALUE = "selected";
  @NonNls public static final String STATE_ELEMENT = "state";

  public HistoryEntry(VirtualFile file, FileEditorProvider[] providers, FileEditorState[] states, FileEditorProvider selectedProvider){
    if (file == null){
      throw new IllegalArgumentException("file cannot be null");
    }
    if (providers == null){
      throw new IllegalArgumentException("providers cannot be null");
    }
    if (states == null){
      throw new IllegalArgumentException("states cannot be null");
    }
    if (selectedProvider == null){
      throw new IllegalArgumentException("selectedProvider cannot be null");
    }
    myFile = file;
    myProvider2State = new HashMap();
    mySelectedProvider = selectedProvider;
    for (int i = 0; i < providers.length; i++) {
      putState(providers[i], states[i]);
    }
  }

  public HistoryEntry(Project project, Element e) throws InvalidDataException{
    if (!e.getName().equals(TAG)) {
      throw new IllegalArgumentException("unexpected tag: " + e);
    }

    String url = e.getAttributeValue(FILE_ATTR);
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file == null){
      throw new InvalidDataException();
    }

    myFile = file;
    myProvider2State = new HashMap();

    List providers = e.getChildren(PROVIDER_ATTR);
    for (Iterator iterator = providers.iterator(); iterator.hasNext();) {
      Element _e = (Element)iterator.next();

      String typeId = _e.getAttributeValue(EDITOR_TYPE_ID_ATTR);
      FileEditorProvider provider = FileEditorProviderManager.getInstance().getProvider(typeId);
      if (provider == null){
        continue;
      }
      if (Boolean.valueOf(_e.getAttributeValue(SELECTED_ATTR_VALUE))) {
        mySelectedProvider = provider;
      }

      Element stateElement = _e.getChild(STATE_ELEMENT);
      if (stateElement == null){
        throw new InvalidDataException();
      }

      FileEditorState state = provider.readState(stateElement, project, file);
      putState(provider, state);
    }
  }

  public FileEditorState getState(FileEditorProvider provider) {
    if (provider == null){
      throw new IllegalArgumentException("provider cannot be null");
    }
    return (FileEditorState)myProvider2State.get(provider);
  }

  public void putState(FileEditorProvider provider, FileEditorState state) {
    if (provider == null){
      throw new IllegalArgumentException("provider cannot be null");
    }
    if (state == null){
      throw new IllegalArgumentException("state cannot be null");
    }
    myProvider2State.put(provider, state);
  }

  /**
   * @return element that was added to the <code>element</code>.
   * Returned element has tag {@link #TAG}. Never null.
   */
  public Element writeExternal(Element element, Project project) {
    Element e = new Element(TAG);
    element.addContent(e);
    e.setAttribute(FILE_ATTR, myFile.getUrl());

    Iterator i = myProvider2State.entrySet().iterator();
    while (i.hasNext()) {
      Map.Entry entry = (Map.Entry)i.next();
      FileEditorProvider provider = (FileEditorProvider)entry.getKey();

      Element providerElement = new Element(PROVIDER_ATTR);
      if (provider.equals(mySelectedProvider)) {
        providerElement.setAttribute(SELECTED_ATTR_VALUE, Boolean.TRUE.toString());
      }
      providerElement.setAttribute(EDITOR_TYPE_ID_ATTR, provider.getEditorTypeId());
      Element stateElement = new Element(STATE_ELEMENT);
      providerElement.addContent(stateElement);
      provider.writeState((FileEditorState)entry.getValue(), project, stateElement);

      e.addContent(providerElement);
    }

    return e;
  }
}
