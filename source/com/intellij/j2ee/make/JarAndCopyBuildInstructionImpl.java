package com.intellij.j2ee.make;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;
import gnu.trove.THashSet;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jun 21, 2004
 * Time: 4:08:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class JarAndCopyBuildInstructionImpl extends FileCopyInstructionImpl implements JarAndCopyBuildInstruction {
  private File myJarFile;

  public JarAndCopyBuildInstructionImpl(Module module,
                                        File directoryToJar,
                                        String outputRelativePath) {
    super(directoryToJar, false, module, outputRelativePath);
  }

  public void addFilesToExploded(CompileContext context,
                                 File outputDir,
                                 Set<String> writtenPaths,
                                 FileFilter fileFilter) throws IOException {
    //todo optmization: cache created jar and issue single FileCopy on it
    final File jarFile = MakeUtil.canonicalRelativePath(outputDir, getOutputRelativePath());

    makeJar(context, jarFile, fileFilter);
    writtenPaths.add(myJarFile.getPath());
  }

  public void addFilesToJar(CompileContext context,
                            File jarFile,
                            JarOutputStream outputStream,
                            BuildRecipe dependencies,
                            Set<String> writtenRelativePaths,
                            FileFilter fileFilter) throws IOException {
    // create temp jars, and add these into upper level jar
    // todo optimization: cache created jars
    final String moduleName = getModule() == null ? "jar" : ModuleUtil.getModuleNameInReadAction(getModule());
    final File tempFile = File.createTempFile(moduleName+"___",".tmp");
    makeJar(context, tempFile, fileFilter);

    final String outputRelativePath = getOutputRelativePath();

    File file = getJarFile();
    if (isExternalDependencyInstruction()) {
      // copy dependent file along with jar file
      final File toFile = MakeUtil.canonicalRelativePath(jarFile, outputRelativePath);
      MakeUtil.getInstance().copyFile(file, toFile, context, new HashSet<String>(), fileFilter);
      dependencies.addInstruction(this);
    }
    else {
      ZipUtil.addFileToZip(outputStream, file, outputRelativePath, writtenRelativePaths, fileFilter);
    }
  }

  public void makeJar(CompileContext context, File jarFile, FileFilter fileFilter) throws IOException {
    if (jarFile.equals(myJarFile)) return;
    if (myJarFile != null) {
      // optimization: file already jarred, copy it over
      MakeUtil.getInstance().copyFile(myJarFile, jarFile, context, null, fileFilter);
    }
    else {
      FileUtil.createParentDirs(jarFile);
      Manifest manifest = new Manifest();
      ManifestBuilder.setGlobalAttributes(manifest.getMainAttributes());

      final JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(jarFile), manifest);
      try {
        boolean ok = ZipUtil.addDirToZipRecursively(jarOutputStream, jarFile, getFile(), "", fileFilter, new THashSet<String>());
        if (!ok) {
          String dirPath = getFile().getPath();
          MakeUtil.reportRecursiveCopying(context, dirPath, jarFile.getPath(), "",
                                          "Please setup jar file location outside directory '" + dirPath + "'.");
        }
      }
      finally {
        jarOutputStream.close();
      }
    }
    myJarFile = jarFile;
  }


  public boolean accept(BuildInstructionVisitor visitor) throws Exception {
    return visitor.visitJarAndCopyBuildInstruction(this);
  }

  public String toString() {
    String s = "JAR and copy: ";
    s += getFile();
    s += "->"+getOutputRelativePath();
    return s;
  }

  public File getJarFile() {
    return myJarFile;
  }
}
