package org.jetbrains.plugins.ipnb;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.MarkdownUtil;
import com.petebevin.markdown.MarkdownProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class IpnbUtils {
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
    MarkdownUtil.replaceCodeBlock(processedLines);
    final String[] lineArray = ArrayUtil.toStringArray(processedLines);
    final String normalizedMarkdown = StringUtil.join(lineArray, "\n");
    String html = ourMarkdownProcessor.markdown(normalizedMarkdown);
    html = "<html><body>" + html
      .replace("<pre><code>", "<pre>").replace("</code></pre>", "</pre>")
      .replace("<em>", "<i>").replace("</em>", "</i>")
      .replace("<strong>", "<b>").replace("</strong>", "</b>")
      .replace("&underline;", "_")
      .trim() + "</body></html>"
    ;
    return html;
  }
}
