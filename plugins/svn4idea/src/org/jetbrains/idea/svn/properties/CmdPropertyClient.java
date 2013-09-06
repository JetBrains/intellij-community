package org.jetbrains.idea.svn.properties;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommand;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
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
  public SVNPropertyData getProperty(@NotNull File path,
                                     @NotNull String property,
                                     boolean revisionProperty,
                                     @Nullable SVNRevision pegRevision,
                                     @Nullable SVNRevision revision)
    throws VcsException {
    List<String> parameters = new ArrayList<String>();

    parameters.add(property);
    CommandUtil.put(parameters, path, pegRevision);
    if (!revisionProperty) {
      CommandUtil.put(parameters, revision);
    } else {
      parameters.add("--revprop");
      CommandUtil.put(parameters, resolveRevisionNumber(path, revision));
    }

    SvnCommand command = CommandUtil.execute(myVcs, SvnCommandName.propget, parameters, null);

    return create(property, command.getOutput());
  }

  @Override
  public void list(@NotNull SvnTarget target,
                   @Nullable SVNRevision revision,
                   @Nullable SVNDepth depth,
                   @Nullable ISVNPropertyHandler handler) throws VcsException {
    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, target);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, depth);
    parameters.add("--xml");
    parameters.add("--verbose");

    SvnCommand command = CommandUtil.execute(myVcs, SvnCommandName.proplist, parameters, null);

    parseOutput(target, command.getOutput(), handler);
  }

  private static void parseOutput(SvnTarget target, String output, ISVNPropertyHandler handler) throws VcsException {
    try {
      Properties properties = CommandUtil.parse(output, Properties.class);

      if (properties != null) {
        for (Target childInfo : properties.targets) {
          // TODO: path could be either relative or absolute - track this
          SvnTarget childTarget = append(target, childInfo.path);
          for (Property property : childInfo.properties) {
            invokeHandler(childTarget, create(property.name, property.value), handler);
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
      result = SvnTarget.fromFile(new File(target.getFile(), path));
    } else {
      result = SvnTarget.fromURL(target.getURL().appendPath(path, false));
    }

    return result;
  }

  private static void invokeHandler(@NotNull SvnTarget target, @NotNull SVNPropertyData data, @Nullable ISVNPropertyHandler handler)
    throws SVNException {
    if (handler != null) {
      if (target.isFile()) {
        handler.handleProperty(target.getFile(), data);
      } else {
        handler.handleProperty(target.getURL(), data);
      }
    }
  }

  private static SVNPropertyData create(@NotNull String property, @Nullable String value) {
    return new SVNPropertyData(property, SVNPropertyValue.create(StringUtil.nullize(value)), null);
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
  }

  public static class Target {

    @XmlAttribute(name = "path")
    public String path;

    @XmlElement(name = "property")
    public List<Property> properties = new ArrayList<Property>();
  }

  public static class Property {
    @XmlAttribute(name = "name")
    public String name;

    @XmlValue
    public String value;
  }
}
