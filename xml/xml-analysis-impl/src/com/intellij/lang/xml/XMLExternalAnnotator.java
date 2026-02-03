// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xml;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class XMLExternalAnnotator extends ExternalAnnotator<XMLExternalAnnotator.MyHost, XMLExternalAnnotator.MyHost> {
  @Override
  public @Nullable MyHost collectInformation(@NotNull PsiFile psiFile) {
    if (!(psiFile instanceof XmlFile)) return null;
    final XmlDocument document = ((XmlFile)psiFile).getDocument();
    if (document == null) return null;
    XmlTag rootTag = document.getRootTag();
    XmlNSDescriptor nsDescriptor = rootTag == null ? null : rootTag.getNSDescriptor(rootTag.getNamespace(), false);

    if (nsDescriptor instanceof Validator) {
      MyHost host = new MyHost();
      ((Validator<XmlDocument>)nsDescriptor).validate(document, host);
      return host;
    }
    return null;
  }

  @Override
  public @Nullable MyHost doAnnotate(MyHost collectedInfo) {
    return collectedInfo;
  }

  @Override
  public void apply(@NotNull PsiFile psiFile, MyHost annotationResult, @NotNull AnnotationHolder holder) {
    annotationResult.apply(holder);
  }

  static class MyHost implements Validator.ValidationHost {
    private final List<Trinity<PsiElement, @InspectionMessage String, ErrorType>> messages = new ArrayList<>();

    @Override
    public void addMessage(PsiElement context, @InspectionMessage String message, @NotNull ErrorType type) {
      messages.add(Trinity.create(context, message, type));
    }

    void apply (AnnotationHolder holder) {
      for (Trinity<PsiElement, @InspectionMessage String, ErrorType> message : messages) {
        addMessageWithFixes(message.first, message.second, message.third, holder);
      }
    }
  }
  
  
  public static void addMessageWithFixes(final PsiElement context,
                                         final @InspectionMessage String message,
                                         final @NotNull Validator.ValidationHost.ErrorType type,
                                         AnnotationHolder myHolder,
                                         final IntentionAction @NotNull ... fixes) {
    if (message != null && !message.isEmpty()) {
      HighlightSeverity severity = type == Validator.ValidationHost.ErrorType.ERROR ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
      if (context instanceof XmlTag) {
        addMessagesForTreeChild(XmlTagUtil.getStartTagNameElement((XmlTag)context), severity, message, myHolder, fixes);

        addMessagesForTreeChild(XmlTagUtil.getEndTagNameElement((XmlTag)context), severity, message, myHolder, fixes);
      }
      else {
        addMessagesForTreeChild(context, severity, message, myHolder,fixes);
      }
    }
  }

  private static void addMessagesForTreeChild(final PsiElement token,
                                              final HighlightSeverity type,
                                              final @InspectionMessage String message,
                                              AnnotationHolder myHolder, IntentionAction @NotNull ... actions) {
    if (token != null) {
      AnnotationBuilder builder = myHolder.newAnnotation(type, message).range(token);

      for (IntentionAction action : actions) {
        builder = builder.withFix(action);
      }
      builder.create();
    }
  }
}
