/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.SystemProperties;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class XsltRunConfiguration extends LocatableConfigurationBase implements ModuleRunConfiguration, RunConfigurationWithSuppressedDefaultDebugAction {
    private static final String NAME = "XSLT Configuration";

    private static final String STRICT_FILE_PATH_EXPR = "(file\\://?(?:/?\\p{Alpha}\\:)?(?:/\\p{Alpha}\\:)?[^:]+)";
    private static final String RELAXED_FILE_PATH_EXPR = "((?:file\\://?)?(?:/?\\p{Alpha}\\:)?(?:/\\p{Alpha}\\:)?[^:]+)";

    private static final String LOG_TAG = "(?:\\[[\\w ]+\\]\\:? +)?";

    public enum OutputType {
        CONSOLE, STDOUT, @Deprecated FILE
    }

    public enum JdkChoice {
        FROM_MODULE, JDK
    }

    private List<Pair<String, String>> myParameters = new ArrayList<>();
    @Nullable private VirtualFilePointer myXsltFile = null;
    @Nullable private VirtualFilePointer myXmlInputFile = null;
    @NotNull private OutputType myOutputType = OutputType.CONSOLE;
    private boolean mySaveToFile = false;
    @NotNull private JdkChoice myJdkChoice = JdkChoice.FROM_MODULE;
    @Nullable private FileType myFileType = StdFileTypes.XML;

    public String myOutputFile; // intentionally untracked. should it be?
    public boolean myOpenOutputFile;
    public boolean myOpenInBrowser;
    public boolean mySmartErrorHandling = true;
    @Deprecated // this is only used if the dynamic selection of a port fails  
    public int myRunnerPort = 34873;
    public String myVmArguments;
    public String myWorkingDirectory;
    public String myModule;
    public String myJdk;

    private String mySuggestedName;

    public XsltRunConfiguration(Project project, ConfigurationFactory factory) {
        super(project, factory, NAME);
        mySuggestedName = null;
    }

    @NotNull
    @Override
    public SettingsEditor<XsltRunConfiguration> getConfigurationEditor() {
        return new XsltRunSettingsEditor(getProject());
    }

    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment executionEnvironment) throws ExecutionException {
        if (myXsltFile == null) throw new ExecutionException("No XSLT file selected");
        final VirtualFile baseFile = myXsltFile.getFile();

        final XsltCommandLineState state = new XsltCommandLineState(this, executionEnvironment);
        final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(getProject());
        builder.addFilter(new CustomRegexpFilter(getProject(), RegexpFilter.FILE_PATH_MACROS + "\\:" +
                "(?:(?: line )?" + RegexpFilter.LINE_MACROS + ")?" +
                "(?:\\:(?: column )?" + RegexpFilter.COLUMN_MACROS + ")?", baseFile, STRICT_FILE_PATH_EXPR));
        builder.addFilter(new CustomRegexpFilter(getProject(), LOG_TAG +
                RegexpFilter.FILE_PATH_MACROS + "\\:" +
                "(?:(?: line )?" + RegexpFilter.LINE_MACROS + ")?" +
                "(?:\\:(?: column )?" + RegexpFilter.COLUMN_MACROS + ")?", baseFile, RELAXED_FILE_PATH_EXPR));

        builder.addFilter(new CustomRegexpFilter(getProject(), RegexpFilter.FILE_PATH_MACROS + ";" +
                " \\w+ #" + RegexpFilter.LINE_MACROS +
                "(?:; \\w+ #" + RegexpFilter.COLUMN_MACROS + ")?", baseFile, STRICT_FILE_PATH_EXPR));
        builder.addFilter(new CustomRegexpFilter(getProject(), LOG_TAG +
                RegexpFilter.FILE_PATH_MACROS + ";" +
                " \\w+ #" + RegexpFilter.LINE_MACROS +
                "(?:; \\w+ #" + RegexpFilter.COLUMN_MACROS + ")?", baseFile, RELAXED_FILE_PATH_EXPR));

        builder.addFilter(new CustomRegexpFilter(getProject(), "(?:" + RegexpFilter.FILE_PATH_MACROS + ")?" +
                " line " + RegexpFilter.LINE_MACROS +
                "(?:\\:(?: column )?" + RegexpFilter.COLUMN_MACROS + ")?", baseFile, STRICT_FILE_PATH_EXPR));

        state.setConsoleBuilder(builder);
        return state;
    }

    @Override
    public void createAdditionalTabComponents(final AdditionalTabComponentManager manager, ProcessHandler startedProcess) {
      if (myOutputType == OutputType.CONSOLE) {
        final HighlightingOutputConsole console = new HighlightingOutputConsole(getProject(), myFileType);

          XsltCommandLineState state = startedProcess.getUserData(XsltCommandLineState.STATE);
          boolean debug = state != null && state.isDebugger();
          boolean consoleTabAdded = false;
        for (XsltRunnerExtension extension : XsltRunnerExtension.getExtensions(this, debug)) {
          if (extension.createTabs(getProject(), manager, console, startedProcess)) {
            consoleTabAdded = true;
          }
        }
        if (!consoleTabAdded) {
          manager.addAdditionalTabComponent(console, console.getTabTitle());    // TODO: verify parameter
        }

        final OutputTabAdapter listener = new OutputTabAdapter(startedProcess, console);
        if (startedProcess.isStartNotified()) {
          listener.startNotified(new ProcessEvent(startedProcess));
        }
        else {
          startedProcess.addProcessListener(listener);
        }
      }
    }

    @Override
    public final RunConfiguration clone() {
        final XsltRunConfiguration configuration = (XsltRunConfiguration)super.clone();
        configuration.myParameters = new ArrayList<>(myParameters);
        if (myXsltFile != null) configuration.myXsltFile = VirtualFilePointerManager.getInstance().duplicate(myXsltFile, getProject(), null);
        if (myXmlInputFile != null) configuration.myXmlInputFile = VirtualFilePointerManager.getInstance().duplicate(myXmlInputFile, getProject(), null);
        return configuration;
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (myXsltFile == null) {
            throw new RuntimeConfigurationError("No XSLT File selected");
        }
        if (myXsltFile.getFile() == null) {
            throw new RuntimeConfigurationError("Selected XSLT File not found");
        }
        if (myXmlInputFile == null) {
            throw new RuntimeConfigurationError("No XML Input File selected");
        }
        if (myXmlInputFile.getFile() == null) {
            throw new RuntimeConfigurationError("Selected XML Input File not found");
        }
        if (mySaveToFile) {
            if (isEmpty(myOutputFile)) {
                throw new RuntimeConfigurationError("No output file selected");
            }
            final File f = new File(myOutputFile);
            if (f.isDirectory()) {
                throw new RuntimeConfigurationError("Selected output file points to a directory");
            } else if (f.exists() && !f.canWrite()) {
                throw new RuntimeConfigurationError("Selected output file is not writable");
            }
        }
        if (getEffectiveJDK() == null) {
            throw new RuntimeConfigurationError("No JDK available");
        }
    }

    static boolean isEmpty(String file) {
        return file == null || file.length() == 0;
    }

    // return modules to compile before run. Null or empty list to make project
    @Override
    @NotNull
    public Module[] getModules() {
        return getModule() != null ? new Module[]{ getModule() } : Module.EMPTY_ARRAY;
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public void readExternal(Element element) throws InvalidDataException {
        super.readExternal(element);
        DefaultJDOMExternalizer.readExternal(this, element);

        Element e = element.getChild("XsltFile");
        if (e != null) {
            final String url = e.getAttributeValue("url");
            if (url != null) {
                myXsltFile = VirtualFilePointerManager.getInstance().create(url, getProject(), null);
            }
        }
        e = element.getChild("XmlFile");
        if (e != null) {
            final String url = e.getAttributeValue("url");
            if (url != null) {
                myXmlInputFile = VirtualFilePointerManager.getInstance().create(url, getProject(), null);
            }
        }

        final Element parameters = element.getChild("parameters");
        if (parameters != null) {
            myParameters.clear();
            final List<Element> params = parameters.getChildren("param");
            for (Element p : params) {
                myParameters.add(Pair.create(p.getAttributeValue("name"), p.getAttributeValue("value")));
            }
        }

        final Element outputType = element.getChild("OutputType");
        if (outputType != null) {
            final String value = outputType.getAttributeValue("value");
            if (OutputType.FILE.name().equals(value)) {
                myOutputType = OutputType.STDOUT;
                mySaveToFile = true;
            } else {
                myOutputType = OutputType.valueOf(value);
                mySaveToFile = Boolean.valueOf(outputType.getAttributeValue("save-to-file"));
            }
        }
        final Element fileType = element.getChild("FileType");
        if (fileType != null) {
            myFileType = getFileType(fileType.getAttributeValue("name"));
        }
        final Element jdkChoice = element.getChild("JdkChoice");
        if (jdkChoice != null) {
            myJdkChoice = JdkChoice.valueOf(jdkChoice.getAttributeValue("value"));
        }
    }

    @Nullable
    private static FileType getFileType(String value) {
        if (value == null) return null;

        final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
        for (FileType fileType : fileTypes) {
            if (fileType.getName().equals(value)) {
                return fileType;
            }
        }
        return null;
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        super.writeExternal(element);
        DefaultJDOMExternalizer.writeExternal(this, element);

        Element e = new Element("XsltFile");
        if (myXsltFile != null) {
            e.setAttribute("url", myXsltFile.getUrl());
            element.addContent(e);
        }
        e = new Element("XmlFile");
        if (myXmlInputFile != null) {
            e.setAttribute("url", myXmlInputFile.getUrl());
            element.addContent(e);
        }

        final Element params = new Element("parameters");
        element.addContent(params);
        for (Pair<String, String> pair : myParameters) {
            final Element p = new Element("param");
            params.addContent(p);
            p.setAttribute("name", pair.getFirst());

            final String value = pair.getSecond();
            if (value != null) {
                p.setAttribute("value", value);
            }
        }

        final Element type = new Element("OutputType");
        type.setAttribute("value", myOutputType.name());
        type.setAttribute("save-to-file", String.valueOf(mySaveToFile));
        element.addContent(type);

        final Element fileType = new Element("FileType");
        if (myFileType != null) {
          fileType.setAttribute("name", myFileType.getName());
        }
        element.addContent(fileType);

        final Element choice = new Element("JdkChoice");
        choice.setAttribute("value", myJdkChoice.name());
        element.addContent(choice);
    }

    public List<Pair<String, String>> getParameters() {
        return myParameters;
    }

    public void setParameters(List<Pair<String, String>> params) {
        myParameters.clear();
        myParameters.addAll(params);
    }

    public String getVmArguments() {
        return myVmArguments;
    }

    public void setVmArguments(String vmArguments) {
        myVmArguments = vmArguments;
    }

    public void setXsltFile(@NotNull String xsltFile) {
        if (isEmpty(xsltFile)) {
            myXsltFile = null;
        } else {
            myXsltFile = VirtualFilePointerManager.getInstance().create(VfsUtilCore.pathToUrl(xsltFile).replace(File.separatorChar, '/'), getProject(), null);
        }
    }

    private void setXsltFile(VirtualFile virtualFile) {
        myXsltFile = VirtualFilePointerManager.getInstance().create(virtualFile, getProject(), null);
    }

    @Nullable
    public String getXsltFile() {
        return myXsltFile != null ? myXsltFile.getPresentableUrl() : null;
    }

    @Nullable
    public VirtualFile findXsltFile() {
        return myXsltFile != null ? myXsltFile.getFile() : null;
    }

    @Nullable
    public VirtualFile findXmlInputFile() {
        return myXmlInputFile != null ? myXmlInputFile.getFile() : null;
    }

    @Nullable
    public String getXmlInputFile() {
        return myXmlInputFile != null ? myXmlInputFile.getPresentableUrl() : null;
    }

    @Nullable
    public FileType getFileType() {
        return myFileType;
    }

    public void setFileType(@Nullable FileType fileType) {
        myFileType = fileType;
    }

    @Nullable
    public Module getModule() {
        return myModule != null ? ModuleManager.getInstance(getProject()).findModuleByName(myModule) : null;
    }

    @Nullable
    public Sdk getJdk() {
        return myJdk != null ? ProjectJdkTable.getInstance().findJdk(myJdk) : null;
    }

    public void setXmlInputFile(@NotNull String xmlInputFile) {
        if (isEmpty(xmlInputFile)) {
            myXmlInputFile = null;
        } else {
            myXmlInputFile = VirtualFilePointerManager.getInstance().create(VfsUtilCore.pathToUrl(xmlInputFile).replace(File.separatorChar, '/'), getProject(), null);
        }
    }

    public void setXmlInputFile(VirtualFile xmlInputFile) {
      myXmlInputFile = VirtualFilePointerManager.getInstance().create(xmlInputFile, getProject(), null);
    }

    public void setModule(Module module) {
        myModule = module != null ? module.getName() : null;
    }

    public void setJDK(Sdk projectJdk) {
        myJdk = projectJdk != null ? projectJdk.getName() : null;
    }

    @NotNull
    public OutputType getOutputType() {
        return myOutputType;
    }

    public void setOutputType(@NotNull OutputType outputType) {
        myOutputType = outputType;
    }

    public boolean isSaveToFile() {
        return mySaveToFile;
    }

    public void setSaveToFile(boolean saveToFile) {
        mySaveToFile = saveToFile;
    }

    @NotNull
    public JdkChoice getJdkChoice() {
        return myJdkChoice;
    }

    public void setJdkChoice(@NotNull JdkChoice jdkChoice) {
        myJdkChoice = jdkChoice;
    }

    private static Sdk ourDefaultSdk;
  
    private static synchronized Sdk getDefaultSdk() {
        if (ourDefaultSdk == null) {
            final String jdkHome = SystemProperties.getJavaHome();
            final String versionName = ProjectBundle.message("sdk.java.name.template", SystemProperties.getJavaVersion());
            Sdk sdk = ProjectJdkTable.getInstance().createSdk(versionName, new SimpleJavaSdkType());
            SdkModificator modificator = sdk.getSdkModificator();
            modificator.setHomePath(jdkHome);
            modificator.commitChanges();
            ourDefaultSdk = sdk;
        }
        
        return ourDefaultSdk;
    }
  
    @Nullable
    public Sdk getEffectiveJDK() {
        if (!XsltRunSettingsEditor.ALLOW_CHOOSING_SDK) {
            return getDefaultSdk(); 
        }
        if (myJdkChoice == JdkChoice.JDK) {
            return myJdk != null ? ProjectJdkTable.getInstance().findJdk(myJdk) : null;
        }
        Sdk jdk = null;
        final Module module = getEffectiveModule();
        if (module != null) {
            jdk = ModuleRootManager.getInstance(module).getSdk();
        }
        if (jdk == null) {
            jdk = ProjectRootManager.getInstance(getProject()).getProjectSdk();
        }
        // EA-33419
        if (jdk == null || !(jdk.getSdkType() instanceof JavaSdkType)) {
          return getDefaultSdk();
        }
        return jdk;
    }

    @Nullable
    public Module getEffectiveModule() {
        //assert myJdkChoice == JdkChoice.FROM_MODULE;

        Module module = myJdkChoice == JdkChoice.FROM_MODULE ? getModule() : null;
        if (module == null && myXsltFile != null) {
            final VirtualFile file = myXsltFile.getFile();
            if (file != null) {
                final ProjectFileIndex index = ProjectRootManager.getInstance(getProject()).getFileIndex();
                module = index.getModuleForFile(file);
            }
        }
        return module;
    }

    @Override
    public String suggestedName() {
        return mySuggestedName;
    }

    public XsltRunConfiguration initFromFile(@NotNull XmlFile file) {
        assert XsltSupport.isXsltFile(file) : "Not an XSLT file: " + file.getName();
        mySuggestedName = file.getName();

        final VirtualFile virtualFile = file.getVirtualFile();
        assert virtualFile != null : "No VirtualFile for " + file.getName();

        setXsltFile(virtualFile);

        final PsiFile[] associations = FileAssociationsManager.getInstance(file.getProject()).getAssociationsFor(file);
        if (associations.length > 0) {
            final VirtualFile assoc = associations[0].getVirtualFile();
            assert assoc != null;
            setXmlInputFile(assoc);
        }

        final XmlDocument document = file.getDocument();
        assert document != null : "XSLT file without document?";

        final XmlTag rootTag = document.getRootTag();
        assert rootTag != null : "XSLT file without root element?";

        final XmlTag[] params = rootTag.findSubTags("param", XsltSupport.XSLT_NS);
        for (XmlTag param : params) {
            final String name = param.getAttributeValue("name");
            if (name != null) {
                final Pair<String, String> pair = Pair.create(name, null);
                myParameters.add(pair);
            }
        }
        final XmlTag[] outputs = rootTag.findSubTags("output", XsltSupport.XSLT_NS);
        for (XmlTag output : outputs) {
            final String method = output.getAttributeValue("method");
            if ("xml".equals(method)) {
                setFileType(StdFileTypes.XML);
            } else if ("html".equals(method)) {
                setFileType(StdFileTypes.HTML);
            } else if ("text".equals(method)) {
                setFileType(FileTypes.PLAIN_TEXT);
            }
        }

        return this;
    }
}
