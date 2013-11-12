package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
// TODO: Probably make command immutable and use CommandBuilder for updates.
public class Command {

  @NotNull private final List<String> myParameters = ContainerUtil.newArrayList();
  @NotNull private final List<String> myOriginalParameters = ContainerUtil.newArrayList();
  @NotNull private final SvnCommandName myName;

  private File workingDirectory;
  @Nullable private File myConfigDir;
  @Nullable private LineCommandListener myResultBuilder;
  @Nullable private SVNURL myRepositoryUrl;
  @NotNull private SvnTarget myTarget;

  @Nullable private ISVNCanceller myCanceller;

  public Command(@NotNull SvnCommandName name) {
    myName = name;
  }

  public void put(@NonNls @NotNull String... parameters) {
    put(Arrays.asList(parameters));
  }

  public void put(@NotNull List<String> parameters) {
    myParameters.addAll(parameters);
  }

  public void putIfNotPresent(@NotNull String parameter) {
    if (!myParameters.contains(parameter)) {
      myParameters.add(parameter);
    }
  }

  @Nullable
  public ISVNCanceller getCanceller() {
    return myCanceller;
  }

  public void setCanceller(@Nullable ISVNCanceller canceller) {
    myCanceller = canceller;
  }

  @Nullable
  public File getConfigDir() {
    return myConfigDir;
  }

  public File getWorkingDirectory() {
    return workingDirectory;
  }

  @Nullable
  public LineCommandListener getResultBuilder() {
    return myResultBuilder;
  }

  @Nullable
  public SVNURL getRepositoryUrl() {
    return myRepositoryUrl;
  }

  @NotNull
  public SvnTarget getTarget() {
    return myTarget;
  }

  @NotNull
  public SvnCommandName getName() {
    return myName;
  }

  public void setWorkingDirectory(File workingDirectory) {
    this.workingDirectory = workingDirectory;
  }

  public void setConfigDir(@Nullable File configDir) {
    this.myConfigDir = configDir;
  }

  public void setResultBuilder(@Nullable LineCommandListener resultBuilder) {
    myResultBuilder = resultBuilder;
  }

  public void setRepositoryUrl(@Nullable SVNURL repositoryUrl) {
    myRepositoryUrl = repositoryUrl;
  }

  public void setTarget(@NotNull SvnTarget target) {
    myTarget = target;
  }

  // TODO: used only to ensure authentication info is not logged to file. Remove when command execution model is refactored
  // TODO: - so we could determine if parameter should be logged by the parameter itself.
  public void saveOriginalParameters() {
    myOriginalParameters.clear();
    myOriginalParameters.addAll(myParameters);
  }

  @NotNull
  public List<String> getParameters() {
    return ContainerUtil.newArrayList(myParameters);
  }

  public String getText() {
    List<String> data = new ArrayList<String>();

    if (myConfigDir != null) {
      data.add("--config-dir");
      data.add(myConfigDir.getPath());
    }
    data.add(myName.getName());
    data.addAll(myOriginalParameters);

    return StringUtil.join(data, " ");
  }
}