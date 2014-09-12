package org.jetbrains.plugins.ipnb;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.MarkdownUtil;
import com.petebevin.markdown.MarkdownProcessor;
import net.sourceforge.jeuclid.MathMLParserSupport;
import net.sourceforge.jeuclid.context.LayoutContextImpl;
import net.sourceforge.jeuclid.context.Parameter;
import net.sourceforge.jeuclid.converter.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbTexPackageDefinitions;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import uk.ac.ed.ph.snuggletex.SnuggleEngine;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleSession;
import uk.ac.ed.ph.snuggletex.XMLStringOutputOptions;

import javax.swing.*;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IpnbUtils {
  private static final Logger LOG = Logger.getInstance(IpnbUtils.class);
  private static MarkdownProcessor ourMarkdownProcessor = new MarkdownProcessor();

  public static String markdown2Html(@NotNull final String description) {
    // TODO: add links to the dependant notebook pages (see: index.ipynb)
    //TODO: relative picture links (see: index.ipynb in IPython.kernel) We should use absolute file:/// path
    final List<String> lines = ContainerUtil.newArrayList(description.split("\n|\r|\r\n"));
    final List<String> processedLines = new ArrayList<String>();
    boolean isInCode = false;
    for (String line : lines) {
      String processedLine = line;
      if (line.startsWith(" ")) {
        processedLine = line.substring(1);
      }
      processedLine = processedLine
        .replace("<pre>", "```").replace("</pre>", "```")
        .replace("<code>", "```").replace("</code>", "```");
      if (processedLine.contains("```")) isInCode = !isInCode;
      if (isInCode) {
        processedLine = processedLine
          .replace("&", "&amp;")
          .replace("<", "&lt;")
          .replace(">", "&gt;");
      }
      else {
        processedLine = processedLine
          .replaceAll("([\\w])_([\\w])", "$1&underline;$2");
      }
      processedLines.add(processedLine);
    }
    MarkdownUtil.replaceHeaders(processedLines);
    MarkdownUtil.removeImages(processedLines);
    MarkdownUtil.generateLists(processedLines);
    //MarkdownUtil.replaceCodeBlock(processedLines);
    final String[] lineArray = ArrayUtil.toStringArray(processedLines);
    final String normalizedMarkdown = StringUtil.join(lineArray, "\n");
    String html = ourMarkdownProcessor.markdown(normalizedMarkdown);
    html = html
      .replace("<pre><code>", "<pre>").replace("</code></pre>", "</pre>")
      .replace("<em>", "<i>").replace("</em>", "</i>")
      .replace("<strong>", "<b>").replace("</strong>", "</b>")
      .replace("&underline;", "_")
      .trim();
    return html;
  }

  public static void addFormulaToPanel(@NotNull final String[] source, @NotNull final JPanel panel) {
    StringBuilder formula = new StringBuilder();
    boolean hasFormula = false;
    boolean isEscaped = false;
    boolean inFormula = false;
    if (isStyleOrScript(StringUtil.join(source))) return;
    for (String string : source) {
      string = StringUtil.replace(string, "\\(", "(");
      string = StringUtil.replace(string, "\\)", ")");
      if (string.startsWith("```") && !isEscaped) {
        isEscaped = true;
      }
      else if (StringUtil.trimTrailing(string).endsWith("```") && isEscaped) {
        isEscaped = false;
      }

      string = string.replace("\n", " \n");
      if (string.contains("{align}"))
        string = string.replace("{align}", "{eqnarray*}");
      if (string.contains("{equation*}"))
        string = string.replace("{equation*}", "{eqnarray*}");


      if ((StringUtil.trimTrailing(string).endsWith("$$")) && inFormula) {
        inFormula = false;
        string = StringUtil.trimTrailing(string);
        formula.append(string);
        addFormula(panel, formula.toString());
        hasFormula = false;
        formula = new StringBuilder();
      }
      else if (string.trim().startsWith("$$") && !isEscaped) {
        formula.append(string);
        hasFormula = true;
        inFormula = true;
      }
      else if (string.startsWith("\\") && !isEscaped || inFormula) {
        inFormula = true;
        hasFormula = true;
        formula.append(string);
      }
      else {
        if (hasFormula) {
          addFormula(panel, formula.toString());
          hasFormula = false;
          formula = new StringBuilder();
        }
        else {
          if (!StringUtil.isEmptyOrSpaces(string))
            addMarkdown(panel, string, isEscaped);
        }
      }
    }
    if (hasFormula) {
      addFormula(panel, formula.toString());
    }
  }



  private static void addFormula(@NotNull final JPanel panel, @NotNull final String formulaText) {
    final SnuggleEngine engine = new SnuggleEngine();
    engine.getPackages().add(0, IpnbTexPackageDefinitions.getPackage());

    final SnuggleSession session = engine.createSession();

    final SnuggleInput input = new SnuggleInput(formulaText);

    try {
      session.parseInput(input);
      XMLStringOutputOptions options = new XMLStringOutputOptions();
      options.setIndenting(true);
      options.setAddingMathSourceAnnotations(false);
      final String xmlString = session.buildXMLString(options);
      if (xmlString == null) return;
      final LayoutContextImpl context = (LayoutContextImpl)LayoutContextImpl.getDefaultLayoutContext();
      context.setParameter(Parameter.MATHSIZE, 18);

      final Document document = MathMLParserSupport.parseString(xmlString);

      final BufferedImage image = Converter.getInstance().render(document, context);
      final JLabel label = new JLabel();
      label.setIcon(new ImageIcon(image));
      panel.add(label);

    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (SAXException e) {
      LOG.error(e);
    }
    catch (ParserConfigurationException e) {
      LOG.error(e);
    }
  }

  private static void addMarkdown(@NotNull final JPanel panel, String string, boolean isEscaped) {
    string = StringUtil.trimStart(string, "```");
    string = StringUtil.trimTrailing(string);
    string = StringUtil.trimEnd(string, "```");
    if (!isEscaped)
      string = IpnbUtils.markdown2Html(string);
    else
      string = "<p>"+string+"</p>";
    final JLabel comp = new JLabel("<html><body style='width: " + IpnbEditorUtil.PANEL_WIDTH + "px'>" + string + "</body></html>");
    final Font font = new Font(Font.SERIF, Font.PLAIN, 16);
    comp.setFont(font);
    panel.add(comp);
  }

  public static boolean isStyleOrScript(String string) {
    return string.contains("<style>") || string.contains("<script>");
  }

}
