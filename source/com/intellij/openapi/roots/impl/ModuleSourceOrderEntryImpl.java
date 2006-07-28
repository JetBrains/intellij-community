package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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

  @NonNls static final String ENTRY_TYPE = "sourceFolder";
  @NonNls private static final String ATTRIBUTE_FOR_TESTS = "forTests";

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
    element.setAttribute(ATTRIBUTE_FOR_TESTS, Boolean.FALSE.toString()); // compatibility with prev builds
    rootElement.addContent(element);
  }

  public boolean isValid() {
    return !isDisposed();
  }

  public Module getOwnerModule() {
    return myRootModel.getModule();
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitModuleSourceOrderEntry(this, initialValue);
  }

  public String getPresentableName() {
    return ProjectBundle.message("project.root.module.source");
  }

  void addExportedFiles(OrderRootType type, List<VirtualFile> result) {
    result.addAll(Arrays.asList(getFiles(type)));
  }

  void addExportedUrls(OrderRootType type, List<String> result) {
    result.addAll(Arrays.asList(getUrls(type)));
  }


  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
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
    return result.toArray(new VirtualFile[result.size()]);
  }

  @NotNull
  public String[] getUrls(OrderRootType type) {
    final ArrayList<String> result = new ArrayList<String>();
    if (OrderRootType.SOURCES.equals(type)) {
      final ContentEntry[] content = myRootModel.getContentEntries();
      for (ContentEntry contentEntry : content) {
        final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (SourceFolder sourceFolder : sourceFolders) {
          final String url = sourceFolder.getUrl();
          result.add(url);
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
    return result.toArray(new String[result.size()]);

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
