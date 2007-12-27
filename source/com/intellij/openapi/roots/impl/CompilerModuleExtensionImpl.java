/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class CompilerModuleExtensionImpl extends CompilerModuleExtension {
  @NonNls private static final String OUTPUT_TAG = "output";
  @NonNls private static final String TEST_OUTPUT_TAG = "output-test";
  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String INHERIT_COMPILER_OUTPUT = "inherit-compiler-output";

  private String myCompilerOutput;
  private VirtualFilePointer myCompilerOutputPointer;

  private String myCompilerOutputForTests;
  private VirtualFilePointer myCompilerOutputPathForTestsPointer;

  private boolean myInheritedCompilerOutput;
  private Module myModule;


  public CompilerModuleExtensionImpl(final Module module) {
    myModule = module;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    myCompilerOutputPointer = getOutputPathValue(element, OUTPUT_TAG, !myInheritedCompilerOutput);
    myCompilerOutput = getOutputPathValue(element, OUTPUT_TAG);

    myCompilerOutputPathForTestsPointer = getOutputPathValue(element, TEST_OUTPUT_TAG, !myInheritedCompilerOutput);
    myCompilerOutputForTests = getOutputPathValue(element, TEST_OUTPUT_TAG);

    final String value = element.getAttributeValue(INHERIT_COMPILER_OUTPUT);
    myInheritedCompilerOutput = value != null && Boolean.parseBoolean(value);
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

  public void setCompilerOutputPath(VirtualFile file) {
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
    return VirtualFilePointerManager.getInstance().create(file, listener);
  }

  private VirtualFilePointer createPointer(final String url) {
    final VirtualFilePointerListener listener =
      ((ProjectRootManagerImpl)ProjectRootManager.getInstance(myModule.getProject())).getVirtualFilePointerListener();
    return VirtualFilePointerManager.getInstance().create(url, listener);
  }

  public void setCompilerOutputPath(String url) {
    if (url != null) {
      myCompilerOutputPointer = createPointer(url);
      myCompilerOutput = url;
    }
    else {
      myCompilerOutputPointer = null;
    }
  }

  public void setCompilerOutputPathForTests(VirtualFile file) {
    if (file != null) {
      myCompilerOutputPathForTestsPointer = createPointer(file);
      myCompilerOutputForTests = file.getUrl();
    }
    else {
      myCompilerOutputPathForTestsPointer = null;
    }
  }

  public void setCompilerOutputPathForTests(String url) {
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

  public void inheritCompilerOutputPath(boolean inherit) {
    myInheritedCompilerOutput = inherit;
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

}