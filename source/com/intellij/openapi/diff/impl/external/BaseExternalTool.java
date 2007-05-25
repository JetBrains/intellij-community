package com.intellij.openapi.diff.impl.external;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.StringProperty;

import java.io.File;
import java.io.IOException;

abstract class BaseExternalTool implements DiffTool {
  private final BooleanProperty myEnableProperty;
  private final StringProperty myToolProperty;

  protected BaseExternalTool(BooleanProperty enableProperty, StringProperty toolProperty) {
    myEnableProperty = enableProperty;
    myToolProperty = toolProperty;
  }

  public boolean canShow(DiffRequest request) {
    AbstractProperty.AbstractPropertyContainer config = DiffManagerImpl.getInstanceEx().getProperties();
    if (!myEnableProperty.value(config)) return false;
    String path = getToolPath();
    if (path == null || path.length() == 0) return false;
    DiffContent[] contents = request.getContents();
    if (contents.length != 2) return false;
    if (externalize(request, 0) == null) return false;
    if (externalize(request, 1) == null) return false;
    return true;
  }

  protected abstract ContentExternalizer externalize(DiffRequest request, int index);

  private String getToolPath() {
    return myToolProperty.get(DiffManagerImpl.getInstanceEx().getProperties());
  }

  public void show(DiffRequest request) {
    //ArrayList<String> commandLine = new ArrayList<String>();
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(getToolPath());
    try {
      commandLine.addParameter(convertToPath(request, 0));
      commandLine.addParameter(convertToPath(request, 1));
      Runtime.getRuntime().exec(commandLine.getCommands());
    } catch (IOException e) {
      ExecutionErrorDialog.show(new ExecutionException(e.getMessage()),
                                DiffBundle.message("cant.launch.diff.tool.error.message"), request.getProject());
    }
  }

  private String convertToPath(DiffRequest request, int index) throws IOException {
    return externalize(request, index).getContentFile().getAbsolutePath();
  }

  protected VirtualFile getLocalFile(VirtualFile file) {
    if (file != null && (file.getFileSystem() instanceof LocalFileSystem)) return file;
    return null;
  }

  protected interface ContentExternalizer {
    File getContentFile() throws IOException;
  }

  protected static class LocalFileExternalizer implements ContentExternalizer {
    private final File myFile;

    public LocalFileExternalizer(File file) {
      myFile = file;
    }

    public File getContentFile() {
      return myFile;
    }

    public static LocalFileExternalizer tryCreate(VirtualFile file) {
      if (file == null || !file.isValid()) return null;
      if (!(file.getFileSystem() instanceof LocalFileSystem)) return null;
      return new LocalFileExternalizer(new File(file.getPath().replace('/', File.separatorChar)));
    }
  }
}
