package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
  @Nullable private volatile SVNURL myRepositoryUrl;
  @NotNull private SvnTarget myTarget;
  @Nullable private Collection<File> myTargets;
  @Nullable private PropertyValue myPropertyValue;

  @Nullable private ProgressTracker myCanceller;

  public Command(@NotNull SvnCommandName name) {
    myName = name;
  }

  public void put(@Nullable Depth depth) {
    CommandUtil.put(myParameters, depth, false);
  }

  public void put(@NotNull SvnTarget target) {
    CommandUtil.put(myParameters, target);
  }

  public void put(@Nullable SVNRevision revision) {
    CommandUtil.put(myParameters, revision);
  }

  public void put(@NotNull String parameter, boolean condition) {
    CommandUtil.put(myParameters, condition, parameter);
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
  public ProgressTracker getCanceller() {
    return myCanceller;
  }

  public void setCanceller(@Nullable ProgressTracker canceller) {
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
  public SVNURL requireRepositoryUrl() {
    SVNURL result = getRepositoryUrl();
    assert result != null;

    return result;
  }

  @NotNull
  public SvnTarget getTarget() {
    return myTarget;
  }

  @Nullable
  public List<String> getTargetsPaths() {
    return ContainerUtil.isEmpty(myTargets) ? null : ContainerUtil.map(myTargets, new Function<File, String>() {
      @Override
      public String fun(File file) {
        return CommandUtil.format(file.getAbsolutePath(), null);
      }
    });
  }

  @Nullable
  public PropertyValue getPropertyValue() {
    return myPropertyValue;
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

  public void setTargets(@Nullable Collection<File> targets) {
    myTargets = targets;
  }

  public void setPropertyValue(@Nullable PropertyValue propertyValue) {
    myPropertyValue = propertyValue;
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
    List<String> data = new ArrayList<>();

    if (myConfigDir != null) {
      data.add("--config-dir");
      data.add(myConfigDir.getPath());
    }
    data.add(myName.getName());
    data.addAll(myOriginalParameters);

    List<String> targetsPaths = getTargetsPaths();
    if (!ContainerUtil.isEmpty(targetsPaths)) {
      data.addAll(targetsPaths);
    }

    return StringUtil.join(data, " ");
  }

  public boolean isLocalInfo() {
    return is(SvnCommandName.info) && hasLocalTarget() && !myParameters.contains("--revision");
  }

  public boolean isLocalStatus() {
    return is(SvnCommandName.st) && hasLocalTarget() && !myParameters.contains("-u");
  }

  public boolean isLocalProperty() {
    boolean isPropertyCommand =
      is(SvnCommandName.proplist) || is(SvnCommandName.propget) || is(SvnCommandName.propset) || is(SvnCommandName.propdel);

    return isPropertyCommand && hasLocalTarget() && isLocal(getRevision());
  }

  public boolean isLocalCat() {
    return is(SvnCommandName.cat) && hasLocalTarget() && isLocal(getRevision());
  }

  @Nullable
  private SVNRevision getRevision() {
    int index = myParameters.indexOf("--revision");

    return index >= 0 && index + 1 < myParameters.size() ? SVNRevision.parse(myParameters.get(index + 1)) : null;
  }

  public boolean is(@NotNull SvnCommandName name) {
    return name.equals(myName);
  }

  private boolean hasLocalTarget() {
    return myTarget.isFile() && isLocal(myTarget.getPegRevision());
  }

  private static boolean isLocal(@Nullable SVNRevision revision) {
    return revision == null ||
           SVNRevision.UNDEFINED.equals(revision) ||
           SVNRevision.BASE.equals(revision) ||
           SVNRevision.WORKING.equals(revision);
  }
}