/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.compiler.impl.rmiCompiler;

import com.intellij.compiler.JavacOutputParser;
import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.CompilerParsingThread;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.compiler.JavacResourcesReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 10, 2005
 */
public class OutputParserPool {
  protected final Project myProject;
  private final CompileContext myContext;
  private final Map<ProjectJdk, OutputParser> myProjectToParserMap = new HashMap<ProjectJdk, OutputParser>();

  protected OutputParserPool(Project project, final CompileContext context) {
    myProject = project;
    myContext = context;
  }

  public OutputParser getOutputParser(ProjectJdk jdk) throws IOException {
    OutputParser outputParser = myProjectToParserMap.get(jdk);
    if (outputParser == null) {
      outputParser = createOutputparser(jdk);
      myProjectToParserMap.put(jdk, outputParser);
    }
    return outputParser;
  }

  private OutputParser createOutputparser(final ProjectJdk jdk) throws IOException {
    final JavacOutputParser outputParser = new JavacOutputParser(myProject);
    // first, need to setup the output parser
    final String[] setupCmdLine = ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      public String[] compute() {
        return createParserSetupCommand(jdk);
      }
    });
    final Process setupProcess = Runtime.getRuntime().exec(setupCmdLine);

    final CompilerParsingThread setupProcessParsingThread = new CompilerParsingThread(setupProcess, outputParser, true) {
      public void setProgressText(String text) {
      }
      public void message(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum) {
        myContext.addMessage(category, message, url, lineNum, columnNum);
      }
      protected boolean isCancelled() {
        return myContext.getProgressIndicator().isCanceled();
      }
      public void fileProcessed(String path) {
      }
      protected void processCompiledClass(String classFileToProcess) {
      }
    };
    setupProcessParsingThread.start();
    try {
      setupProcessParsingThread.join();
    }
    catch (InterruptedException e) {
    }
    return outputParser;
  }

  private String[] createParserSetupCommand(final ProjectJdk jdk) {
    final List<String> commandLine = new ArrayList<String>();

    final VirtualFile homeDirectory = jdk.getHomeDirectory();
    if (homeDirectory == null) {
      throw new IllegalArgumentException(CompilerBundle.message("javac.error.jdk.home.missing", jdk.getName()));
    }

    commandLine.add(jdk.getVMExecutablePath());

    CompilerUtil.addLocaleOptions(commandLine, false);

    //noinspection HardCodedStringLiteral
    commandLine.add("-classpath");
    commandLine.add(jdk.getToolsPath() + File.pathSeparator + PathUtilEx.getIdeaRtJarPath());

    commandLine.add(JavacResourcesReader.class.getName());

    return commandLine.toArray(new String[commandLine.size()]);
  }

}
