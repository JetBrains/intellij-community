package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.Handler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.ArrayBackedNodeIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.*;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Compiles the handlers for usability
 */
public class PatternCompiler {
  private static StringBuffer buf = new StringBuffer();
  private static CompileContext context = new CompileContext();

  public static void transformOldPattern(MatchOptions options) {
    StringToConstraintsTransformer.transformOldPattern(options);
  }

  public static CompiledPattern compilePattern(final Project project, final MatchOptions options) {
    final CompiledPattern[] result = new CompiledPattern[1];

    final Runnable runnable = new Runnable() {
      public void run() {
        result[0] = ApplicationManager.getApplication().runWriteAction(new Computable<CompiledPattern>() {
          public CompiledPattern compute() {
            return compilePatternImpl(project, options);
          }
        });
      }
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(
        runnable,
        ModalityState.defaultModalityState()
      );
    }

    return result[0];
  }

  private static CompiledPattern compilePatternImpl(Project project,MatchOptions options) {

    CompiledPattern result = options.getFileType() == StdFileTypes.JAVA ?
                             new CompiledPattern.JavaCompiledPattern() :
                             new CompiledPattern.XmlCompiledPattern();

    try {
      context.init(result, options,project, options.getScope() instanceof GlobalSearchScope);
      Template template = TemplateManager.getInstance(project).createTemplate("","",options.getSearchPattern());

      int segmentsCount = template.getSegmentsCount();
      String text = template.getTemplateText();
      buf.setLength(0);
      int prevOffset = 0;

      for(int i=0;i<segmentsCount;++i) {
        final int offset = template.getSegmentOffset(i);
        final String name = template.getSegmentName(i);

        buf.append(text.substring(prevOffset,offset));
        buf.append(result.getTypedVarPrefix());
        buf.append(name);

        MatchVariableConstraint constraint = options.getVariableConstraint(name);
        if (constraint==null) {
          // we do not edited the constraints
          constraint = new MatchVariableConstraint();
          constraint.setName( name );
          options.addVariableConstraint(constraint);
        }

        SubstitutionHandler handler = result.createSubstitutionHandler(
          name,
          result.getTypedVarPrefix() + name,
          constraint.isPartOfSearchResults(),
          constraint.getMinCount(),
          constraint.getMaxCount(),
          constraint.isGreedy()
        );

        if(constraint.isWithinHierarchy()) {
          handler.setSubtype(true);
        }

        if(constraint.isStrictlyWithinHierarchy()) {
          handler.setStrictSubtype(true);
        }

        Handler predicate;

        if (constraint.getRegExp()!=null && constraint.getRegExp().length() > 0) {
          predicate = new RegExpPredicate(
            constraint.getRegExp(),
            options.isCaseSensitiveMatch(),
            name,
            constraint.isWholeWordsOnly(),
            constraint.isPartOfSearchResults()
          );
          if (constraint.isInvertRegExp()) {
            predicate = new NotPredicate(predicate);
          }
          addPredicate(handler,predicate);
        }

        if (constraint.isReadAccess()) {
          predicate = new ReadPredicate();

          if (constraint.isInvertReadAccess()) {
            predicate = new NotPredicate(predicate);
          }
          addPredicate(handler,predicate);
        }

        if (constraint.isWriteAccess()) {
          predicate = new WritePredicate();

          if (constraint.isInvertWriteAccess()) {
            predicate = new NotPredicate(predicate);
          }
          addPredicate(handler,predicate);
        }

        if (constraint.isReference()) {
          predicate = new ReferencePredicate( constraint.getNameOfReferenceVar() );

          if (constraint.isInvertReference()) {
            predicate = new NotPredicate(predicate);
          }
          addPredicate(handler,predicate);
        }

        if (constraint.getNameOfExprType()!=null &&
            constraint.getNameOfExprType().length() > 0
            ) {
          predicate = new ExprTypePredicate(
            constraint.getNameOfExprType(),
            name,
            constraint.isExprTypeWithinHierarchy(),
            options.isCaseSensitiveMatch(),
            constraint.isPartOfSearchResults()
          );

          if (constraint.isInvertExprType()) {
            predicate = new NotPredicate(predicate);
          }
          addPredicate(handler,predicate);
        }

        if (constraint.getNameOfFormalArgType()!=null && constraint.getNameOfFormalArgType().length() > 0) {
          predicate = new FormalArgTypePredicate(
            constraint.getNameOfFormalArgType(),
            name,
            constraint.isFormalArgTypeWithinHierarchy(),
            options.isCaseSensitiveMatch(),
            constraint.isPartOfSearchResults()
          );
          if (constraint.isInvertFormalType()) {
            predicate = new NotPredicate(predicate);
          }
          addPredicate(handler,predicate);
        }

        if (constraint.getScriptCodeConstraint()!= null && constraint.getScriptCodeConstraint().length() > 0) {
          predicate = compileScript(name, constraint.getScriptCodeConstraint());
          addPredicate(handler,predicate);
        }

        prevOffset = offset;
      }

      buf.append(text.substring(prevOffset,text.length()));

      PsiElement patternNode;
      PsiElement[] matchStatements;

      try {
        matchStatements = MatcherImplUtil.createTreeFromText(buf.toString(), MatcherImplUtil.TreeContext.Block, options.getFileType(), project);
        if (matchStatements.length==0) throw new MalformedPatternException();
        patternNode = matchStatements[0].getParent();
      } catch (IncorrectOperationException e) {
        throw new MalformedPatternException(e.getMessage());
      }

      NodeFilter filter = LexicalNodesFilter.getInstance();

      CompilingVisitor compilingVisitor = CompilingVisitor.getInstance();
      compilingVisitor.compile(patternNode,context);
      List<PsiElement> elements = new LinkedList<PsiElement>();

      for (PsiElement matchStatement : matchStatements) {
        if (!filter.accepts(matchStatement)) {
          elements.add(matchStatement);
        }
      }
      context.pattern.setNodes(
        new ArrayBackedNodeIterator(elements.toArray(new PsiElement[elements.size()]))
      );

      // delete last brace
      ApplicationManager.getApplication().runWriteAction(
        new DeleteNodesAction(compilingVisitor.getLexicalNodes())
      );

      if (context.findMatchingFiles && context.isScannedSomething()) {
        final Set<PsiFile> set = context.filesToScan.keySet();
        final List<PsiFile> filesToScan = new ArrayList<PsiFile>(set.size());
        final GlobalSearchScope scope = (GlobalSearchScope)options.getScope();

        for (final PsiFile file : set) {
          if (!scope.contains(file.getVirtualFile())) {
            continue;
          }

          filesToScan.add(file);
        }

        if (filesToScan.size() == 0) {
          throw new MalformedPatternException(SSRBundle.message("ssr.will.not.find.anything"));
        }
        result.setScope(
          new LocalSearchScope( filesToScan.toArray(new PsiElement[filesToScan.size()]) )
        );
      }
    } finally {
      context.clear();
    }

    return result;
  }

  private static Handler compileScript(String name, String scriptCodeConstraint) {
    return new Handler() {
      public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
        return false;
      }
    };
  }

  static void addPredicate(SubstitutionHandler handler, Handler predicate) {
    if (handler.getPredicate()==null) {
      handler.setPredicate(predicate);
    } else {
      handler.setPredicate(
        new BinaryPredicate(
          handler.getPredicate(),
          predicate,
          false
        )
      );
    }
  }

}