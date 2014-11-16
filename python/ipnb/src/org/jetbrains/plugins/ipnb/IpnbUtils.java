package org.jetbrains.plugins.ipnb;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.MarkdownUtil;
import com.intellij.util.ui.UIUtil;
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
import javax.swing.text.html.StyleSheet;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;

public class IpnbUtils {
  private static final Logger LOG = Logger.getInstance(IpnbUtils.class);
  private static final MarkdownProcessor ourMarkdownProcessor = new MarkdownProcessor();
  private static final String ourImagePrefix = "http://image";
  private static final Font ourFont = new Font(Font.SERIF, Font.PLAIN, 16);
  private static final String ourBodyRule = "body { font-family: \"DejaVu\"; " +
                                         "font-size: " + ourFont.getSize() + "pt;}";

  private static final String ourCodeRule = "code { font-family: \"DejaVu\"; " +
                                         "font-size: " + ourFont.getSize() + "pt;}";

  private static final String ourAlertRule = ".alert{ background-color: #fcf8e3;" +
                                                     "color: #c09853;" +
                                                     "padding: 8px 35px 8px 14px;" +
                                                     "border: 1px solid #fbeed5;}";

  private static final String ourAlertSuccessRule = ".alert-success{ background-color: #dff0d8;\n" +
                                                    "color: #468847;" +
                                                    "padding: 8px 35px 8px 14px;" +
                                                    "border: 1px solid #d6e9c6;}";

  private static final String ourAlertErrorRule = ".alert-error{ background-color: #f2dede;\n" +
                                                  "color: #b94a48;" +
                                                  "padding: 8px 35px 8px 14px;" +
                                                  "border: 1px solid #eed3d7;}";

  public static String markdown2Html(@NotNull String description) {
    description = StringUtil.replace(description, "class=\"alert alert-success\"", "class=\"alert-success\"");
    description = StringUtil.replace(description, "class=\"alert alert-error\"", "class=\"alert-error\"");
    ArrayList<String> lines = ContainerUtil.newArrayList(description.split("\n|\r|\r\n"));
    MarkdownUtil.replaceHeaders(lines);
    return ourMarkdownProcessor.markdown(StringUtil.join(lines, "\n"));
  }

  public static JEditorPane createLatexPane(@NotNull final String source) {
    final JEditorPane editorPane = new JEditorPane();
    editorPane.setBackground(IpnbEditorUtil.getBackground());
    editorPane.setContentType(new HTMLEditorKit().getContentType());
    editorPane.setEditorKit(new HTMLEditorKit());

    final StyleSheet sheet = ((HTMLDocument)editorPane.getDocument()).getStyleSheet();
    sheet.addRule(ourBodyRule);
    sheet.addRule(ourCodeRule);
    sheet.addRule(ourAlertRule);
    sheet.addRule(ourAlertSuccessRule);
    sheet.addRule(ourAlertErrorRule);

    editorPane.setEditable(false);

    final String html = convertToHtml(source, editorPane);
    editorPane.setText("<html><body>" + html + "</body></html>");

    editorPane.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final Container parent = editorPane.getParent();
        final MouseEvent parentEvent = SwingUtilities.convertMouseEvent(editorPane, e, parent);
        parent.dispatchEvent(parentEvent);
      }
    });
    editorPane.addHyperlinkListener(new BrowserHyperlinkListener());
    //TODO: jump to the section (see User Interface#Utilities)

    return editorPane;
  }

  private static String convertToHtml(@NotNull final String source, @NotNull final JEditorPane editorPane) {
    final StringBuilder result = new StringBuilder();
    StringBuilder markdown = new StringBuilder();
    StringBuilder formula = new StringBuilder();
    boolean inCode = false;
    int inMultiStringCode = 0;
    boolean inEnd = false;
    boolean escaped = false;
    boolean backQuoted = false;

    int imageIndex = 0;
    for (int i = 0; i != source.length(); ++i) {
      final char charAt = source.charAt(i);

      if (charAt == '`') {
        backQuoted = !backQuoted;
        if (source.length() > i + 2 && source.charAt(i + 1) == '`' && source.charAt(i + 2) == '`') {
          markdown.append(escaped ? "</code>" : "<code>");
          escaped = !escaped;
          //noinspection AssignmentToForLoopParameter
          i += 2;
          continue;
        }
      }
      if (escaped && charAt == '\n') {
        markdown.append("<br/>");
        continue;
      }

      if (!escaped && !backQuoted) {
        if (charAt == '$' && source.length() > i + 1 && source.charAt(i+1) != '$') {
          inCode = !inCode;
        }

        if (charAt == '\\' && source.substring(i).startsWith("\\begin")) {
          inMultiStringCode += 1;
        }
        if (charAt == '\\' && source.substring(i).startsWith("\\end")) {
          inEnd = true;
        }
      }

      final boolean doubleDollar = charAt == '$' && ((source.length() > i + 1 && source.charAt(i + 1) == '$')
                                                     || (i >= 1  && source.charAt(i - 1) == '$'));
      if (inCode || inMultiStringCode != 0 || (!backQuoted && doubleDollar)) {
        if (markdown.length() != 0) {
          result.append(markdown.toString());
          markdown = new StringBuilder();
        }
        formula.append(charAt);
      }
      else {
        if (formula.length() != 0 && charAt == '$') {
          formula.append(charAt);
        }
        else {
          markdown.append(charAt);
        }
        if (formula.length() != 0) {
          addFormula(formula.toString(), editorPane, imageIndex, result);

          imageIndex += 1;
          formula = new StringBuilder();
        }
      }

      if (inEnd && charAt == '}') {
        inMultiStringCode -= 1;
        inEnd = false;
      }

    }
    if (formula.length() != 0) {
      addFormula(formula.toString(), editorPane, imageIndex, result);
    }
    if (markdown.length() != 0) {
      result.append(markdown.toString());
    }
    return markdown2Html(result.toString());
  }

  private static void addFormula(@NotNull final String formulaText, JEditorPane editorPane, int imageIndex, StringBuilder result) {
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
      context.setParameter(Parameter.MATHCOLOR, UIUtil.getTextAreaForeground());
      context.setParameter(Parameter.MATHSIZE, 18);

      final Document document = MathMLParserSupport.parseString(xmlString);

      final BufferedImage image = Converter.getInstance().render(document, context);

      try {
        @SuppressWarnings("unchecked")
        Dictionary<URL, BufferedImage> cache = (Dictionary<URL, BufferedImage>)editorPane.getDocument().getProperty("imageCache");
        if (cache == null) {
          //noinspection UseOfObsoleteCollectionType
          cache = new Hashtable<URL, BufferedImage>();
          editorPane.getDocument().putProperty("imageCache", cache);
        }

        final URL u = new URL(ourImagePrefix + imageIndex + ".jpg");
        cache.put(u, image);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
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
    if (formulaText.startsWith("$$") || formulaText.startsWith("\\begin")) {
      result.append("<p style = \"text-align:center;\"><img src=\"").append(ourImagePrefix).append(imageIndex).append(".jpg\"/></p>");
    }
    else
      result.append("<img src=\"").append(ourImagePrefix).append(imageIndex).append(".jpg\"/>");
  }
}
