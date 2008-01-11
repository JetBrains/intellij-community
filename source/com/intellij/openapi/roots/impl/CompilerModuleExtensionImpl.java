/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class CompilerModuleExtensionImpl extends CompilerModuleExtension {
  @NonNls private static final String OUTPUT_TAG = "output";
  @NonNls private static final String TEST_OUTPUT_TAG = "output-test";
  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String INHERIT_COMPILER_OUTPUT = "inherit-compiler-output";
  @NonNls private static final String EXCLUDE_OUTPUT_TAG = "exclude-output";

  private String myCompilerOutput;
  private VirtualFilePointer myCompilerOutputPointer;

  private String myCompilerOutputForTests;
  private VirtualFilePointer myCompilerOutputPathForTestsPointer;

  private boolean myInheritedCompilerOutput;
  private boolean myExcludeOutput = true;
  private Module myModule;

  private CompilerModuleExtensionImpl mySource;
  private boolean myWritable;
  private static final Logger LOG = Logger.getInstance("#" + CompilerModuleExtensionImpl.class.getName());
  private ArrayList<VirtualFilePointer> myVirtualFilePointers = new ArrayList<VirtualFilePointer>();

  public CompilerModuleExtensionImpl(final Module module) {
    myModule = module;
  }

  public CompilerModuleExtensionImpl(final CompilerModuleExtensionImpl source, final boolean writable) {
    myWritable = writable;
    myCompilerOutput = source.myCompilerOutput;
    myCompilerOutputPointer = source.myCompilerOutputPointer;
    myCompilerOutputForTests = source.myCompilerOutputForTests;
    myCompilerOutputPathForTestsPointer = source.myCompilerOutputPathForTestsPointer;
    myInheritedCompilerOutput = source.myInheritedCompilerOutput;
    myExcludeOutput = source.myExcludeOutput;
    myModule = source.myModule;
    mySource = source;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    myCompilerOutputPointer = getOutputPathValue(element, OUTPUT_TAG, !myInheritedCompilerOutput);
    myCompilerOutput = getOutputPathValue(element, OUTPUT_TAG);

    myCompilerOutputPathForTestsPointer = getOutputPathValue(element, TEST_OUTPUT_TAG, !myInheritedCompilerOutput);
    myCompilerOutputForTests = getOutputPathValue(element, TEST_OUTPUT_TAG);

    final String value = element.getAttributeValue(INHERIT_COMPILER_OUTPUT);
    myInheritedCompilerOutput = value != null && Boolean.parseBoolean(value);
    myExcludeOutput = element.getChild(EXCLUDE_OUTPUT_TAG) != null;
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    if (myCompilerOutput != null) {
      final Element pathElement = new Element(OUTPUT_TAG);
      pathElement.setAttribute(ATTRIBUTE_URL, myCompilerOutput);
      element.addContent(pathElement);
    }
    if (myCompilerOutputForTests != null) {
      final Element pathElement = new Element(TEST_OUTPUT_TAG);
      pathElement.setAttribute(ATTRIBUTE_URL, myCompilerOutputForTests);
      element.addContent(pathElement);
    }
    element.setAttribute(INHERIT_COMPILER_OUTPUT, String.valueOf(myInheritedCompilerOutput));
    if (myExcludeOutput) {
      element.addContent(new Element(EXCLUDE_OUTPUT_TAG));
    }
  }

  @Nullable
  protected VirtualFilePointer getOutputPathValue(Element element, String tag, final boolean createPointer) {
    final Element outputPathChild = element.getChild(tag);
    VirtualFilePointer vptr = null;
    if (outputPathChild != null && createPointer) {
      String outputPath = outputPathChild.getAttributeValue(ATTRIBUTE_URL);
      vptr = createPointer(outputPath);
    }
    return vptr;
  }

  @Nullable
  protected static String getOutputPathValue(Element element, String tag) {
    final Element outputPathChild = element.getChild(tag);
    if (outputPathChild != null) {
      return outputPathChild.getAttributeValue(ATTRIBUTE_URL);
    }
    return null;
  }

  @Nullable
  public VirtualFile getCompilerOutputPath() {
    if (myInheritedCompilerOutput) {
      final VirtualFile projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutput();
      if (projectOutputPath == null) return null;
      return projectOutputPath.findFileByRelativePath(PRODUCTION + "/" + getModule().getName());
    }
    if (myCompilerOutputPointer == null) {
      return null;
    }
    else {
      return myCompilerOutputPointer.getFile();
    }
  }

  @Nullable
  public VirtualFile getCompilerOutputPathForTests() {
    if (myInheritedCompilerOutput) {
      final VirtualFile projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutput();
      if (projectOutputPath == null) return null;
      return projectOutputPath.findFileByRelativePath(TEST + "/" + getModule().getName());
    }
    if (myCompilerOutputPathForTestsPointer == null) {
      return null;
    }
    else {
      return myCompilerOutputPathForTestsPointer.getFile();
    }
  }

  @Nullable
  public String getCompilerOutputUrl() {
    if (myInheritedCompilerOutput) {
      final String projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutputUrl();
      if (projectOutputPath == null) return null;
      return projectOutputPath + "/" + PRODUCTION + "/" + getModule().getName();
    }
    if (myCompilerOutputPointer == null) {
      return null;
    }
    else {
      return myCompilerOutputPointer.getUrl();
    }
  }

  @Nullable
  public String getCompilerOutputUrlForTests() {
    if (myInheritedCompilerOutput) {
      final String projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutputUrl();
      if (projectOutputPath == null) return null;
      return projectOutputPath + "/" + TEST + "/" + getModule().getName();
    }
    if (myCompilerOutputPathForTestsPointer == null) {
      return null;
    }
    else {
      return myCompilerOutputPathForTestsPointer.getUrl();
    }
  }

  public void setCompilerOutputPath(final VirtualFile file) {
    assertWritable();
    if (file != null) {
      myCompilerOutputPointer = createPointer(file);
      myCompilerOutput = file.getUrl();
    }
    else {
      myCompilerOutputPointer = null;
    }
  }

  private VirtualFilePointer createPointer(final VirtualFile file) {
    final VirtualFilePointerListener listener =
      ((ProjectRootManagerImpl)ProjectRootManager.getInstance(myModule.getProject())).getVirtualFilePointerListener();
    final VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(file, listener);
    myVirtualFilePointers.add(pointer);
    return pointer;
  }

  private VirtualFilePointer createPointer(final String url) {
    final VirtualFilePointerListener listener =
      ((ProjectRootManagerImpl)ProjectRootManager.getInstance(myModule.getProject())).getVirtualFilePointerListener();
    final VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(url, listener);
    myVirtualFilePointers.add(pointer);
    return pointer;
  }

  public void setCompilerOutputPath(final String url) {
    assertWritable();
    if (url != null) {
      myCompilerOutputPointer = createPointer(url);
      myCompilerOutput = url;
    }
    else {
      myCompilerOutputPointer = null;
    }
  }

  public void setCompilerOutputPathForTests(final VirtualFile file) {
    assertWritable();
    if (file != null) {
      myCompilerOutputPathForTestsPointer = createPointer(file);
      myCompilerOutputForTests = file.getUrl();
    }
    else {
      myCompilerOutputPathForTestsPointer = null;
    }
  }

  public void setCompilerOutputPathForTests(final String url) {
    assertWritable();
    if (url != null) {
      myCompilerOutputPathForTestsPointer = createPointer(url);
      myCompilerOutputForTests = url;
    }
    else {
      myCompilerOutputPathForTestsPointer = null;
    }
  }

  public Module getModule() {
    return myModule;
  }

  public Project getProject() {
    return myModule.getProject();
  }

  public void inheritCompilerOutputPath(final boolean inherit) {
    assertWritable();
    myInheritedCompilerOutput = inherit;
  }

  private void assertWritable() {
    LOG.assertTrue(myWritable, "Writable model can be retrieved from writable ModifiableRootModel");
  }

  public boolean isCompilerOutputPathInherited() {
    return myInheritedCompilerOutput;
  }

  public VirtualFilePointer getCompilerOutputPointer() {
    return myCompilerOutputPointer;
  }

  public VirtualFilePointer getCompilerOutputForTestsPointer() {
    return myCompilerOutputPathForTestsPointer;
  }

  public void setExcludeOutput(final boolean exclude) {
    assertWritable();
    myExcludeOutput = exclude;
  }

  public boolean isExcludeOutput() {
    return myExcludeOutput;
  }

  public CompilerModuleExtension getModifiableModel(final boolean writable) {
    return new CompilerModuleExtensionImpl(this, writable);
  }

  public void commit() {
    if (mySource != null) {
      mySource.myCompilerOutput = myCompilerOutput;
      mySource.myCompilerOutputPointer = myCompilerOutputPointer;
      mySource.myCompilerOutputForTests = myCompilerOutputForTests;
      mySource.myCompilerOutputPathForTestsPointer = myCompilerOutputPathForTestsPointer;
      mySource.myInheritedCompilerOutput = myInheritedCompilerOutput;
      mySource.myExcludeOutput = myExcludeOutput;
      mySource.myModule = myModule;
    }
  }

  public boolean isChanged() {
    if (myInheritedCompilerOutput != mySource.myInheritedCompilerOutput) {
      return true;
    }

    if (!myInheritedCompilerOutput) {
      if (!vptrEqual(myCompilerOutputPointer, mySource.myCompilerOutputPointer)) {
        return true;
      }
      if (!vptrEqual(myCompilerOutputPathForTestsPointer, mySource.myCompilerOutputPathForTestsPointer)) {
        return true;
      }
    }

    if (myExcludeOutput != mySource.myExcludeOutput) return true;

    return false;
  }

  private static boolean vptrEqual(VirtualFilePointer p1, VirtualFilePointer p2) {
    if (p1 == null && p2 == null) return true;
    if (p1 == null || p2 == null) return false;
    return Comparing.equal(p1.getUrl(), p2.getUrl());
  }

  public void dispose() {
    if (myModule != null) {
      mySource = null;
      myCompilerOutput = null;
      myCompilerOutputForTests = null;
      final VirtualFilePointerListener listener =
        ((ProjectRootManagerImpl)ProjectRootManager.getInstance(myModule.getProject())).getVirtualFilePointerListener();
      final VirtualFilePointerManager filePointerManager = VirtualFilePointerManager.getInstance();
      for (VirtualFilePointer pointer : myVirtualFilePointers) {
        filePointerManager.kill(pointer, listener);
      }
      myVirtualFilePointers.clear();
      myModule = null;
    }
  }
}