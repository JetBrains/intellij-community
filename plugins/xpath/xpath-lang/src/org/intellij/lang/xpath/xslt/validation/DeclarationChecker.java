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
package org.intellij.lang.xpath.xslt.validation;

import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import gnu.trove.THashMap;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.impl.XsltChecker;
import org.intellij.lang.xpath.xslt.util.ElementProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

// TODO: include/import semantics are not 100% correct currently
public final class DeclarationChecker extends ElementProcessor<XmlTag> implements PsiElementProcessor<PsiElement> {

  private final static UserDataCache<CachedValue<DeclarationChecker>, XmlFile, Void> CACHE =
          new UserDataCache<CachedValue<DeclarationChecker>, XmlFile, Void>("CACHE") {
            protected CachedValue<DeclarationChecker> compute(final XmlFile file, final Void p) {
              return CachedValuesManager.getManager(file.getProject()).createCachedValue(() -> {
                final DeclarationChecker holder = new DeclarationChecker(file);
                holder.check(file);
                return CachedValueProvider.Result.create(holder, file);
              }, false);
            }
          };

  private final Map<XmlTag, XmlTag> myDuplications = new HashMap<>();
  private final Map<XmlTag, XmlTag> myShadows = new HashMap<>();

  private State myProcessingState;

  DeclarationChecker(XmlFile file) {
    super(file.getRootTag());
  }

  @Override
  public boolean execute(@NotNull PsiElement element) {
    if (element instanceof XmlTag) {
      return process((XmlTag)element);
    }
    return true;
  }

  protected boolean followImport() {
    return false;
  }

  protected void processTemplate(XmlTag t) {
    final String n = t.getAttributeValue("name");
    if (n != null) {
      myProcessingState.processTemplate(n, t);
    }

    // template contents from included files are not relevant
    if (!isInclude()) {
      myProcessingState.enterTemplate();
      try {
        processChildren(t);
      } finally {
        myProcessingState.leaveTemplate();
      }
    }
  }

  protected void processVarOrParam(XmlTag t) {
    final String n = t.getAttributeValue("name");
    if (n != null) {
      myProcessingState.processVariable(n, t);
    }

    processChildren(t);
  }

  protected boolean shouldContinue() {
    return true;
  }

  public XmlTag getShadowedVariable(XmlTag var) {
    return myShadows.get(var);
  }

  public XmlTag getDuplicatedSymbol(XmlTag var) {
    return myDuplications.get(var);
  }

  @Override
  protected void processTag(XmlTag tag) {
    if (myProcessingState.insideTemplate()) {
      processChildren(tag);
    }
  }

  private void processChildren(XmlTag t) {
    final XmlTag[] subTags = t.getSubTags();
    for (XmlTag subTag : subTags) {
      process(subTag);
    }
  }

  private void check(XmlFile file) {
    final XmlTag rootTag = file.getRootTag();
    if (rootTag != null) {
      myProcessingState = new State();
      try {
        rootTag.processElements(this, rootTag);
      } finally {
        myProcessingState = null;
      }
    }
  }

  public static DeclarationChecker getInstance(XmlFile file) {
    return CACHE.get(file, null).getValue();
  }

  final class State {
    private final Map<String, XmlTag> myTemplateDeclarations = new THashMap<>();
    private final Map<String, XmlTag> myTopLevelVariables = new THashMap<>();
    private final Map<String, XmlTag> myLocalVariables = new THashMap<>();

    private Map<String, XmlTag> myVariableDeclarations = myTopLevelVariables;

    public void enterTemplate() {
      myVariableDeclarations = myLocalVariables;
    }

    public void leaveTemplate() {
      myLocalVariables.clear();
      myVariableDeclarations = myTopLevelVariables;
    }

    public boolean insideTemplate() {
      return myVariableDeclarations == myLocalVariables;
    }

    public void processVariable(final String name, XmlTag tag) {
      ResolveUtil.treeWalkUp(new ElementProcessor<XmlTag>(tag) {
        boolean myContinue = true;

        @Override
        protected void processTemplate(XmlTag tag) {
          myContinue = false;
        }

        @Override
        protected void processVarOrParam(XmlTag tag) {
          if (tag != myRoot && name.equals(tag.getAttributeValue("name"))) {
            assert myContinue;
            if (XsltSupport.getXsltLanguageLevel(tag.getContainingFile()) == XsltChecker.LanguageLevel.V2) {
              myShadows.put(myRoot, tag);
            } else {
              myDuplications.put(myRoot, tag);
            }
          }
        }

        @Override
        protected boolean shouldContinue() {
          return myContinue;
        }

        @Override
        protected boolean followImport() {
          return false;
        }
      }, tag);

      if (insideTemplate()) {
        XmlTag var = myTopLevelVariables.get(name);

        if (var != null) {
          myShadows.put(tag, var);
        }
      }
      myVariableDeclarations.put(name, tag);
    }

    public void processTemplate(String name, XmlTag tag) {
      final XmlTag templ = myTemplateDeclarations.get(name);
      if (templ != null) {
        myDuplications.put(tag, templ);
      }
      myTemplateDeclarations.put(name, tag);
    }
  }
}
