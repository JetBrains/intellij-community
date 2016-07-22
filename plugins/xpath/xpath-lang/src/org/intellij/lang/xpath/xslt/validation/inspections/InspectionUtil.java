/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.validation.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.util.XmlUtil;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InspectionUtil {
    static final Pattern SUPPRESSION_PATTERN = Pattern.compile("[ \t]*(?:noinspection|suppress)[ \t]+(\\w+(,[ \t]*\\w+)*)[ \t]*");

    @NonNls
    private static final String ALL_ID = "ALL";

    private InspectionUtil() {
    }

    public static boolean isSuppressed(LocalInspectionTool tool, PsiElement element) {
        final XmlTag tag = PsiTreeUtil.getContextOfType(element, XmlTag.class, true);
        if (isSuppressedAt(tag, tool)) {
            return true;
        }
        final XmlTag tmpl = XsltCodeInsightUtil.getTemplateTag(element, true);
        if (isSuppressedAt(tmpl, tool)) {
            return true;
        }
        final XmlDocument document = PsiTreeUtil.getContextOfType(element, XmlDocument.class, true);
        if (document != null) {
            final XmlTag sheet = document.getRootTag();
            if (isSuppressedAt(sheet, tool)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSuppressedAt(PsiElement anchor, LocalInspectionTool tool) {
        if (anchor == null) return false;

        PsiElement prevSibling;
        if (!(anchor instanceof XmlComment)) {
            prevSibling = anchor.getPrevSibling();
            while (prevSibling instanceof PsiWhiteSpace || prevSibling instanceof XmlText) {
                prevSibling = prevSibling.getPrevSibling();
            }
        } else {
            prevSibling = anchor;
        }

        if (prevSibling instanceof XmlProlog) {
            if (prevSibling.getTextLength() > 0 && !"\n".equals(prevSibling.getText())) {
                return isSuppressedAt(prevSibling.getLastChild(), tool);
            } else {
                return isSuppressedAt(prevSibling, tool);                
            }
        }
        if (prevSibling instanceof XmlComment) {
            final XmlComment comment = (XmlComment)prevSibling;
            final String text = XmlUtil.getCommentText(comment);
            if (text != null) {
                final Matcher matcher = SUPPRESSION_PATTERN.matcher(text);
                if (matcher.matches()) {
                    final String[] strings = matcher.group(1).split(",");
                    final String toolId = tool.getID();
                    for (String s : strings) {
                        if (s.trim().equals(toolId) || ALL_ID.equals(s.trim())) return true;
                    }
                }
            }
        }
        return false;
    }

    public static List<SuppressIntentionAction> getSuppressActions(LocalInspectionTool inspection, final boolean isXPath) {
        final List<SuppressIntentionAction> actions = new ArrayList<>(4);

        actions.add(new SuppressInspectionAction(inspection.getID(), "Suppress for Instruction") {
            @Override
            protected XmlTag getAnchor(@NotNull PsiElement element) {
                return PsiTreeUtil.getContextOfType(element, XmlTag.class, isXPath);
            }
        });

        actions.add(new SuppressInspectionAction(inspection.getID(), "Suppress for Template") {
            @Override
            protected XmlTag getAnchor(@NotNull PsiElement element) {
                return XsltCodeInsightUtil.getTemplateTag(element, isXPath);
            }
        });

        actions.add(new SuppressInspectionAction(inspection.getID(), "Suppress for Stylesheet") {
            @Override
            protected XmlTag getAnchor(@NotNull PsiElement element) {
                final XmlDocument document = PsiTreeUtil.getContextOfType(element, XmlDocument.class, isXPath);
                return document != null ? document.getRootTag() : null;
            }
        });

        actions.add(new SuppressInspectionAction(ALL_ID, "Suppress all for Stylesheet") {
            @Override
            protected XmlTag getAnchor(@NotNull PsiElement element) {
                final XmlDocument document = PsiTreeUtil.getContextOfType(element, XmlDocument.class, isXPath);
                return document != null ? document.getRootTag() : null;
            }
        });

        return actions;
    }
}
