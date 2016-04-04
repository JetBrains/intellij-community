package org.jetbrains.idea.maven.server;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;
import org.jdom.*;
import org.jdom.filter.ElementFilter;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenEffectivePomDumper {

  /**
   * The POM XSD URL
   */
  private static final String POM_XSD_URL = "http://maven.apache.org/maven-v4_0_0.xsd";

  /**
   * The Settings XSD URL
   */
  private static final String SETTINGS_XSD_URL = "http://maven.apache.org/xsd/settings-1.0.0.xsd";

  // See org.apache.maven.plugins.help.EffectivePomMojo#execute from maven-help-plugin
  @Nullable
  public static String evaluateEffectivePom(final Maven3ServerEmbedder embedder,
                                            @NotNull final File file,
                                            @NotNull List<String> activeProfiles,
                                            @NotNull List<String> inactiveProfiles)
    throws RemoteException, MavenServerProcessCanceledException {

    final StringWriter w = new StringWriter();

    try {
      final MavenExecutionRequest request = embedder.createRequest(file, activeProfiles, inactiveProfiles, Collections.<String>emptyList());

      embedder.executeWithMavenSession(request, new Runnable() {
        @Override
        public void run() {
          try {
            // copied from DefaultMavenProjectBuilder.buildWithDependencies
            ProjectBuilder builder = embedder.getComponent(ProjectBuilder.class);
            ProjectBuildingResult buildingResult = builder.build(new File(file.getPath()), request.getProjectBuildingRequest());

            MavenProject project = buildingResult.getProject();

            XMLWriter writer = new PrettyPrintXMLWriter(new PrintWriter(w), StringUtils.repeat(" ", XmlWriterUtil.DEFAULT_INDENTATION_SIZE),
                                                        "\n", null, null);

            writeHeader(writer);

            writeEffectivePom(project, writer);
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
    catch (Exception e) {
      return null;
    }

    return w.toString();
  }

  /**
   * org.apache.maven.plugins.help.EffectivePomMojo#writeEffectivePom
   */
  private static void writeEffectivePom(MavenProject project, XMLWriter writer)
    throws MojoExecutionException {
    Model pom = project.getModel();
    cleanModel(pom);

    String effectivePom;

    StringWriter sWriter = new StringWriter();
    MavenXpp3Writer pomWriter = new MavenXpp3Writer();
    try {
      pomWriter.write(sWriter, pom);
    }
    catch (IOException e) {
      throw new MojoExecutionException("Cannot serialize POM to XML.", e);
    }

    effectivePom = addMavenNamespace(sWriter.toString(), true);

    writeComment(writer, "Effective POM for project \'" + project.getId() + "\'");

    writer.writeMarkup(effectivePom);
  }

  /**
   * org.jetbrains.idea.maven.server.MavenEffectivePomDumper#cleanModel(org.apache.maven.model.Model)
   */
  private static void cleanModel(Model pom) {
    Properties properties = new SortedProperties();
    properties.putAll(pom.getProperties());
    pom.setProperties(properties);
  }


  /**
   * Copy/pasted from org.apache.maven.plugins.help.AbstractEffectiveMojo#writeHeader
   */
  protected static void writeHeader(XMLWriter writer) {
    XmlWriterUtil.writeCommentLineBreak(writer);
    XmlWriterUtil.writeComment(writer, " ");
    // Use ISO8601-format for date and time
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");
    XmlWriterUtil.writeComment(writer, "Generated on " + dateFormat.format(new Date(System.currentTimeMillis())));
    XmlWriterUtil.writeComment(writer, " ");
    XmlWriterUtil.writeCommentLineBreak(writer);

    XmlWriterUtil.writeLineBreak(writer);
  }

  /**
   * Copy/pasted from org.apache.maven.plugins.help.AbstractEffectiveMojo
   */
  protected static void writeComment(XMLWriter writer, String comment) {
    XmlWriterUtil.writeCommentLineBreak(writer);
    XmlWriterUtil.writeComment(writer, " ");
    XmlWriterUtil.writeComment(writer, comment);
    XmlWriterUtil.writeComment(writer, " ");
    XmlWriterUtil.writeCommentLineBreak(writer);

    XmlWriterUtil.writeLineBreak(writer);
  }

  private static boolean hasLineBreak(Element e) {
    return !e.getChildren().isEmpty() || e.getText().contains("\n");
  }

  private static boolean isOneEOFText(String text) {
    int eof = text.indexOf('\n');
    return eof != -1 && eof == text.lastIndexOf('\n') && text.trim().isEmpty();
  }

  private static void addLineBreaks(Element element) {
    List<Content> children = element.getContent();

    for (int i = 0; i < children.size() - 2; i++) {
      Content c1 = children.get(i);
      Content c2 = children.get(i + 1);
      Content c3 = children.get(i + 2);

      if (c1 instanceof Element && c2 instanceof Text && c3 instanceof Element
          && (hasLineBreak((Element)c1) || hasLineBreak((Element)c3))
          && isOneEOFText(((Text)c2).getText())) {
        element.setContent(i + 1, new Text(((Text)c2).getText().replace("\n", "\n\n")));
      }
    }
  }

  private static void addLineBreaks(Document pomXml, Namespace pomNamespace) {
    Element rootElement = pomXml.getRootElement();

    addLineBreaks(rootElement);

    Element buildElement = rootElement.getChild("build", pomNamespace);
    if (buildElement != null) {
      addLineBreaks(buildElement);
    }
  }

  /**
   * Copy/pasted from org.apache.maven.plugins.help.AbstractEffectiveMojo
   */
  protected static String addMavenNamespace(String effectiveXml, boolean isPom) {
    SAXBuilder builder = new SAXBuilder();

    try {
      Document document = builder.build(new StringReader(effectiveXml));
      Element rootElement = document.getRootElement();

      // added namespaces
      Namespace pomNamespace = Namespace.getNamespace("", "http://maven.apache.org/POM/4.0.0");
      rootElement.setNamespace(pomNamespace);

      Namespace xsiNamespace = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
      rootElement.addNamespaceDeclaration(xsiNamespace);
      if (rootElement.getAttribute("schemaLocation", xsiNamespace) == null) {
        rootElement.setAttribute("schemaLocation", "http://maven.apache.org/POM/4.0.0 "
                                                   + (isPom ? POM_XSD_URL : SETTINGS_XSD_URL), xsiNamespace);
      }

      ElementFilter elementFilter = new ElementFilter(Namespace.getNamespace(""));
      for (Iterator i = rootElement.getDescendants(elementFilter); i.hasNext(); ) {
        Element e = (Element)i.next();
        e.setNamespace(pomNamespace);
      }

      addLineBreaks(document, pomNamespace);

      StringWriter w = new StringWriter();
      Format format = Format.getRawFormat();
      XMLOutputter out = new XMLOutputter(format);
      out.output(document.getRootElement(), w);

      return w.toString();
    }
    catch (JDOMException e) {
      return effectiveXml;
    }
    catch (IOException e) {
      return effectiveXml;
    }
  }

  /**
   * Properties which provides a sorted keySet().
   */
  protected static class SortedProperties
    extends Properties {
    /**
     * serialVersionUID
     */
    static final long serialVersionUID = -8985316072702233744L;

    /**
     * {@inheritDoc}
     */
    public Set keySet() {
      Set keynames = super.keySet();
      Vector list = new Vector(keynames);
      Collections.sort(list);

      return new LinkedHashSet(list);
    }
  }
}
