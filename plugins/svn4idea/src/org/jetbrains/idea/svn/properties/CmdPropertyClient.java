// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.properties;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.*;
import org.jetbrains.idea.svn.info.Info;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class CmdPropertyClient extends BaseSvnClient implements PropertyClient {

  private static final Logger LOG = Logger.getInstance(CmdPropertyClient.class);

  @Nullable
  @Override
  public PropertyValue getProperty(@NotNull Target target,
                                   @NotNull String property,
                                   boolean revisionProperty,
                                   @Nullable Revision revision)
    throws SvnBindException {
    List<String> parameters = new ArrayList<>();

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

    PropertyData data = null;

    try {
      CommandExecutor command = execute(myVcs, target, SvnCommandName.propget, parameters, null);
      data = parseSingleProperty(target, command);
    }
    catch (SvnBindException e) {
      if (!isPropertyNotFoundError(e)) {
        throw e;
      }
    }

    return data != null ? data.getValue() : null;
  }

  @Override
  public void getProperty(@NotNull Target target,
                          @NotNull String property,
                          @Nullable Revision revision,
                          @Nullable Depth depth,
                          @Nullable PropertyConsumer handler) throws SvnBindException {
    List<String> parameters = new ArrayList<>();

    parameters.add(property);
    fillListParameters(target, revision, depth, parameters, false);

    try {
      CommandExecutor command = execute(myVcs, target, SvnCommandName.propget, parameters, null);
      parseOutput(target, command, handler);
    }
    catch (SvnBindException e) {
      if (!isPropertyNotFoundError(e)) {
        throw e;
      }
    }
  }

  @Override
  public void list(@NotNull Target target,
                   @Nullable Revision revision,
                   @Nullable Depth depth,
                   @Nullable PropertyConsumer handler) throws SvnBindException {
    List<String> parameters = new ArrayList<>();
    fillListParameters(target, revision, depth, parameters, true);

    CommandExecutor command = execute(myVcs, target, SvnCommandName.proplist, parameters, null);
    parseOutput(target, command, handler);
  }

  @Override
  public void setProperty(@NotNull File file,
                          @NotNull String property,
                          @Nullable PropertyValue value,
                          @Nullable Depth depth,
                          boolean force) throws SvnBindException {
    runSetProperty(Target.on(file), property, null, depth, value, force);
  }

  @Override
  public void setProperties(@NotNull File file, @NotNull PropertiesMap properties) throws SvnBindException {
    PropertiesMap currentProperties = collectPropertiesToDelete(file);
    currentProperties.putAll(properties);

    for (Map.Entry<String, PropertyValue> entry : currentProperties.entrySet()) {
      setProperty(file, entry.getKey(), entry.getValue(), Depth.EMPTY, true);
    }
  }

  /**
   * Such error is thrown (if there is no requested property on the given target) by svn 1.9 client.
   */
  private static boolean isPropertyNotFoundError(@NotNull SvnBindException e) {
    return e.contains(ErrorCode.BASE) && e.contains(ErrorCode.PROPERTY_NOT_FOUND);
  }

  @NotNull
  private PropertiesMap collectPropertiesToDelete(@NotNull File file) throws SvnBindException {
    final PropertiesMap result = new PropertiesMap();

    list(Target.on(file), null, Depth.EMPTY, new PropertyConsumer() {
      @Override
      public void handleProperty(File path, PropertyData property) {
        // null indicates property will be deleted
        result.put(property.getName(), null);
      }
    });

    return result;
  }

  @Override
  public void setRevisionProperty(@NotNull Target target,
                                  @NotNull String property,
                                  @NotNull Revision revision,
                                  @Nullable PropertyValue value,
                                  boolean force) throws SvnBindException {
    runSetProperty(target, property, revision, null, value, force);
  }

  private void runSetProperty(@NotNull Target target,
                              @NotNull String property,
                              @Nullable Revision revision,
                              @Nullable Depth depth,
                              @Nullable PropertyValue value,
                              boolean force) throws SvnBindException {
    boolean isDelete = value == null;
    Command command = newCommand(isDelete ? SvnCommandName.propdel : SvnCommandName.propset);

    command.put(property);
    if (revision != null) {
      command.put("--revprop");
      command.put(revision);
    }
    if (!isDelete) {
      command.setPropertyValue(value);
      // --force could only be used in "propset" command, but not in "propdel" command
      command.put("--force", force);
    }
    command.put(target);
    command.put(depth);

    execute(myVcs, target, null, command, null);
  }

  private void fillListParameters(@NotNull Target target,
                                  @Nullable Revision revision,
                                  @Nullable Depth depth,
                                  @NotNull List<String> parameters,
                                  boolean verbose) {
    CommandUtil.put(parameters, target);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, depth);
    parameters.add("--xml");
    CommandUtil.put(parameters, verbose, "--verbose");
  }

  @Nullable
  private PropertyData parseSingleProperty(Target target, @NotNull CommandExecutor command) throws SvnBindException {
    final PropertyData[] data = new PropertyData[1];
    PropertyConsumer handler = new PropertyConsumer() {
      @Override
      public void handleProperty(File path, PropertyData property) {
        data[0] = property;
      }

      @Override
      public void handleProperty(Url url, PropertyData property) {
        data[0] = property;
      }

      @Override
      public void handleProperty(long revision, PropertyData property) {
        data[0] = property;
      }
    };

    parseOutput(target, command, handler);

    return data[0];
  }

  private static void parseOutput(Target target, @NotNull CommandExecutor command, PropertyConsumer handler) throws SvnBindException {
    try {
      Properties properties = CommandUtil.parse(command.getOutput(), Properties.class);

      if (properties != null) {
        for (PropertiesTarget childInfo : properties.targets) {
          Target childTarget = SvnUtil.append(target, childInfo.path);
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
      LOG.error("Could not parse properties. Command: " + command.getCommandText() + ", Warning: " + command.getErrorOutput(),
                new Attachment("output.xml", command.getOutput()));
      throw new SvnBindException(e);
    }
  }

  private static void invokeHandler(@NotNull Target target, @Nullable PropertyData data, @Nullable PropertyConsumer handler)
    throws SvnBindException {
    if (handler != null && data != null) {
      if (target.isFile()) {
        handler.handleProperty(target.getFile(), data);
      } else {
        handler.handleProperty(target.getUrl(), data);
      }
    }
  }

  private static void invokeHandler(long revision, @Nullable PropertyData data, @Nullable PropertyConsumer handler)
    throws SvnBindException {
    if (handler != null && data != null) {
      handler.handleProperty(revision, data);
    }
  }

  @Nullable
  private static PropertyData create(@NotNull String property, @Nullable String value) {
    PropertyData result = null;

    // such behavior is required to compatibility with SVNKit as some logic in merge depends on
    // whether null property data or property data with empty string value is returned
    if (value != null) {
      result = new PropertyData(property, PropertyValue.create(value.trim()));
    }

    return result;
  }

  private Revision resolveRevisionNumber(@NotNull File path, @Nullable Revision revision) throws SvnBindException {
    long result = revision != null ? revision.getNumber() : -1;

    // base should be resolved manually - could not set revision to BASE to get revision property
    if (Revision.BASE.equals(revision)) {
      Info info = myVcs.getInfo(path, Revision.BASE);

      result = info != null ? info.getRevision().getNumber() : -1;
    }

    if (result == -1) {
      throw new SvnBindException(message("error.could.not.determine.revision.number.for.file.and.revision", path, revision));
    }

    return Revision.of(result);
  }

  @XmlRootElement(name = "properties")
  public static class Properties {

    @XmlElement(name = "target")
    public List<PropertiesTarget> targets = new ArrayList<>();

    @XmlElement(name = "revprops")
    public RevisionProperties revisionProperties;
  }

  public static class PropertiesTarget {

    @XmlAttribute(name = "path")
    public String path;

    @XmlElement(name = "property")
    public List<Property> properties = new ArrayList<>();
  }

  public static class RevisionProperties {

    @XmlAttribute(name = "rev")
    public String revision;

    @XmlElement(name = "property")
    public List<Property> properties = new ArrayList<>();

    public long revisionNumber() {
      return Long.parseLong(revision);
    }
  }

  public static class Property {
    @XmlAttribute(name = "name")
    public String name;

    @XmlValue
    public String value;
  }
}
