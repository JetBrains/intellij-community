package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FileEditorProviderManagerImpl extends FileEditorProviderManager implements ApplicationComponent{
  private static final FileEditorProvider[] EMPTY_ARRAY=new FileEditorProvider[]{};

  private final ArrayList<FileEditorProvider> myProviders;
  private final ArrayList<FileEditorProvider> mySharedProviderList;

  public FileEditorProviderManagerImpl(FileEditorProvider[] providers) {
    myProviders = new ArrayList<FileEditorProvider>();
    mySharedProviderList = new ArrayList<FileEditorProvider>();

    for (int i = 0; i < providers.length; i++) {
      FileEditorProvider provider = providers[i];
      registerProvider(provider);
    }
  }

  public synchronized FileEditorProvider[] getProviders(Project project, VirtualFile file){
    if(file==null){
      throw new IllegalArgumentException("file cannot be null");
    }

    // Collect all possible editors
    mySharedProviderList.clear();
    boolean doNotShowTextEditor = false;
    for(int i = myProviders.size() -1 ; i >= 0; i--){
      FileEditorProvider provider=myProviders.get(i);
      if(provider.accept(project, file)){
        mySharedProviderList.add(provider);
        doNotShowTextEditor |= provider.getPolicy() == FileEditorPolicy.HIDE_DEFAULT_EDITOR;
      }
    }

    // Throw out default editors provider if necessary
    if(doNotShowTextEditor){
      for(int i = mySharedProviderList.size() - 1; i >= 0; i--){
        if(mySharedProviderList.get(i) instanceof TextEditorProvider){
          mySharedProviderList.remove(i);
        }
      }
    }

    // Sort editors according policies
    Collections.sort(myProviders, MyComparator.ourInstance);

    if(mySharedProviderList.size()>0){
      return mySharedProviderList.toArray(new FileEditorProvider[mySharedProviderList.size()]);
    }
    else{
      return EMPTY_ARRAY;
    }
  }

  public synchronized FileEditorProvider getProvider(String editorTypeId){
    if (editorTypeId == null){
      throw new IllegalArgumentException("editorTypeId cannot be null");
    }
    for(int i=myProviders.size()-1;i>=0;i--){
      FileEditorProvider provider=myProviders.get(i);
      if(provider.getEditorTypeId().equals(editorTypeId)){
        return provider;
      }
    }
    return null;
  }

  public String getComponentName(){
    return "resourceProviderManager";
  }

  public void initComponent(){
  }

  private void registerProvider(FileEditorProvider provider) {
    String editorTypeId = provider.getEditorTypeId();
    for(int i=myProviders.size()-1;i>=0;i--){
      FileEditorProvider _provider=myProviders.get(i);
      if(editorTypeId.equals(_provider.getEditorTypeId())){
        throw new IllegalArgumentException(
          "attempt to register provider with non unique editorTypeId: "+_provider.getEditorTypeId()
        );
      }
    }
    myProviders.add(provider);
  }

  public void disposeComponent(){}

  private static final class MyComparator implements Comparator<FileEditorProvider>{
    public static final MyComparator ourInstance = new MyComparator();

    private MyComparator() {}

    public int compare(FileEditorProvider provider1, FileEditorProvider provider2) {
      if(provider1.getPolicy() == FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR){
        return provider2 instanceof TextEditorProvider ? -1 : 0;
      }
      else if(provider2.getPolicy() == FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR){
        return provider1 instanceof TextEditorProvider ? 1 : 0;
      }
      else{
        return 0;
      }
    }
  }
}
