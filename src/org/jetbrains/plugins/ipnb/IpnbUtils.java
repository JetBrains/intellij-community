package org.jetbrains.plugins.ipnb;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
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
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
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

  public static void addLatexToPanel(@NotNull final String[] source, @NotNull final JPanel panel) {
    StringBuilder formula = new StringBuilder();
    List<String> markdown = new ArrayList<String>();
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

      if (inFormula && !markdown.isEmpty()) {
        addMarkdown(panel, markdown);
        markdown.clear();
      }
      if ((StringUtil.trimTrailing(string).endsWith("$$")) && inFormula) {
        inFormula = false;
        string = StringUtil.trimTrailing(string);
        string = prepareLatex(string);
        formula.append(string);
        addFormula(panel, formula.toString());
        hasFormula = false;
        formula = new StringBuilder();
      }
      else if (string.trim().startsWith("$$") && !isEscaped) {
        string = prepareLatex(string);
        formula.append(string);
        hasFormula = true;
        inFormula = true;
      }
      else if (string.startsWith("\\") && !isEscaped || inFormula) {
        inFormula = true;
        hasFormula = true;
        string = prepareLatex(string);
        formula.append(string);
      }
      else {
        if (hasFormula) {
          addFormula(panel, formula.toString());
          hasFormula = false;
          formula = new StringBuilder();
        }
        else {
          if (!StringUtil.isEmptyOrSpaces(string)) {
            markdown.add(string);
          }
        }
      }
    }
    if (hasFormula) {
      addFormula(panel, formula.toString());
    }
    if (!markdown.isEmpty()) {
      addMarkdown(panel, markdown);
    }
  }

  private static String prepareLatex(@NotNull String string) {
    string = string.replace("\n", " \n");
    if (string.contains("{align}"))
      string = string.replace("{align}", "{eqnarray*}");
    if (string.contains("{equation*}"))
      string = string.replace("{equation*}", "{eqnarray*}");
    return string;
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

  private static void addMarkdown(@NotNull final JPanel panel, List<String> strings) {
    StringBuilder stringBuilder = new StringBuilder();
    boolean isEscaped = false;
    for (String string : strings) {
      if (string.startsWith("```") && !isEscaped) {
        isEscaped = true;
        string = StringUtil.trimStart(string, "```");
        stringBuilder.append("<p>").append(string).append("</p>");
      }
      else if (StringUtil.trimTrailing(string).endsWith("```") && isEscaped) {
        isEscaped = false;
        string = StringUtil.trimTrailing(string);
        string = StringUtil.trimEnd(string, "```");
        stringBuilder.append("<p>").append(string).append("</p>");
      }
      else if (!isEscaped) {
        stringBuilder.append(IpnbUtils.markdown2Html(string));
      }
      else {
        stringBuilder.append("<p>").append(string).append("</p>");
      }

    }

    final JEditorPane editorPane = new JEditorPane(new HTMLEditorKit().getContentType(), "<html><body style='width: " +
                                                                                         IpnbEditorUtil.PANEL_WIDTH +
                                                                                         "px'>" +
                                                                                         stringBuilder.toString() +
                                                                                         "</body></html>");
    final Font font = new Font(Font.SERIF, Font.PLAIN, 16);
    String bodyRule = "body { font-family: " + font.getFamily() + "; " +
                      "font-size: " + font.getSize() + "pt; }";
    ((HTMLDocument)editorPane.getDocument()).getStyleSheet().addRule(bodyRule);

    editorPane.setEditable(false);

    editorPane.addHyperlinkListener(new BrowserHyperlinkListener());

    panel.add(editorPane);
  }

  public static boolean isStyleOrScript(@NotNull final String string) {
    return string.contains("<style>") || string.contains("<script>");
  }

}
