/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.documentation.docstrings;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.documentation.SectionBasedDocString;
import com.jetbrains.python.documentation.SectionBasedDocString.Section;
import com.jetbrains.python.documentation.SectionBasedDocString.SectionField;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class SectionBasedDocStringUpdater extends DocStringUpdater<SectionBasedDocString> {
  public SectionBasedDocStringUpdater(@NotNull SectionBasedDocString docString, @NotNull String minContentIndent) {
    super(docString, minContentIndent);
  }

  @Override
  public final void addParameter(@NotNull final String name, @Nullable String type) {
    if (type != null) {
      final Substring typeSub = myOriginalDocString.getParamTypeSubstring(name);
      if (typeSub != null) {
        replace(typeSub.getTextRange(), type);
        return;
      }
      final Substring nameSub = ContainerUtil.find(myOriginalDocString.getParameterSubstrings(), new Condition<Substring>() {
        @Override
        public boolean value(Substring substring) {
          return substring.toString().equals(name);
        }
      });
      if (nameSub != null) {
        updateParamDeclarationWithType(nameSub, type);
        return;
      }
    }
    final Section paramSection = findFirstParametersSection();
    if (paramSection != null) {
      final List<SectionField> fields = paramSection.getFields();
      if (!fields.isEmpty()) {
        final SectionField firstField = fields.get(0);
        final String newLine = createParamLine(name, type, getSectionIndent(paramSection), getFieldIndent(paramSection, firstField));
        insertBeforeLine(getFieldStartLine(firstField), newLine);
      }
      else {
        final String newLine = createParamLine(name, type, getSectionIndent(paramSection), getExpectedFieldIndent());
        insertAfterLine(getSectionLastTitleLine(paramSection), newLine);
      }
    }
    else {
      final int line = findLastNonEmptyLine();
      final String newSection = createBuilder()
        .withSectionIndent(getExpectedFieldIndent())
        .addEmptyLine()
        .startParametersSection()
        .addParameter(name, type, "")
        .buildContent(getExpectedSectionIndent(), true);
      insertAfterLine(line, newSection);
    }
  }

  @Override
  public final void addReturnValue(@Nullable String type) {
    if (StringUtil.isEmpty(type)) {
      return;
    }
    final Substring typeSub = myOriginalDocString.getReturnTypeSubstring();
    if (typeSub != null) {
      replace(typeSub.getTextRange(), type);
      return;
    }
    final Section returnSection = findFirstReturnSection();
    if (returnSection != null) {
      final List<SectionField> fields = returnSection.getFields();
      if (!fields.isEmpty()) {
        final SectionField firstField = fields.get(0);
        final String newLine = createReturnLine(type, getSectionIndent(returnSection), getFieldIndent(returnSection, firstField));
        insertBeforeLine(getFieldStartLine(firstField), newLine);
      }
      else {
        final String newLine = createReturnLine(type, getSectionIndent(returnSection), getExpectedFieldIndent());
        insertAfterLine(getSectionLastTitleLine(returnSection), newLine);
      }
    }
    else {
      final int line = findLastNonEmptyLine();
      final String newSection = createBuilder()
        .withSectionIndent(getExpectedFieldIndent())
        .addEmptyLine()
        .startReturnsSection()
        .addReturnValue(null, type, "")
        .buildContent(getExpectedSectionIndent(), true);
      insertAfterLine(line, newSection);
    }
  }

  abstract void updateParamDeclarationWithType(@NotNull Substring nameSubstring, @NotNull String type);

  protected int getSectionLastTitleLine(@NotNull Section paramSection) {
    return getSectionStartLine(paramSection);
  }

  protected abstract SectionBasedDocStringBuilder createBuilder();

  protected String createParamLine(@NotNull String name,
                                   @Nullable String type,
                                   @NotNull String docStringIndent,
                                   @NotNull String sectionIndent) {
    return createBuilder()
      .withSectionIndent(sectionIndent)
      .addParameter(name, type, "")
      .buildContent(docStringIndent, true);
  }

  protected String createReturnLine(@NotNull String type,
                                    @NotNull String docStringIndent,
                                    @NotNull String sectionIndent) {
    return createBuilder()
      .withSectionIndent(sectionIndent)
      .addReturnValue(null, type, "")
      .buildContent(docStringIndent, true);
  }

  @Nullable
  protected Section findFirstParametersSection() {
    return ContainerUtil.find(myOriginalDocString.getSections(), new Condition<Section>() {
      @Override
      public boolean value(Section section) {
        return section.getNormalizedTitle().equals(SectionBasedDocString.PARAMETERS_SECTION);
      }
    });
  }

  @Nullable
  protected Section findFirstReturnSection() {
    return ContainerUtil.find(myOriginalDocString.getSections(), new Condition<Section>() {
      @Override
      public boolean value(Section section) {
        return section.getNormalizedTitle().equals(SectionBasedDocString.RETURNS_SECTION);
      }
    });
  }

  @NotNull
  protected String getExpectedSectionIndent() {
    final Section first = ContainerUtil.getFirstItem(myOriginalDocString.getSections());
    return first != null ? getSectionIndent(first) : myMinContentIndent;
  }

  @NotNull
  protected String getExpectedFieldIndent() {
    for (Section section : myOriginalDocString.getSections()) {
      final List<SectionField> fields = section.getFields();
      if (fields.isEmpty()) {
        continue;
      }
      return getFieldIndent(section, fields.get(0));
    }
    return createBuilder().mySectionIndent;
  }

  @NotNull
  protected String getFieldIndent(@NotNull Section section, @NotNull SectionField field) {
    final String titleIndent = getSectionIndent(section);
    final String fieldIndent = getLineIndent(getFieldStartLine(field));
    final int diffSize = Math.max(1, PyIndentUtil.getLineIndentSize(fieldIndent) - PyIndentUtil.getLineIndentSize(titleIndent));
    return StringUtil.repeatSymbol(' ', diffSize);
  }

  @NotNull
  protected String getSectionIndent(@NotNull Section section) {
    return getLineIndent(getSectionStartLine(section));
  }

  protected int getSectionStartLine(@NotNull Section section) {
    return myOriginalDocString.getLineByOffset(section.getTitleAsSubstring().getStartOffset());
  }

  protected int getFieldStartLine(@NotNull SectionField field) {
    final Substring anyFieldSub = ObjectUtils.chooseNotNull(field.getNameAsSubstring(), field.getTypeAsSubstring());
    //noinspection ConstantConditions
    return myOriginalDocString.getLineByOffset(anyFieldSub.getStartOffset());
  }
}
