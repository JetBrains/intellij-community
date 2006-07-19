/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 17, 2004
 */
public class JavaSdkImpl extends JavaSdk {
  // do not use javaw.exe for Windows because of issues with encoding
  @NonNls private static final String VM_EXE_NAME = "java";
  @SuppressWarnings({"HardCodedStringLiteral"})
  private final Pattern myVersionStringPattern = Pattern.compile("^(.*)java version \"([1234567890_.]*)\"(.*)$");
  public static final Icon ICON = IconLoader.getIcon("/nodes/ppJdkClosed.png");
  private static final Icon JDK_ICON_EXPANDED = IconLoader.getIcon("/nodes/ppJdkOpen.png");
  private static final Icon ADD_ICON = IconLoader.getIcon("/general/addJdk.png");
  private final JarFileSystem myJarFileSystem;
  @NonNls private static final String JAVA_VERSION_PREFIX = "java version ";

  public JavaSdkImpl(JarFileSystem jarFileSystem) {
    super("JavaSDK");
    myJarFileSystem = jarFileSystem;
  }

  public String getPresentableName() {
    return ProjectBundle.message("sdk.java.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  public Icon getIconForExpandedTreeNode() {
    return JDK_ICON_EXPANDED;
  }

  public Icon getIconForAddAction() {
    return ADD_ICON;
  }

  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
    return null;
  }

  public void saveAdditionalData(SdkAdditionalData additionalData, Element additional) {
  }

  public SdkAdditionalData loadAdditionalData(Element additional) {
    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getBinPath(Sdk sdk) {
    return getConvertedHomePath(sdk) + "bin";
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getToolsPath(Sdk sdk) {
    final String versionString = sdk.getVersionString();
    final boolean isJdk1_x = versionString.indexOf("1.0") > -1 || versionString.indexOf("1.1") > -1;
    return getConvertedHomePath(sdk) + "lib" + File.separator + (isJdk1_x? "classes.zip" : "tools.jar");
  }

  public String getVMExecutablePath(Sdk sdk) {
    /*
    if ("64".equals(System.getProperty("sun.arch.data.model"))) {
      return getBinPath(sdk) + File.separator + System.getProperty("os.arch") + File.separator + VM_EXE_NAME;
    }
    */
    return getBinPath(sdk) + File.separator + VM_EXE_NAME;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getRtLibraryPath(Sdk sdk) {
    return getConvertedHomePath(sdk) + "jre" + File.separator + "lib" + File.separator + "rt.jar";
  }

  private String getConvertedHomePath(Sdk sdk) {
    String path = sdk.getHomePath().replace('/', File.separatorChar);
    if (!path.endsWith(File.separator)) {
      path = path + File.separator;
    }
    return path;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String suggestHomePath() {
    if (SystemInfo.isMac) {
      return "/System/Library/Frameworks/JavaVM.framework/Versions/";
    }
    return null;
  }

  public boolean isValidSdkHome(String path) {
    return checkForJdk(new File(path));
  }

  public String suggestSdkName(String currentSdkName, String sdkHome) {
    final String suggestedName;
    if (currentSdkName != null && currentSdkName.length() > 0) {
      final Matcher matcher = myVersionStringPattern.matcher(currentSdkName);
      final boolean replaceNameWithVersion = matcher.matches();
      if (replaceNameWithVersion){
        // user did not change name -> set it automatically
        final String versionString = getVersionString(sdkHome);
        suggestedName = (versionString == null)? currentSdkName : matcher.replaceFirst("$1" + versionString + "$3");
      }
      else {
        suggestedName = currentSdkName;
      }
    }
    else {
      String versionString = getVersionString(sdkHome);
      if (versionString != null) {
        if (versionString.startsWith(JAVA_VERSION_PREFIX)) {
          versionString = versionString.substring(JAVA_VERSION_PREFIX.length());
          if (versionString.startsWith("\"") && versionString.endsWith("\"")) {
            versionString = versionString.substring(1, versionString.length() - 1);
          }
          int dotIdx = versionString.indexOf('.');
          if (dotIdx > 0) {
            try {
              int major = Integer.parseInt(versionString.substring(0, dotIdx));
              int minorDot = versionString.indexOf('.', dotIdx + 1);
              if (minorDot > 0) {
                int minor = Integer.parseInt(versionString.substring(dotIdx + 1, minorDot));
                versionString = String.valueOf(major) + "." + String.valueOf(minor);
              }
            }
            catch (NumberFormatException e) {
              // Do nothing. Use original version string if failed to parse according to major.minor pattern.
            }
          }
        }
        suggestedName = versionString;
      }
      else {
        suggestedName = ProjectBundle.message("sdk.java.unknown.name");
      }
    }
    return suggestedName;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void setupSdkPaths(Sdk sdk) {
    final File jdkHome = new File(sdk.getHomePath());
    VirtualFile[] classes = findClasses(jdkHome, false, JarFileSystem.getInstance());
    VirtualFile sources = findSources(jdkHome);
    VirtualFile docs = findDocs(jdkHome, "docs/api");

    final SdkModificator sdkModificator = sdk.getSdkModificator();
    for (VirtualFile aClass : classes) {
      sdkModificator.addRoot(aClass, ProjectRootType.CLASS);
    }
    if(sources != null){
      sdkModificator.addRoot(sources, ProjectRootType.SOURCE);
    }
    if(docs != null){
      sdkModificator.addRoot(docs, ProjectRootType.JAVADOC);
    }
    else if (SystemInfo.isMac) {
      VirtualFile commonDocs;
      commonDocs = findDocs(jdkHome, "docs");
      if (commonDocs == null) {
        commonDocs = findInJar(new File(jdkHome, "docs.jar"), "doc/api");
      }
      if (commonDocs != null) {
        sdkModificator.addRoot(commonDocs, ProjectRootType.JAVADOC);
      }

      VirtualFile appleDocs;
      appleDocs = findDocs(jdkHome, "appledocs");
      if (appleDocs == null) {
        appleDocs = findInJar(new File(jdkHome, "appledocs.jar"), "appledoc/api");
      }
      if (appleDocs != null) {
        sdkModificator.addRoot(appleDocs, ProjectRootType.JAVADOC);
      }
    }
    sdkModificator.commitChanges();
  }

  public final String getVersionString(final String sdkHome) {
    String versionString = getVersionStringImpl(sdkHome);
    if (versionString != null && versionString.length() == 0) {
      versionString = null;
    }
    if (versionString == null){
      Messages.showMessageDialog(ProjectBundle.message("sdk.java.corrupt.error", sdkHome),
                                 ProjectBundle.message("sdk.java.corrupt.title"), Messages.getErrorIcon());
    }
    return versionString;
  }


  public String getComponentName() {
    return getName();
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  private static String getVersionStringImpl(String homePath){
    if (homePath == null || !new File(homePath).exists()) {
      return null;
    }
    final String[] versionString = new String[1];
    try {
      //noinspection HardCodedStringLiteral
      Process process = Runtime.getRuntime().exec(new String[] {homePath + File.separator + "bin" + File.separator + "java",  "-version"});
      VersionParsingThread parsingThread = new VersionParsingThread(process.getErrorStream(), versionString);
      parsingThread.start();
      ReadStreamThread readThread = new ReadStreamThread(process.getInputStream());
      readThread.start();
      try {
        try {
          process.waitFor();
        }
        catch (InterruptedException e) {
          e.printStackTrace();
          process.destroy();
        }
      }
      finally {
        try {
          parsingThread.join();
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
    return versionString[0];
  }

  public ProjectJdk createJdk(final String jdkName, final String home, final boolean isJre) {
    ProjectJdkImpl jdk = new ProjectJdkImpl(jdkName, this);
    SdkModificator sdkModificator = jdk.getSdkModificator();

    String path = home.replace(File.separatorChar, '/');
    sdkModificator.setHomePath(path);
    jdk.setVersionString(jdkName); // must be set after home path, otherwise setting home path clears the version string

    File jdkHomeFile = new File(home);
    addClasses(jdkHomeFile, sdkModificator, isJre, myJarFileSystem);
    addSources(jdkHomeFile, sdkModificator);
    addDocs(jdkHomeFile, sdkModificator);
    sdkModificator.commitChanges();
    return jdk;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static ProjectJdk getMockJdk(@NonNls String versionName) {
    final String forcedPath = System.getProperty("idea.testingFramework.mockJDK");
    String jdkHome = forcedPath != null ? forcedPath : PathManager.getHomePath() + File.separator + "mockJDK";
    return createMockJdk(jdkHome, versionName, getInstance());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static ProjectJdk getMockJdk15(String versionName) {
    String jdkHome = PathManager.getHomePath() + File.separator + "mockJDK-1.5";
    return createMockJdk(jdkHome, versionName, getInstance());
  }

  private static ProjectJdk createMockJdk(String jdkHome, final String versionName, JavaSdk javaSdk) {
    File jdkHomeFile = new File(jdkHome);
    if (!jdkHomeFile.exists()) return null;

    final ProjectJdk jdk = new ProjectJdkImpl(versionName, javaSdk);
    final SdkModificator sdkModificator = jdk.getSdkModificator();

    String path = jdkHome.replace(File.separatorChar, '/');
    sdkModificator.setHomePath(path);
    sdkModificator.setVersionString(versionName); // must be set after home path, otherwise setting home path clears the version string

    addSources(jdkHomeFile, sdkModificator);
    addClasses(jdkHomeFile, sdkModificator, false, JarFileSystem.getInstance());
    addClasses(jdkHomeFile, sdkModificator, true, JarFileSystem.getInstance());
    sdkModificator.commitChanges();

    return jdk;
  }

  private static void addClasses(File file, SdkModificator sdkModificator, final boolean isJre, JarFileSystem jarFileSystem) {
    VirtualFile[] classes = findClasses(file, isJre, jarFileSystem);
    for (VirtualFile virtualFile : classes) {
      sdkModificator.addRoot(virtualFile, ProjectRootType.CLASS);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static VirtualFile[] findClasses(File file, boolean isJre, JarFileSystem jarFileSystem) {
    FileFilter jarFileFilter = new FileFilter(){
      @SuppressWarnings({"HardCodedStringLiteral"})
      public boolean accept(File f){
        if (f.isDirectory()) return false;
        if (f.getName().endsWith(".jar")) return true;
        return false;
      }
    };

    File[] jarDirs;
    if(SystemInfo.isMac && !ApplicationManager.getApplication().isUnitTestMode()){
      File libFile = new File(file, "lib");
      File classesFile = new File(file, "../Classes");
      File libExtFile = new File(libFile, "ext");
      jarDirs = new File[]{libFile, classesFile, libExtFile};
    }
    else{
      File jreLibFile = isJre ? new File(file, "lib") : new File(new File(file, "jre"), "lib");
      File jreLibExtFile = new File(jreLibFile, "ext");
      jarDirs = new File[]{jreLibFile, jreLibExtFile};
    }

    ArrayList<File> childrenList = new ArrayList<File>();
    for (File jarDir : jarDirs) {
      if ((jarDir != null) && jarDir.isDirectory()) {
        File[] files = jarDir.listFiles(jarFileFilter);
        for (File file1 : files) {
          childrenList.add(file1);
        }
      }
    }

    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (File child : childrenList) {
      String path = child.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
      jarFileSystem.setNoCopyJarForPath(path);
      VirtualFile vFile = jarFileSystem.findFileByPath(path);
      if (vFile != null) {
        result.add(vFile);
      }
    }

    File classesZipFile = new File(new File(file, "lib"), "classes.zip");
    if((!classesZipFile.isDirectory()) && classesZipFile.exists()){
      String path = classesZipFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
      jarFileSystem.setNoCopyJarForPath(path);
      VirtualFile vFile = jarFileSystem.findFileByPath(path);
      if (vFile != null){
        result.add(vFile);
      }
    }

    return result.toArray(new VirtualFile[result.size()]);
  }

  private static void addSources(File file, SdkModificator sdkModificator) {
    VirtualFile vFile = findSources(file);
    if (vFile != null) {
      sdkModificator.addRoot(vFile, ProjectRootType.SOURCE);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static VirtualFile findSources(File file) {
    File srcfile = new File(file, "src");
    File jarfile = new File(file, "src.jar");
    if (!jarfile.exists()) {
      jarfile = new File(file, "src.zip");
    }

    if (jarfile.exists()) {
      VirtualFile vFile = findInJar(jarfile, "src");
      if (vFile != null) return vFile;
      // try 1.4 format
      vFile = findInJar(jarfile, "");
      return vFile;
    }
    else {
      if (!srcfile.exists() || !srcfile.isDirectory()) return null;
      String path = srcfile.getAbsolutePath().replace(File.separatorChar, '/');
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(path);
      return vFile;
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void addDocs(File file, SdkModificator rootContainer) {
    VirtualFile vFile = findDocs(file, "docs/api");
    if (vFile != null) {
      rootContainer.addRoot(vFile, ProjectRootType.JAVADOC);
    }
  }

  private static VirtualFile findInJar(File jarFile, String relativePath) {
    if (!jarFile.exists()) return null;
    JarFileSystem jarFileSystem = JarFileSystem.getInstance();
    String path = jarFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR + relativePath;
    jarFileSystem.setNoCopyJarForPath(path);
    return jarFileSystem.findFileByPath(path);
  }

  public static VirtualFile findDocs(File file, final String relativePath) {
    file = new File(file.getAbsolutePath() + File.separator + relativePath.replace('/', File.separatorChar));
    if (!file.exists() || !file.isDirectory()) return null;
    String path = file.getAbsolutePath().replace(File.separatorChar, '/');
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(path);
    return vFile;
  }

  private static class ReadStreamThread extends Thread {
    private InputStream myStream;

    protected ReadStreamThread(InputStream stream) {
      myStream = stream;
    }

    public void run() {
      try {
        while (true) {
          int b = myStream.read();
          if (b == -1) break;
        }
      }
      catch (IOException e) {
      }
    }
  }

  private static class VersionParsingThread extends Thread {
    private Reader myReader;
    private boolean mySkipLF = false;
    private String[] myVersionString;
    @NonNls private static final String VERSION = "version";

    protected VersionParsingThread(InputStream input, String[] versionString) {
      myReader = new InputStreamReader(input);
      myVersionString = versionString;
    }

    public void run() {
      try {
        while (true) {
          String line = readLine();
          if (line == null) return;
          if (line.indexOf(VERSION) >= 0) {
            myVersionString[0] = line;
          }
        }
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }

    private String readLine() throws IOException {
      boolean first = true;
      StringBuffer buffer = new StringBuffer();
      while (true) {
        int c = myReader.read();
        if (c == -1) break;
        first = false;
        if (c == '\n') {
          if (mySkipLF) {
            mySkipLF = false;
            continue;
          }
          break;
        }
        else if (c == '\r') {
          mySkipLF = true;
          break;
        }
        else {
          mySkipLF = false;
          buffer.append((char)c);
        }
      }
      if (first) return null;
      String s = buffer.toString();
      //if (Diagnostic.TRACE_ENABLED){
      //  Diagnostic.trace(s);
      //}
      return s;
    }
  }
}
