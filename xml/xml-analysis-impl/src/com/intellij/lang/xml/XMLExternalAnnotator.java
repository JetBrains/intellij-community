/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.xml;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class XMLExternalAnnotator extends ExternalAnnotator<XMLExternalAnnotator.MyHost, XMLExternalAnnotator.MyHost> {
  @Nullable
  @Override
  public MyHost collectInformation(@NotNull PsiFile file) {
    if (!(file instanceof XmlFile)) return null;
    final XmlDocument document = ((XmlFile)file).getDocument();
    if (document == null) return null;
    XmlTag rootTag = document.getRootTag();
    XmlNSDescriptor nsDescriptor = rootTag == null ? null : rootTag.getNSDescriptor(rootTag.getNamespace(), false);

    if (nsDescriptor instanceof Validator) {
      //noinspection unchecked
      MyHost host = new MyHost();
      ((Validator<XmlDocument>)nsDescriptor).validate(document, host);
      return host;
    }
    return null;
  }

  @Nullable
  @Override
  public MyHost doAnnotate(MyHost collectedInfo) {
    return collectedInfo;
  }

  @Override
  public void apply(@NotNull PsiFile file, MyHost annotationResult, @NotNull AnnotationHolder holder) {
    annotationResult.apply(holder);
  }

  private static void appendFixes(final Annotation annotation, final IntentionAction... actions) {
    if (actions != null) {
      for (IntentionAction action : actions) annotation.registerFix(action);
    }
  }

  static class MyHost implements Validator.ValidationHost {
    private final List<Trinity<PsiElement, String, ErrorType>> messages = new ArrayList<>();

    @Override
    public void addMessage(PsiElement context, String message, int type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addMessage(PsiElement context, String message, @NotNull ErrorType type) {
      messages.add(Trinity.create(context, message, type));
    }

    void apply (AnnotationHolder holder) {
      for (Trinity<PsiElement, String, ErrorType> message : messages) {
        addMessageWithFixes(message.first, message.second, message.third, holder);
      }
    }
  }
  
  
  public static void addMessageWithFixes(final PsiElement context,
                                         final String message,
                                         @NotNull final Validator.ValidationHost.ErrorType type,
                                         AnnotationHolder myHolder,
                                         @NotNull final IntentionAction... fixes) {
    if (message != null && !message.isEmpty()) {
      if (context instanceof XmlTag) {
        addMessagesForTag((XmlTag)context, message, type, myHolder, fixes);
      }
      else {
        if (type == Validator.ValidationHost.ErrorType.ERROR) {
          appendFixes(myHolder.createErrorAnnotation(context, message), fixes);
        }
        else {
          appendFixes(myHolder.createWarningAnnotation(context, message), fixes);
        }
      }
    }
  }

  private static void addMessagesForTag(XmlTag tag, String message, Validator.ValidationHost.ErrorType type, AnnotationHolder myHolder, IntentionAction... actions) {
    XmlToken childByRole = XmlTagUtil.getStartTagNameElement(tag);

    addMessagesForTreeChild(childByRole, type, message, myHolder, actions);

    childByRole = XmlTagUtil.getEndTagNameElement(tag);
    addMessagesForTreeChild(childByRole, type, message, myHolder, actions);
  }

  private static void addMessagesForTreeChild(final XmlToken childByRole,
                                              final Validator.ValidationHost.ErrorType type,
                                              final String message,
                                              AnnotationHolder myHolder, IntentionAction... actions) {
    if (childByRole != null) {
      Annotation annotation;
      if (type == Validator.ValidationHost.ErrorType.ERROR) {
        annotation = myHolder.createErrorAnnotation(childByRole, message);
      }
      else {
        annotation = myHolder.createWarningAnnotation(childByRole, message);
      }

      appendFixes(annotation, actions);
    }
  }
}
