package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.remote.PyRemoteInterpreterException;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.remote.PythonRemoteSdkAdditionalData;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author vlan
 */
public class PyPackageManager {
  public static final int OK = 0;
  public static final int ERROR_WRONG_USAGE = 1;
  public static final int ERROR_NO_PACKAGING_TOOLS = 2;
  public static final int ERROR_INVALID_SDK = -1;
  public static final int ERROR_HELPER_NOT_FOUND = -2;
  public static final int ERROR_TIMEOUT = -3;
  public static final int ERROR_INVALID_OUTPUT = -4;
  public static final int ERROR_ACCESS_DENIED = -5;

  private static final String PACKAGING_TOOL = "packaging_tool.py";
  private static final String VIRTUALENV = "virtualenv.py";
  private static final int TIMEOUT = 10 * 60 * 1000;
  private static final Logger LOG = Logger.getInstance("#" + PyPackageManager.class.getName());

  private static final Map<Sdk, PyPackageManager> ourInstances = new HashMap<Sdk, PyPackageManager>();

  private List<PyPackage> myPackagesCache = null;
  private Sdk mySdk;

  private PyPackageManager(@NotNull Sdk sdk) {
    mySdk = sdk;
  }

  @NotNull
  public static PyPackageManager getInstance(@NotNull Sdk sdk) {
    PyPackageManager manager = ourInstances.get(sdk);
    if (manager == null) {
      manager = new PyPackageManager(sdk);
      ourInstances.put(sdk, manager);
    }
    return manager;
  }

  public Sdk getSdk() {
    return mySdk;
  }

  // TODO: There are too many install() methods
  public void install(@NotNull List<PyRequirement> requirements) throws PyExternalProcessException {
    install(requirements, null);
  }

  public void install(@NotNull PyRequirement requirement, @Nullable List<String> options) throws PyExternalProcessException {
    install(Lists.newArrayList(requirement), options);
  }

  public void install(@NotNull PyRequirement requirement, @Nullable String url,
                      @Nullable List<String> options) throws PyExternalProcessException {
    install(Lists.newArrayList(requirement), url, options);
  }

  private void install(@NotNull List<PyRequirement> requirements, @Nullable String url,
                      @Nullable List<String> options) throws PyExternalProcessException {
    final List<String> args = new ArrayList<String>();
    args.add("install");
    if (url != null) {
      args.add("--extra-index-url");
      args.add(url);
    }
    final File buildDir;
    try {
      buildDir = FileUtil.createTempDirectory("packaging", null);
    }
    catch (IOException e) {
      throw new PyExternalProcessException(ERROR_ACCESS_DENIED, "Cannot create temporary build directory");
    }
    args.addAll(list("--build-dir", buildDir.getAbsolutePath()));
    for (PyRequirement req : requirements) {
      args.add(req.toString());
    }
    if (options != null) {
      args.addAll(options);
    }
    try {
      runPythonHelper(PACKAGING_TOOL, args);
    }
    finally {
      myPackagesCache = null;
      FileUtil.delete(buildDir);
    }
  }

  private void install(@NotNull List<PyRequirement> requirements, @Nullable List<String> options) throws PyExternalProcessException {
    install(requirements, null, options);
  }

  public void uninstall(@NotNull PyPackage pkg) throws PyExternalProcessException {
    try {
      runPythonHelper(PACKAGING_TOOL, list("uninstall", pkg.getName()));
    }
    finally {
      myPackagesCache = null;
    }
  }

  @NotNull
  public List<PyPackage> getPackages() throws PyExternalProcessException {
    if (myPackagesCache == null) {
      final String output = runPythonHelper(PACKAGING_TOOL, list("list"));
      myPackagesCache = parsePackagingToolOutput(output);
      Collections.sort(myPackagesCache, new Comparator<PyPackage>() {
        @Override
        public int compare(PyPackage aPackage, PyPackage aPackage1) {
          return aPackage.getName().compareTo(aPackage1.getName());
        }
      });
    }
    return myPackagesCache;
  }

  @NotNull
  public String createVirtualEnv(@NotNull String desinationDir) throws PyExternalProcessException {
    // TODO: Add boolean systemSitePackages option
    runPythonHelper(VIRTUALENV, list("--never-download", "--distribute", desinationDir));
    final String binary = PythonSdkType.getPythonExecutable(desinationDir);
    final String binaryFallback = desinationDir + File.separator + "bin" + File.separator + "python";
    return (binary != null) ? binary : binaryFallback;
  }

  public static void deleteVirtualEnv(@NotNull String sdkHome) throws PyExternalProcessException {
    final File root = PythonSdkType.getVirtualEnvRoot(sdkHome);
    if (root == null) {
      throw new PyExternalProcessException(ERROR_INVALID_SDK, "Cannot find virtualenv root for interpreter");
    }
    FileUtil.delete(root);
  }

  @Nullable
  public static List<PyRequirement> getRequirements(@NotNull Module module) {
    // TODO: Cache requirements, clear cache on requirements.txt or setup.py updates
    final Document requirementsTxt = findRequirementsTxt(module);
    if (requirementsTxt != null) {
      return PyRequirement.parse(requirementsTxt.getText());
    }
    final PyListLiteralExpression installRequires = findSetupPyInstallRequires(module);
    if (installRequires != null) {
      final List<String> lines = new ArrayList<String>();
      for (PyExpression e : installRequires.getElements()) {
        if (e instanceof PyStringLiteralExpression) {
          lines.add(((PyStringLiteralExpression)e).getStringValue());
        }
      }
      return PyRequirement.parse(StringUtil.join(lines, "\n"));
    }
    return null;
  }

  @Nullable
  private static PyListLiteralExpression findSetupPyInstallRequires(@NotNull Module module) {
    final PyFile setupPy = findSetupPy(module);
    if (setupPy != null) {
      final PyCallExpression setup = findSetupCall(setupPy);
      if (setup != null) {
        for (PyExpression arg : setup.getArguments()) {
          if (arg instanceof PyKeywordArgument) {
            final PyKeywordArgument kwarg = (PyKeywordArgument)arg;
            if ("install_requires".equals(kwarg.getKeyword())) {
              final PyExpression value = kwarg.getValueExpression();
              if (value instanceof PyListLiteralExpression) {
                return (PyListLiteralExpression)value;
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyCallExpression findSetupCall(@NotNull PyFile file) {
    final Ref<PyCallExpression> result = new Ref<PyCallExpression>(null);
    file.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        final PyExpression callee = node.getCallee();
        final String name = PyUtil.getReadableRepr(callee, true);
        if ("setup".equals(name)) {
          result.set(node);
        }
      }

      @Override
      public void visitPyElement(PyElement node) {
        if (!(node instanceof ScopeOwner)) {
          super.visitPyElement(node);
        }
      }
    });
    return result.get();
  }

  @Nullable
  private static Document findRequirementsTxt(@NotNull Module module) {
    for (VirtualFile root : PyUtil.getSourceRoots(module)) {
      final VirtualFile child = root.findChild("requirements.txt");
      if (child != null) {
        return FileDocumentManager.getInstance().getDocument(child);
      }
    }
    return null;
  }

  @Nullable
  public static PyFile findSetupPy(@NotNull Module module) {
    for (VirtualFile root : PyUtil.getSourceRoots(module)) {
      final VirtualFile child = root.findChild("setup.py");
      if (child != null) {
        final PsiFile file = PsiManager.getInstance(module.getProject()).findFile(child);
        if (file instanceof PyFile) {
          return (PyFile)file;
        }
      }
    }
    return null;
  }

  public void clearCaches() {
    myPackagesCache = null;
  }

  private static <T> List<T> list(T... xs) {
    return Arrays.asList(xs);
  }

  @NotNull
  private String runPythonHelper(@NotNull final String helper,
                                 @NotNull final List<String> args) throws PyExternalProcessException {
    ProcessOutput output = getProcessOutput(helper, args);
    final int retcode = output.getExitCode();
    if (output.isTimeout()) {
      throw new PyExternalProcessException(ERROR_TIMEOUT, "Timed out");
    }
    else if (retcode != 0) {
      final String stdout = output.getStdout();
      final String stderr = output.getStderr();
      String message = stderr;
      if (message.trim().isEmpty()) {
        message = stdout;
      }
      final String header = String.format("Error when running '%s %s'", helper, StringUtil.join(args, " "));
      LOG.debug(String.format("%s\nSTDOUT: %s\nSTDERR: %s\n\n", header, stdout, stderr));
      throw new PyExternalProcessException(retcode, String.format("%s: %s", header, message));
    }
    return output.getStdout();
  }


  private ProcessOutput getProcessOutput(String helper, List<String> args) throws PyExternalProcessException {
    final SdkAdditionalData sdkData = mySdk.getSdkAdditionalData();
    if (sdkData instanceof PythonRemoteSdkAdditionalData) {
      final PythonRemoteSdkAdditionalData remoteSdkData = (PythonRemoteSdkAdditionalData)sdkData;
      final PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        final List<String> cmdline = new ArrayList<String>();
        cmdline.add(mySdk.getHomePath());
        cmdline.add(new File(remoteSdkData.getPyCharmTempFilesPath(),
                             helper).getPath());
        cmdline.addAll(args);
        try {
          return manager.runRemoteProcess(null, remoteSdkData, ArrayUtil.toStringArray(cmdline));
        }
        catch (PyRemoteInterpreterException e) {
          throw new PyExternalProcessException(ERROR_INVALID_SDK, "Error running sdk.");
        }
      }
      else {
        throw new PyExternalProcessException(ERROR_INVALID_SDK,
                                             "Remote interpreter can't be executed. Please enable WebDeployment plugin.");
      }
    }
    else {
      final String homePath = mySdk.getHomePath();
      if (homePath == null) {
        throw new PyExternalProcessException(ERROR_INVALID_SDK, "Cannot find interpreter for SDK");
      }
      final String helperPath = PythonHelpersLocator.getHelperPath(helper);
      if (helperPath == null) {
        throw new PyExternalProcessException(ERROR_HELPER_NOT_FOUND, String.format("Cannot find helper tool: '%s'", helper));
      }
      final String parentDir = new File(homePath).getParent();
      final List<String> cmdline = new ArrayList<String>();
      cmdline.add(homePath);
      cmdline.add(helperPath);
      cmdline.addAll(args);
      return PySdkUtil.getProcessOutput(parentDir, ArrayUtil.toStringArray(cmdline), TIMEOUT);
    }
  }

  @NotNull
  private static List<PyPackage> parsePackagingToolOutput(@NotNull String s) throws PyExternalProcessException {
    final String[] lines = StringUtil.splitByLines(s);
    final List<PyPackage> packages = new ArrayList<PyPackage>();
    for (String line : lines) {
      final List<String> fields = StringUtil.split(line, "\t");
      if (fields.size() < 3) {
        throw new PyExternalProcessException(ERROR_INVALID_OUTPUT, String.format("Invalid output format of '%s'", PACKAGING_TOOL));
      }
      final String name = fields.get(0);
      final String version = fields.get(1);
      final String location = fields.get(2);
      if (!"Python".equals(name) && !"wsgiref".equals(name)) {
        packages.add(new PyPackage(name, version, location, new ArrayList<PyRequirement>()));
      }
    }
    return packages;
  }
}
