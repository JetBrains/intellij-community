/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.uiDesigner.compiler;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.compiler.*;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.ClassWriter;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.OneToManyPathsMapping;
import org.jetbrains.jps.javac.BinaryContent;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerConfiguration;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerExtensionService;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 11/20/12
 */
public class FormsInstrumenter extends FormsBuilder {

  public FormsInstrumenter() {
    super(BuilderCategory.CLASS_INSTRUMENTER, "forms");
  }

  @Override
  public ExitCode build(CompileContext context, ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder, OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsUiDesignerConfiguration config = JpsUiDesignerExtensionService.getInstance().getOrCreateUiDesignerConfiguration(project);
    if (!config.isInstrumentClasses()) {
      return ExitCode.NOTHING_DONE;
    }

    final List<File> forms = new ArrayList<File>();
    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor descriptor) throws IOException {
        if (FORM_SOURCES_FILTER.accept(file)) {
          forms.add(file);
        }
        return true;
      }
    });

    if (forms.isEmpty()) {
      return ExitCode.NOTHING_DONE;
    }

    if (context.isMake()) {
      final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
      if (logger.isEnabled()) {
        logger.logCompiledFiles(forms, getPresentableName(), "Compiling forms:");
      }
    }

    try {
      context.processMessage(new ProgressMessage("Instrumenting forms [" + chunk.getName() + "]"));
      final ProjectPaths paths = context.getProjectPaths();
      final Collection<File> classpath = paths.getCompilationClasspath(chunk, false);
      final Collection<File> platformCp = paths.getPlatformCompilationClasspath(chunk, false);
      final Map<File, String> chunkSourcePath = ProjectPaths.getSourceRootsWithDependents(chunk);
      final InstrumentationClassFinder finder = createInstrumentationClassFinder(platformCp, classpath, chunkSourcePath, outputConsumer);

      try {
        instrumentForms(context, chunk, chunkSourcePath, finder, forms, outputConsumer);
      }
      finally {
        finder.releaseResources();
      }
    }
    finally {
      context.processMessage(new ProgressMessage("Finished instrumenting forms [" + chunk.getName() + "]"));
    }

    return ExitCode.OK;
  }

  private void instrumentForms(
    CompileContext context, ModuleChunk chunk, final Map<File, String> chunkSourcePath, final InstrumentationClassFinder finder, Collection<File> forms, OutputConsumer outConsumer
  ) throws ProjectBuildException {

    final Map<String, File> class2form = new HashMap<String, File>();
    final OneToManyPathsMapping sourceToFormMap = context.getProjectDescriptor().dataManager.getSourceToFormMap();
    final Set<File> touchedFiles = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);

    final MyNestedFormLoader nestedFormsLoader =
      new MyNestedFormLoader(chunkSourcePath, ProjectPaths.getOutputPathsWithDependents(chunk));

    for (File formFile : forms) {
      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(
          formFile.toURI().toURL(), new CompiledClassPropertiesProvider( finder.getLoader())
        );
      }
      catch (AlienFormFileException e) {
        // ignore non-IDEA forms
        continue;
      }
      catch (UnexpectedFormElementException e) {
        context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, e.getMessage(), formFile.getPath()));
        LOG.info(e);
        continue;
      }
      catch (UIDesignerException e) {
        context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, e.getMessage(), formFile.getPath()));
        LOG.info(e);
        continue;
      }
      catch (Exception e) {
        throw new ProjectBuildException("Cannot process form file " + formFile.getAbsolutePath(), e);
      }

      final String classToBind = rootContainer.getClassToBind();
      if (classToBind == null) {
        continue;
      }

      final CompiledClass compiled = findClassFile(outConsumer, classToBind);
      if (compiled == null) {
        context.processMessage(new CompilerMessage(
          getPresentableName(), BuildMessage.Kind.WARNING, "Class to bind does not exist: " + classToBind, formFile.getAbsolutePath())
        );
        continue;
      }

      final File alreadyProcessedForm = class2form.get(classToBind);
      if (alreadyProcessedForm != null) {
        context.processMessage(
          new CompilerMessage(
            getPresentableName(), BuildMessage.Kind.WARNING,
            formFile.getAbsolutePath() + ": The form is bound to the class " + classToBind + ".\nAnother form " + alreadyProcessedForm.getAbsolutePath() + " is also bound to this class",
            formFile.getAbsolutePath())
        );
        continue;
      }

      class2form.put(classToBind, formFile);

      try {
        final BinaryContent originalContent = compiled.getContent();
        final ClassReader classReader =
          new ClassReader(originalContent.getBuffer(), originalContent.getOffset(), originalContent.getLength());

        final int version = getClassFileVersion(classReader);
        final InstrumenterClassWriter classWriter = new InstrumenterClassWriter(getAsmClassWriterFlags(version), finder);
        final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, finder, nestedFormsLoader, false, classWriter);
        final byte[] patchedBytes = codeGenerator.patchClass(classReader);
        if (patchedBytes != null) {
          compiled.setContent(new BinaryContent(patchedBytes));
        }

        final FormErrorInfo[] warnings = codeGenerator.getWarnings();
        for (final FormErrorInfo warning : warnings) {
          context.processMessage(
            new CompilerMessage(getPresentableName(), BuildMessage.Kind.WARNING, warning.getErrorMessage(), formFile.getAbsolutePath())
          );
        }

        final FormErrorInfo[] errors = codeGenerator.getErrors();
        if (errors.length > 0) {
          StringBuilder message = new StringBuilder();
          for (final FormErrorInfo error : errors) {
            if (message.length() > 0) {
              message.append("\n");
            }
            message.append(formFile.getAbsolutePath()).append(": ").append(error.getErrorMessage());
          }
          context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, message.toString()));
        }
        else {
          final File sourceFile = compiled.getSourceFile();
          if (sourceFile != null) {
            if (touchedFiles.add(sourceFile)) { // clear data once before updating
              sourceToFormMap.update(sourceFile.getPath(), formFile.getPath());
            }
            else {
              sourceToFormMap.appendData(sourceFile.getPath(), formFile.getPath());
            }
          }
        }
      }
      catch (Exception e) {
        context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, "Forms instrumentation failed" + e.getMessage(), formFile.getAbsolutePath()));
      }
    }
  }


  private static CompiledClass findClassFile(OutputConsumer outputConsumer, String classToBind) {
    final Map<String, CompiledClass> compiled = outputConsumer.getCompiledClasses();
    while (true) {
      final CompiledClass fo = compiled.get(classToBind);
      if (fo != null) {
        return fo;
      }
      final int dotIndex = classToBind.lastIndexOf('.');
      if (dotIndex <= 0) {
        return null;
      }
      classToBind = classToBind.substring(0, dotIndex) + "$" + classToBind.substring(dotIndex + 1);
    }
  }

  private static int getClassFileVersion(ClassReader reader) {
    final Ref<Integer> result = new Ref<Integer>(0);
    reader.accept(new ClassVisitor(Opcodes.ASM4) {
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        result.set(version);
      }
    }, 0);
    return result.get();
  }

  private static int getAsmClassWriterFlags(int version) {
    return version >= Opcodes.V1_6 && version != Opcodes.V1_1 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS;
  }

  private static InstrumentationClassFinder createInstrumentationClassFinder(
    Collection<File> platformCp, Collection<File> cp, Map<File, String> sourcePath, final OutputConsumer outputConsumer
  ) throws MalformedURLException {

    final URL[] platformUrls = new URL[platformCp.size()];
    int index = 0;
    for (File file : platformCp) {
      platformUrls[index++] = file.toURI().toURL();
    }

    final List<URL> urls = new ArrayList<URL>(cp.size() + sourcePath.size() + 1);
    for (File file : cp) {
      urls.add(file.toURI().toURL());
    }
    urls.add(getResourcePath(GridConstraints.class).toURI().toURL()); // forms_rt.jar

    for (File file : sourcePath.keySet()) { // sourcepath for loading forms resources
      urls.add(file.toURI().toURL());
    }

    return new InstrumentationClassFinder(platformUrls, urls.toArray(new URL[urls.size()])) {
      protected InputStream lookupClassBeforeClasspath(String internalClassName) {
        final BinaryContent content = outputConsumer.lookupClassBytes(internalClassName.replace("/", "."));
        if (content != null) {
          return new ByteArrayInputStream(content.getBuffer(), content.getOffset(), content.getLength());
        }
        return null;
      }
    };
  }

  private static File getResourcePath(Class aClass) {
    return new File(PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class"));
  }

  private static class MyNestedFormLoader implements NestedFormLoader {
    private final Map<File, String> mySourceRoots;
    private final Collection<File> myOutputRoots;
    private final HashMap<String, LwRootContainer> myCache = new HashMap<String, LwRootContainer>();

    /**
     * @param sourceRoots all source roots for current module chunk and all dependent recursively
     * @param outputRoots output roots for this module chunk and all dependent recursively
     */
    public MyNestedFormLoader(Map<File, String> sourceRoots, Collection<File> outputRoots) {
      mySourceRoots = sourceRoots;
      myOutputRoots = outputRoots;
    }

    public LwRootContainer loadForm(String formFileName) throws Exception {
      if (myCache.containsKey(formFileName)) {
        return myCache.get(formFileName);
      }

      final String relPath = FileUtil.toSystemIndependentName(formFileName);

      for (Map.Entry<File, String> entry : mySourceRoots.entrySet()) {
        final File sourceRoot = entry.getKey();
        final String prefix = entry.getValue();
        String path = relPath;
        if (prefix != null && FileUtil.startsWith(path, prefix)) {
          path = path.substring(prefix.length());
        }
        final File formFile = new File(sourceRoot, path);
        if (formFile.exists()) {
          final BufferedInputStream stream = new BufferedInputStream(new FileInputStream(formFile));
          try {
            return loadForm(formFileName, stream);
          }
          finally {
            stream.close();
          }
        }
      }

      throw new Exception("Cannot find nested form file " + formFileName);
    }

    private LwRootContainer loadForm(String formFileName, InputStream resourceStream) throws Exception {
      final LwRootContainer container = Utils.getRootContainer(resourceStream, null);
      myCache.put(formFileName, container);
      return container;
    }

    public String getClassToBindName(LwRootContainer container) {
      final String className = container.getClassToBind();
      for (File outputRoot : myOutputRoots) {
        final String result = getJVMClassName(outputRoot, className.replace('.', '/'));
        if (result != null) {
          return result.replace('/', '.');
        }
      }
      return className;
    }
  }

  @Nullable
  private static String getJVMClassName(File outputRoot, String className) {
    while (true) {
      final File candidateClass = new File(outputRoot, className + ".class");
      if (candidateClass.exists()) {
        return className;
      }
      final int position = className.lastIndexOf('/');
      if (position < 0) {
        return null;
      }
      className = className.substring(0, position) + '$' + className.substring(position + 1);
    }
  }

}
