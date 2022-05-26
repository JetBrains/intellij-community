// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.uiDesigner.compiler;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.compiler.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.instrumentation.ClassProcessingBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.OneToManyPathsMapping;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerConfiguration;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerExtensionService;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class FormsInstrumenter extends FormsBuilder {
  public static final @NlsSafe String BUILDER_NAME = "forms";

  public FormsInstrumenter() {
    super(BuilderCategory.CLASS_INSTRUMENTER, BUILDER_NAME);
  }

  @Override
  public ExitCode build(CompileContext context, ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder, OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsUiDesignerConfiguration config = JpsUiDesignerExtensionService.getInstance().getOrCreateUiDesignerConfiguration(project);
    if (!config.isInstrumentClasses()) {
      return ExitCode.NOTHING_DONE;
    }

    final Map<File, Collection<File>> srcToForms = FORMS_TO_COMPILE.get(context);
    FORMS_TO_COMPILE.set(context, null);

    if (srcToForms == null || srcToForms.isEmpty()) {
      return ExitCode.NOTHING_DONE;
    }

    final Set<File> formsToCompile = FileCollectionFactory.createCanonicalFileSet();
    for (Collection<File> files : srcToForms.values()) {
      formsToCompile.addAll(files);
    }

    if (JavaBuilderUtil.isCompileJavaIncrementally(context)) {
      final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
      if (logger.isEnabled()) {
        logger.logCompiledFiles(formsToCompile, "forms", "Compiling forms:");
      }
    }

    try {
      final Collection<File> platformCp = ProjectPaths.getPlatformCompilationClasspath(chunk, false);

      final List<File> classpath = new ArrayList<>(ProjectPaths.getCompilationClasspath(chunk, false));
      classpath.add(getResourcePath(GridConstraints.class)); // forms_rt.jar
      final Map<File, String> chunkSourcePath = ProjectPaths.getSourceRootsWithDependents(chunk);
      classpath.addAll(chunkSourcePath.keySet()); // sourcepath for loading forms resources
      final JpsSdk<JpsDummyElement> sdk = chunk.representativeTarget().getModule().getSdk(JpsJavaSdkType.INSTANCE);
      final InstrumentationClassFinder finder = ClassProcessingBuilder.createInstrumentationClassFinder(sdk, platformCp, classpath, outputConsumer);

      try {
        final Map<File, Collection<File>> processed = instrumentForms(context, chunk, chunkSourcePath, finder, formsToCompile, outputConsumer, config.isUseDynamicBundles());

        final OneToManyPathsMapping sourceToFormMap = context.getProjectDescriptor().dataManager.getSourceToFormMap();

        for (Map.Entry<File, Collection<File>> entry : processed.entrySet()) {
          final File src = entry.getKey();
          final Collection<File> forms = entry.getValue();

          final Collection<String> formPaths = new ArrayList<>(forms.size());
          for (File form : forms) {
            formPaths.add(form.getPath());
          }
          sourceToFormMap.update(src.getPath(), formPaths);
          srcToForms.remove(src);
        }
        // clean mapping
        for (File srcFile : srcToForms.keySet()) {
          sourceToFormMap.remove(srcFile.getPath());
        }
      }
      finally {
        finder.releaseResources();
      }
    }
    finally {
      context.processMessage(new ProgressMessage(FormBundle.message("finish.progress.message", chunk.getPresentableShortName())));
    }

    return ExitCode.OK;
  }

  @NotNull
  @Override
  public List<String> getCompilableFileExtensions() {
    return Collections.emptyList();
  }

  private Map<File, Collection<File>> instrumentForms(
    CompileContext context,
    ModuleChunk chunk,
    final Map<File, String> chunkSourcePath,
    final InstrumentationClassFinder finder,
    Collection<File> forms,
    OutputConsumer outConsumer,
    boolean useDynamicBundles) throws ProjectBuildException {

    final Map<File, Collection<File>> instrumented = FileCollectionFactory.createCanonicalFileMap();
    final Map<String, File> class2form = new HashMap<>();

    final MyNestedFormLoader nestedFormsLoader = new MyNestedFormLoader(chunkSourcePath, ProjectPaths.getOutputPathsWithDependents(chunk), finder);

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
      catch (UnexpectedFormElementException | UIDesignerException e) {
        context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, e.getMessage(), formFile.getPath()));
        LOG.info(e);
        continue;
      }
      catch (Exception e) {
        throw new ProjectBuildException(FormBundle.message("cannot.process.form.file", formFile.getAbsolutePath()), e);
      }

      final @NlsSafe String classToBind = rootContainer.getClassToBind();
      if (classToBind == null) {
        continue;
      }

      final CompiledClass compiled = findClassFile(outConsumer, classToBind);
      if (compiled == null) {
        context.processMessage(new CompilerMessage(
          getPresentableName(), BuildMessage.Kind.ERROR, FormBundle.message("class.to.bind.does.not.exist", classToBind), formFile.getAbsolutePath())
        );
        continue;
      }

      final File alreadyProcessedForm = class2form.get(classToBind);
      if (alreadyProcessedForm != null) {
        context.processMessage(
          new CompilerMessage(
            getPresentableName(), BuildMessage.Kind.WARNING,
            FormBundle.message("form.is.bound.to.the.class.from.another.form", formFile.getAbsolutePath(), classToBind, alreadyProcessedForm.getAbsolutePath()),
            formFile.getAbsolutePath())
        );
        continue;
      }

      class2form.put(classToBind, formFile);
      for (File file : compiled.getSourceFiles()) {
        addBinding(file, formFile, instrumented);
      }


      try {
        context.processMessage(new ProgressMessage(FormBundle.message("progress.message", chunk.getPresentableShortName())));

        final BinaryContent originalContent = compiled.getContent();
        final ClassReader classReader =
          new FailSafeClassReader(originalContent.getBuffer(), originalContent.getOffset(), originalContent.getLength());

        final int flags = InstrumenterClassWriter.getAsmClassWriterFlags(InstrumenterClassWriter.getClassFileVersion(classReader));
        final InstrumenterClassWriter classWriter = new InstrumenterClassWriter(classReader, flags, finder);
        final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, finder, nestedFormsLoader, false, useDynamicBundles, classWriter);
        final byte[] patchedBytes = codeGenerator.patchClass(classReader);
        if (patchedBytes != null) {
          compiled.setContent(new BinaryContent(patchedBytes));
        }

        final FormErrorInfo[] warnings = codeGenerator.getWarnings();
        for (final FormErrorInfo warning : warnings) {
          @NlsSafe String message = warning.getErrorMessage();
          context.processMessage(
            new CompilerMessage(getPresentableName(), BuildMessage.Kind.WARNING, message, formFile.getAbsolutePath())
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
          @NlsSafe String text = message.toString();
          context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, text));
        }
      }
      catch (Exception e) {
        context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, FormBundle.message(
          "forms.instrumentation.failed", e.getMessage()), formFile.getAbsolutePath()));
      }
    }
    return instrumented;
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

  private static File getResourcePath(Class aClass) {
    return new File(PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class"));
  }

  private static class MyNestedFormLoader implements NestedFormLoader {
    private final Map<File, String> mySourceRoots;
    private final Collection<File> myOutputRoots;
    private final InstrumentationClassFinder myClassFinder;
    private final HashMap<String, LwRootContainer> myCache = new HashMap<>();

    /**
     * @param sourceRoots all source roots for current module chunk and all dependent recursively
     * @param outputRoots output roots for this module chunk and all dependent recursively
     */
    MyNestedFormLoader(Map<File, String> sourceRoots, Collection<File> outputRoots, InstrumentationClassFinder classFinder) {
      mySourceRoots = sourceRoots;
      myOutputRoots = outputRoots;
      myClassFinder = classFinder;
    }

    @Override
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

      InputStream fromLibraries = null;
      try {
        fromLibraries = myClassFinder.getResourceAsStream(relPath);
      }
      catch (IOException ignored) {
      }
      if (fromLibraries != null) {
        return loadForm(formFileName, fromLibraries);
      }

      throw new Exception("Cannot find nested form file " + formFileName);
    }

    private LwRootContainer loadForm(String formFileName, InputStream resourceStream) throws Exception {
      final LwRootContainer container = Utils.getRootContainer(resourceStream, null);
      myCache.put(formFileName, container);
      return container;
    }

    @Override
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
