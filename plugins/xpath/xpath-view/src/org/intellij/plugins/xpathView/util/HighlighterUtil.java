/*
 * Copyright 2002-2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.xpathView.util;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.xpathView.Config;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HighlighterUtil {
    private static final Key<List<RangeHighlighter>> HIGHLIGHTERS_KEY = Key.create("XPATH_HIGHLIGHTERS");

    private HighlighterUtil() {
    }

    /**
     * Clear all highlighters in an editor that are set up by this class
     *
     * @param editor the editor
     */
    public static void clearHighlighters(final Editor editor) {
        final List<RangeHighlighter> hl = editor.getUserData(HIGHLIGHTERS_KEY);
        if (hl != null) {
            if (purgeInvalidHighlighters(editor, hl)) {
                final HighlightManager mgr = HighlightManager.getInstance(editor.getProject());
                for (Iterator<RangeHighlighter> iterator = hl.iterator(); iterator.hasNext();) {
                    RangeHighlighter highlighter = iterator.next();
                    mgr.removeSegmentHighlighter(editor, highlighter);
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Add a new highlighter to an editor
     *
     * @param editor      the editor
     * @param highlighter the highlighter
     */
    public static void addHighlighter(Editor editor, RangeHighlighter highlighter) {
        List<RangeHighlighter> hl = editor.getUserData(HIGHLIGHTERS_KEY);
        if (hl == null) {
            hl = new LinkedList<>();
            editor.putUserData(HIGHLIGHTERS_KEY, hl);
        } else {
            purgeInvalidHighlighters(editor, hl);
        }
        hl.add(highlighter);
    }

    public static void removeHighlighter(Editor editor, RangeHighlighter h) {
        final HighlightManager mgr = HighlightManager.getInstance(editor.getProject());
        mgr.removeSegmentHighlighter(editor, h);
    }

    public static boolean hasHighlighters(Editor editor) {
        final List<RangeHighlighter> hl = editor.getUserData(HIGHLIGHTERS_KEY);
        if (hl != null) {
            if (hl.isEmpty()) {
                return false;
            }
            return purgeInvalidHighlighters(editor, hl);
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "RawUseOfParameterizedType"})
    private static boolean purgeInvalidHighlighters(Editor editor, List<RangeHighlighter> hl) {
        final Set set = ContainerUtil.newIdentityTroveSet(Arrays.asList(editor.getMarkupModel().getAllHighlighters()));
        boolean hasHighlighter = false;
        for (Iterator<RangeHighlighter> iterator = hl.iterator(); iterator.hasNext();) {
            final RangeHighlighter h = iterator.next();
            if (!h.isValid() || !set.contains(h)) {
                iterator.remove();
            } else {
                hasHighlighter = true;
            }
        }
        return hasHighlighter;
    }

    public static List<RangeHighlighter> getHighlighters(Editor editor) {
        if (!hasHighlighters(editor)) {
            //noinspection unchecked
            return Collections.emptyList();
        } else {
            return editor.getUserData(HIGHLIGHTERS_KEY);
        }
    }

    /**
     * Highlight a node in the editor.
     * @param editor the editor
     * @param node the node to be highlighted
     * @param attrs the attributes for the highlighter
     * @param cfg the plugin configuration
     * @return The created highlighter object
     */
    public static RangeHighlighter highlightNode(Editor editor, final PsiElement node, TextAttributes attrs, Config cfg) {
        TextRange range;
        final PsiElement realElement;
        if ((node instanceof XmlTag) && cfg.isHighlightStartTagOnly()) {
            XmlTag tag = (XmlTag)node;
            realElement = MyPsiUtil.getNameElement(tag);
            range = realElement.getTextRange();
        } else {
            range = node.getTextRange();
            realElement = node;
        }

        // TODO: break at line boundaries
        final ArrayList<RangeHighlighter> highlighters = new ArrayList<>(1);
        final HighlightManager mgr = HighlightManager.getInstance(editor.getProject());
        mgr.addRangeHighlight(editor, range.getStartOffset(), range.getEndOffset(), attrs, false, highlighters);
        final RangeHighlighter rangeHighlighter = highlighters.get(0);

        if (cfg.isAddErrorStripe()) {
            rangeHighlighter.setErrorStripeMarkColor(attrs.getBackgroundColor());
            rangeHighlighter.setErrorStripeTooltip(formatTooltip(editor, realElement));
        } else {
            rangeHighlighter.setErrorStripeMarkColor(null);
        }
        return rangeHighlighter;
    }

    private static Object formatTooltip(Editor e, PsiElement element) {
        if (!(element instanceof XmlTag)) {
          final String text = element.getText();
          if ((text == null || text.length() == 0) && MyPsiUtil.isNameElement(element)) {
            final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, true);
            if (tag != null) {
              return tag.getName();
            }
          }
          return text;
        }
        // have to use html/preformatted or else the tooltip gets formatted totally weird.

        final CodeStyleSettingsManager instance = CodeStyleSettingsManager.getInstance(element.getProject());
        final int tabSize = instance.getCurrentSettings().getTabSize(FileTypeManager.getInstance().getFileTypeByExtension("xml"));
        final char[] spaces = new char[tabSize];
        for (int i = 0; i < spaces.length; i++) {
            spaces[i] = ' ';
        }

        final int textOffset = element.getTextOffset();
        final int lineStartOffset = e.logicalPositionToOffset(new LogicalPosition(e.offsetToLogicalPosition(textOffset).line, 0));
        final CharSequence chars = e.getDocument().getCharsSequence();
        int indent = 0;
        for (int i=lineStartOffset; i<textOffset; i++) {
            if (chars.charAt(i) == ' ') {
                indent++;
            } else if (chars.charAt(i) == '\t') {
                indent += ((indent + tabSize) / tabSize) * tabSize - indent;
            } else {
                break;
            }
        }

        final String text = element.getText().replaceAll("\\t", new String(spaces)).replaceAll("&", "&amp;").replaceAll("<", "&lt;");
        final Pattern indentPattern = Pattern.compile("^(\\s*).+");

        final StringBuilder sb = new StringBuilder("<html><pre>");
        final String[] lines = text.split("\\n");
        for (String line : lines) {
            final Matcher matcher = indentPattern.matcher(line);
            if (matcher.matches()) {
                // strip off the amount of spaces the top-level element is indented with
                line = line.substring(Math.min(matcher.group(1).length(), indent));
            }
            sb.append(line).append("\n");
        }
        return sb.append("</pre></html>").toString();
    }
}
