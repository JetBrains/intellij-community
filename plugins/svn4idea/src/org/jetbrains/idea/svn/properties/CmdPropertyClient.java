package org.jetbrains.idea.svn.properties;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdPropertyClient extends BaseSvnClient implements PropertyClient {

  @Nullable
  @Override
  public SVNPropertyData getProperty(@NotNull SvnTarget target,
                                     @NotNull String property,
                                     boolean revisionProperty,
                                     @Nullable SVNRevision revision)
    throws VcsException {
    List<String> parameters = new ArrayList<String>();

    parameters.add(property);
    if (!revisionProperty) {
      CommandUtil.put(parameters, target);
      CommandUtil.put(parameters, revision);
    } else {
      // currently revision properties are returned only for file targets
      assertFile(target);

      // "svn propget --revprop" treats '@' symbol at file path end as part of the path - so here we manually omit adding '@' at the end
      CommandUtil.put(parameters, target, false);
      parameters.add("--revprop");
      CommandUtil.put(parameters, resolveRevisionNumber(target.getFile(), revision));
    }
    // always use --xml option here - this allows to determine if property exists with empty value or property does not exist, which
    // is critical for some parts of merge logic
    parameters.add("--xml");

    CommandExecutor command = CommandUtil.execute(myVcs, target, SvnCommandName.propget, parameters, null);
    return parseSingleProperty(target, command.getOutput());
  }

  @Override
  public void getProperty(@NotNull SvnTarget target,
                          @NotNull String property,
                          @Nullable SVNRevision revision,
                          @Nullable SVNDepth depth,
                          @Nullable ISVNPropertyHandler handler) throws VcsException {
    List<String> parameters = new ArrayList<String>();

    parameters.add(property);
    fillListParameters(target, revision, depth, parameters, false);

    CommandExecutor command = CommandUtil.execute(myVcs, target, SvnCommandName.propget, parameters, null);
    parseOutput(target, command.getOutput(), handler);
  }

  @Override
  public void list(@NotNull SvnTarget target,
                   @Nullable SVNRevision revision,
                   @Nullable SVNDepth depth,
                   @Nullable ISVNPropertyHandler handler) throws VcsException {
    List<String> parameters = new ArrayList<String>();
    fillListParameters(target, revision, depth, parameters, true);

    CommandExecutor command = CommandUtil.execute(myVcs, target, SvnCommandName.proplist, parameters, null);
    parseOutput(target, command.getOutput(), handler);
  }

  @Override
  public void setProperty(@NotNull File file,
                          @NotNull String property,
                          @Nullable SVNPropertyValue value,
                          @Nullable SVNDepth depth,
                          boolean force) throws VcsException {
    runSetProperty(SvnTarget.fromFile(file), property, null, depth, value, force);
  }

  @Override
  public void setRevisionProperty(@NotNull SvnTarget target,
                                  @NotNull String property,
                                  @NotNull SVNRevision revision,
                                  @Nullable SVNPropertyValue value,
                                  boolean force) throws VcsException {
    runSetProperty(target, property, revision, null, value, force);
  }

  private void runSetProperty(@NotNull SvnTarget target,
                              @NotNull String property,
                              @Nullable SVNRevision revision,
                              @Nullable SVNDepth depth,
                              @Nullable SVNPropertyValue value,
                              boolean force) throws VcsException {
    List<String> parameters = new ArrayList<String>();
    boolean isDelete = value == null;

    parameters.add(property);
    if (revision != null) {
      parameters.add("--revprop");
      CommandUtil.put(parameters, revision);
    }
    if (!isDelete) {
      parameters.add(SVNPropertyValue.getPropertyAsString(value));
      // --force could only be used in "propset" command, but not in "propdel" command
      CommandUtil.put(parameters, force, "--force");
    }
    CommandUtil.put(parameters, target);
    CommandUtil.put(parameters, depth);

    // For some reason, command setting ignore property when working directory equals target directory (like
    // "svn propset svn:ignore *.java . --depth empty") tries to set ignore also on child files and fails with error like
    // "svn: E200009: Cannot set 'svn:ignore' on a file ('...File1.java')". So here we manually force home directory to be used.
    // NOTE: that setting other properties (not svn:ignore) does not cause such error.
    CommandUtil
      .execute(myVcs, target, CommandUtil.getHomeDirectory(), isDelete ? SvnCommandName.propdel : SvnCommandName.propset, parameters,
               null);
  }

  private void fillListParameters(@NotNull SvnTarget target,
                                  @Nullable SVNRevision revision,
                                  @Nullable SVNDepth depth,
                                  @NotNull List<String> parameters,
                                  boolean verbose) {
    CommandUtil.put(parameters, target);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, depth);
    parameters.add("--xml");
    CommandUtil.put(parameters, verbose, "--verbose");
  }

  private SVNPropertyData parseSingleProperty(SvnTarget target, String output) throws VcsException {
    final SVNPropertyData[] data = new SVNPropertyData[1];
    ISVNPropertyHandler handler = new ISVNPropertyHandler() {
      @Override
      public void handleProperty(File path, SVNPropertyData property) throws SVNException {
        data[0] = property;
      }

      @Override
      public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        data[0] = property;
      }

      @Override
      public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        data[0] = property;
      }
    };

    parseOutput(target, output, handler);

    return data[0];
  }

  private static void parseOutput(SvnTarget target, String output, ISVNPropertyHandler handler) throws VcsException {
    try {
      Properties properties = CommandUtil.parse(output, Properties.class);

      if (properties != null) {
        for (Target childInfo : properties.targets) {
          SvnTarget childTarget = append(target, childInfo.path);
          for (Property property : childInfo.properties) {
            invokeHandler(childTarget, create(property.name, property.value), handler);
          }
        }

        if (properties.revisionProperties != null) {
          for (Property property : properties.revisionProperties.properties) {
            invokeHandler(properties.revisionProperties.revisionNumber(), create(property.name, property.value), handler);
          }
        }
      }
    }
    catch (JAXBException e) {
      throw new VcsException(e);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  // TODO: Create custom Target class and implement append there
  private static SvnTarget append(@NotNull SvnTarget target, @NotNull String path) throws SVNException {
    SvnTarget result;

    if (target.isFile()) {
      result = SvnTarget.fromFile(FileUtil.isAbsolute(path) ? new File(path) : new File(target.getFile(), path));
    } else {
      result = SvnTarget.fromURL(target.getURL().appendPath(path, false));
    }

    return result;
  }

  private static void invokeHandler(@NotNull SvnTarget target, @Nullable SVNPropertyData data, @Nullable ISVNPropertyHandler handler)
    throws SVNException {
    if (handler != null && data != null) {
      if (target.isFile()) {
        handler.handleProperty(target.getFile(), data);
      } else {
        handler.handleProperty(target.getURL(), data);
      }
    }
  }

  private static void invokeHandler(long revision, @Nullable SVNPropertyData data, @Nullable ISVNPropertyHandler handler)
    throws SVNException {
    if (handler != null && data != null) {
      handler.handleProperty(revision, data);
    }
  }

  @Nullable
  private static SVNPropertyData create(@NotNull String property, @Nullable String value) {
    SVNPropertyData result = null;

    // such behavior is required to compatibility with SVNKit as some logic in merge depends on
    // whether null property data or property data with empty string value is returned
    if (value != null) {
      result = new SVNPropertyData(property, SVNPropertyValue.create(value.trim()), LF_SEPARATOR_OPTIONS);
    }

    return result;
  }

  private SVNRevision resolveRevisionNumber(@NotNull File path, @Nullable SVNRevision revision) throws VcsException {
    long result = revision != null ? revision.getNumber() : -1;

    // base should be resolved manually - could not set revision to BASE to get revision property
    if (SVNRevision.BASE.equals(revision)) {
      SVNInfo info = myVcs.getInfo(path, SVNRevision.BASE);

      result = info != null ? info.getRevision().getNumber() : -1;
    }

    if (result == -1) {
      throw new VcsException("Could not determine revision number for file " + path + " and revision " + revision);
    }

    return SVNRevision.create(result);
  }

  @XmlRootElement(name = "properties")
  public static class Properties {

    @XmlElement(name = "target")
    public List<Target> targets = new ArrayList<Target>();

    @XmlElement(name = "revprops")
    public RevisionProperties revisionProperties;
  }

  public static class Target {

    @XmlAttribute(name = "path")
    public String path;

    @XmlElement(name = "property")
    public List<Property> properties = new ArrayList<Property>();
  }

  public static class RevisionProperties {

    @XmlAttribute(name = "rev")
    public String revision;

    @XmlElement(name = "property")
    public List<Property> properties = new ArrayList<Property>();

    public long revisionNumber() {
      return Long.valueOf(revision);
    }
  }

  public static class Property {
    @XmlAttribute(name = "name")
    public String name;

    @XmlValue
    public String value;
  }
}
