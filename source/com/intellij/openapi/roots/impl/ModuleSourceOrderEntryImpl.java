package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *  @author dsl
 */
public class ModuleSourceOrderEntryImpl extends OrderEntryBaseImpl implements ModuleSourceOrderEntry,
                                                                                  WritableOrderEntry,
                                                                                  ClonableOrderEntry {
  private final RootModelImpl myRootModel;

  static final String ENTRY_TYPE = "sourceFolder";

  ModuleSourceOrderEntryImpl(RootModelImpl rootModel) {
    super(rootModel);
    myRootModel = rootModel;
  }

  ModuleSourceOrderEntryImpl(Element element, RootModelImpl rootModel) throws InvalidDataException {
    super(rootModel);
    if (!element.getName().equals(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME)) {
      throw new InvalidDataException();
    }
    myRootModel = rootModel;
  }

  public void writeExternal(Element rootElement) throws WriteExternalException {
    Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    element.setAttribute(OrderEntryFactory.ORDER_ENTRY_TYPE_ATTR, ENTRY_TYPE);
    element.setAttribute("forTests", "false"); // compatibility with prev builds
    rootElement.addContent(element);
  }

  public boolean isValid() {
    return true;
  }

  public Module getOwnerModule() {
    return myRootModel.getModule();
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitModuleSourceOrderEntry(this, initialValue);
  }

  public String getPresentableName() {
    return "<Module source>";
  }

  void addExportedFiles(OrderRootType type, List<VirtualFile> result) {
    result.addAll(Arrays.asList(getFiles(type)));
  }

  void addExportedUrls(OrderRootType type, List<String> result) {
    result.addAll(Arrays.asList(getUrls(type)));
  }


  public VirtualFile[] getFiles(OrderRootType type) {
    final ArrayList result = new ArrayList();
    if (OrderRootType.SOURCES.equals(type)) {
      result.addAll(Arrays.asList(myRootModel.getSourceRoots()));
    }
    else if (OrderRootType.CLASSES_AND_OUTPUT.equals(type) || OrderRootType.COMPILATION_CLASSES.equals(type)) {
      VirtualFile outputRoot = myRootModel.getCompilerOutputPath();
      if (outputRoot != null) result.add(outputRoot);
      final VirtualFile outputPathForTests = myRootModel.getCompilerOutputPathForTests();
      if (outputPathForTests != null && !outputPathForTests.equals(outputRoot)) {
        result.add(outputPathForTests);
      }
    }
    return (VirtualFile[])result.toArray(new VirtualFile[result.size()]);
  }

  public VirtualFilePointer[] getFilePointers(OrderRootType type) {
    return new VirtualFilePointer[0];
  }

  public String[] getUrls(OrderRootType type) {
    final ArrayList result = new ArrayList();
    if (OrderRootType.SOURCES.equals(type)) {
      final ContentEntry[] content = myRootModel.getContentEntries();
      for (int i = 0; i < content.length; i++) {
        ContentEntry contentEntry = content[i];
        final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (int j = 0; j < sourceFolders.length; j++) {
          final String url = sourceFolders[j].getUrl();
          if (url != null) result.add(url);
        }
      }
    }
    else if (OrderRootType.CLASSES_AND_OUTPUT.equals(type) || OrderRootType.COMPILATION_CLASSES.equals(type)) {
      String outputRoot = myRootModel.getCompilerOutputPathUrl();
      if (outputRoot != null) result.add(outputRoot);
      final String outputPathForTests = myRootModel.getCompilerOutputUrlForTests();
      if (outputPathForTests != null && !outputPathForTests.equals(outputRoot)) {
        result.add(outputPathForTests);
      }
    }
    return (String[])result.toArray(new String[result.size()]);

  }

  public OrderEntry cloneEntry(RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    return new ModuleSourceOrderEntryImpl(rootModel);
  }

  public boolean isSynthetic() {
    return true;
  }
}
