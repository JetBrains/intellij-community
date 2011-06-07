package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.ArrayBackedNodeIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.*;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.*;
import java.util.regex.Matcher;

/**
 * Compiles the handlers for usability
 */
public class PatternCompiler {
  private static CompileContext lastTestingContext;
  public static final Key<List<PsiElement>> ALTERNATIVE_PATTERN_ROOTS = new Key<List<PsiElement>>("ALTERNATIVE_PATTERN_ROOTS");

  public static void transformOldPattern(MatchOptions options) {
    StringToConstraintsTransformer.transformOldPattern(options);
  }

  public static CompiledPattern compilePattern(final Project project, final MatchOptions options) throws MalformedPatternException,
                                                                                                         UnsupportedOperationException {
    return new WriteAction<CompiledPattern>() {
      protected void run(Result<CompiledPattern> result) throws Throwable {
        result.setResult(compilePatternImpl(project, options));
      }
    }.execute().getResultObject();
  }

  public static String getLastFindPlan() {
    return ((TestModeOptimizingSearchHelper)lastTestingContext.getSearchHelper()).getSearchPlan();
  }

  private static CompiledPattern compilePatternImpl(Project project,MatchOptions options) {
    FileType fileType = options.getFileType();
    assert fileType instanceof LanguageFileType;
    Language language = ((LanguageFileType)fileType).getLanguage();
    StructuralSearchProfile profile = StructuralSearchUtil.getProfileByLanguage(language);
    assert profile != null;
    CompiledPattern result = profile.createCompiledPattern();

    final String[] prefixes = result.getTypedVarPrefixes();
    assert prefixes.length > 0;

    final CompileContext context = new CompileContext();
    if (ApplicationManager.getApplication().isUnitTestMode()) lastTestingContext = context;

    /*CompiledPattern result = options.getFileType() == StdFileTypes.JAVA ?
                             new JavaCompiledPattern() :
                             new XmlCompiledPattern();*/

    try {
      context.init(result, options, project, options.getScope() instanceof GlobalSearchScope);

      List<PsiElement> elements = compileByAllPrefixes(project, options, result, context, prefixes);

      context.getPattern().setNodes(
        new ArrayBackedNodeIterator(PsiUtilBase.toPsiElementArray(elements))
      );

      if (context.getSearchHelper().doOptimizing() && context.getSearchHelper().isScannedSomething()) {
        final Set<PsiFile> set = context.getSearchHelper().getFilesSetToScan();
        final List<PsiFile> filesToScan = new ArrayList<PsiFile>(set.size());
        final GlobalSearchScope scope = (GlobalSearchScope)options.getScope();

        for (final PsiFile file : set) {
          if (!scope.contains(file.getVirtualFile())) {
            continue;
          }

          if (file instanceof PsiFileImpl) {
            ((PsiFileImpl)file).clearCaches();
          }
          filesToScan.add(file);
        }

        if (filesToScan.size() == 0) {
          throw new MalformedPatternException(SSRBundle.message("ssr.will.not.find.anything"));
        }
        result.setScope(
          new LocalSearchScope(PsiUtilBase.toPsiElementArray(filesToScan))
        );
      }
    } finally {
      context.clear();
    }

    return result;
  }

  @NotNull
  private static List<PsiElement> compileByAllPrefixes(Project project,
                                                       MatchOptions options,
                                                       CompiledPattern pattern,
                                                       CompileContext context,
                                                       String[] applicablePrefixes) {
    if (applicablePrefixes.length == 0) {
      return Collections.emptyList();
    }

    LinkedList<PsiElement> elements = doCompile(project, options, pattern, new ConstantPrefixProvider(applicablePrefixes[0]), context);
    if (elements.size() == 0) {
      return elements;
    }

    final PsiFile file = elements.getFirst().getContainingFile();
    if (file == null) {
      return elements;
    }

    final PsiElement last = elements.getLast();
    final Pattern[] patterns = new Pattern[applicablePrefixes.length];

    for (int i = 0; i < applicablePrefixes.length; i++) {
      String s = StructuralSearchUtil.shieldSpecialChars(applicablePrefixes[i]);
      patterns[i] = Pattern.compile(s + "\\w+\\b");
    }

    final int[] varEndOffsets = findAllTypedVarOffsets(file, patterns);

    final int patternEndOffset = last.getTextRange().getEndOffset();
    if (elements.size() == 0 ||
        checkErrorElements(file, patternEndOffset, patternEndOffset, varEndOffsets, true) != Boolean.TRUE) {
      return elements;
    }

    final int varCount = varEndOffsets.length;
    final String[] prefixSequence = new String[varCount];

    for (int i = 0; i < varCount; i++) {
      prefixSequence[i] = applicablePrefixes[0];
    }

    final List<PsiElement> finalElements =
      compileByPrefixes(project, options, pattern, context, applicablePrefixes, patterns, prefixSequence, 0);
    return finalElements != null
           ? finalElements
           : doCompile(project, options, pattern, new ConstantPrefixProvider(applicablePrefixes[0]), context);
  }

  @Nullable
  private static List<PsiElement> compileByPrefixes(Project project,
                                                    MatchOptions options,
                                                    CompiledPattern pattern,
                                                    CompileContext context,
                                                    String[] applicablePrefixes,
                                                    Pattern[] substitutionPatterns,
                                                    String[] prefixSequence,
                                                    int index) {
    if (index >= prefixSequence.length) {
      final LinkedList<PsiElement> elements = doCompile(project, options, pattern, new ArrayPrefixProvider(prefixSequence), context);
      if (elements.size() == 0) {
        return elements;
      }

      final PsiElement parent = elements.getFirst().getParent();
      final PsiElement last = elements.getLast();
      final int[] varEndOffsets = findAllTypedVarOffsets(parent.getContainingFile(), substitutionPatterns);
      final int patternEndOffset = last.getTextRange().getEndOffset();
      return checkErrorElements(parent, patternEndOffset, patternEndOffset, varEndOffsets, false) != Boolean.TRUE
             ? elements
             : null;
    }

    String[] alternativeVariant = null;

    for (String applicablePrefix : applicablePrefixes) {
      prefixSequence[index] = applicablePrefix;

      LinkedList<PsiElement> elements = doCompile(project, options, pattern, new ArrayPrefixProvider(prefixSequence), context);
      if (elements.size() == 0) {
        return elements;
      }

      final PsiFile file = elements.getFirst().getContainingFile();
      if (file == null) {
        return elements;
      }

      final int[] varEndOffsets = findAllTypedVarOffsets(file, substitutionPatterns);
      final int offset = varEndOffsets[index];

      final int patternEndOffset = elements.getLast().getTextRange().getEndOffset();
      final Boolean result = checkErrorElements(file, offset, patternEndOffset, varEndOffsets, false);

      if (result == Boolean.TRUE) {
        continue;
      }

      if (result == Boolean.FALSE || (result == null && alternativeVariant == null)) {
        final List<PsiElement> finalElements =
          compileByPrefixes(project, options, pattern, context, applicablePrefixes, substitutionPatterns, prefixSequence, index + 1);
        if (finalElements != null) {
          if (result == Boolean.FALSE) {
            return finalElements;
          }
          alternativeVariant = new String[prefixSequence.length];
          System.arraycopy(prefixSequence, 0, alternativeVariant, 0, prefixSequence.length);
        }
      }
    }

    return alternativeVariant != null ?
           compileByPrefixes(project, options, pattern, context, applicablePrefixes, substitutionPatterns, alternativeVariant, index + 1) :
           null;
  }

  @NotNull
  private static int[] findAllTypedVarOffsets(final PsiFile file, final Pattern[] substitutionPatterns) {
    final TIntHashSet result = new TIntHashSet();

    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);

        if (element instanceof LeafElement) {
          final String text = element.getText();

          for (Pattern pattern : substitutionPatterns) {
            final Matcher matcher = pattern.matcher(text);

            while (matcher.find()) {
              result.add(element.getTextRange().getStartOffset() + matcher.end());
            }
          }
        }
      }
    });

    final int[] resultArray = result.toArray();
    Arrays.sort(resultArray);
    return resultArray;
  }


  /**
   * False: there are no error elements before offset, except patternEndOffset
   * Null: there are only error elements located exactly after template variables or at the end of the pattern
   * True: otherwise
   */
  private static Boolean checkErrorElements(PsiElement element,
                                            final int offset,
                                            final int patternEndOffset,
                                            final int[] varEndOffsets,
                                            final boolean strict) {
    final TIntArrayList errorOffsets = new TIntArrayList();
    final boolean[] containsErrorTail = {false};
    final TIntHashSet varEndOffsetsSet = new TIntHashSet(varEndOffsets);

    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);

        if (!(element instanceof PsiErrorElement)) {
          return;
        }

        final int startOffset = element.getTextRange().getStartOffset();

        if ((strict || !varEndOffsetsSet.contains(startOffset)) && startOffset != patternEndOffset) {
          errorOffsets.add(startOffset);
        }

        if (startOffset == offset) {
          containsErrorTail[0] = true;
        }
      }
    });

    for (int i = 0; i < errorOffsets.size(); i++) {
      final int errorOffset = errorOffsets.get(i);
      if (errorOffset <= offset) {
        return true;
      }
    }
    return containsErrorTail[0] ? null : false;
  }

  private interface PrefixProvider {
    String getPrefix(int varIndex);
  }

  private static class ConstantPrefixProvider implements PrefixProvider {
    private final String myPrefix;

    private ConstantPrefixProvider(String prefix) {
      myPrefix = prefix;
    }

    @Override
    public String getPrefix(int varIndex) {
      return myPrefix;
    }
  }

  private static class ArrayPrefixProvider implements PrefixProvider {
    private final String[] myPrefixes;

    private ArrayPrefixProvider(String[] prefixes) {
      myPrefixes = prefixes;
    }

    @Override
    public String getPrefix(int varIndex) {
      return myPrefixes[varIndex];
    }
  }

  private static LinkedList<PsiElement> doCompile(Project project,
                                                  MatchOptions options,
                                                  CompiledPattern result,
                                                  PrefixProvider prefixProvider,
                                                  CompileContext context) {
    result.clearHandlers();
    context.init(result, options, project, options.getScope() instanceof GlobalSearchScope);

    final StringBuilder buf = new StringBuilder();

    Template template = TemplateManager.getInstance(project).createTemplate("","",options.getSearchPattern());

    int segmentsCount = template.getSegmentsCount();
    String text = template.getTemplateText();
    buf.setLength(0);
    int prevOffset = 0;

    for(int i=0;i<segmentsCount;++i) {
      final int offset = template.getSegmentOffset(i);
      final String name = template.getSegmentName(i);

      final String prefix = prefixProvider.getPrefix(i);

      buf.append(text.substring(prevOffset,offset));
      buf.append(prefix);
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
        prefix + name,
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

      MatchPredicate predicate;

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

      addScriptConstraint(name, constraint, handler);

      if (constraint.getContainsConstraint() != null && constraint.getContainsConstraint().length() > 0) {
        predicate = new ContainsPredicate(name, constraint.getContainsConstraint());
        if (constraint.isInvertContainsConstraint()) {
          predicate = new NotPredicate(predicate);
        }
        addPredicate(handler,predicate);
      }

      if (constraint.getWithinConstraint() != null && constraint.getWithinConstraint().length() > 0) {
        assert false;
      }

      prevOffset = offset;
    }

    MatchVariableConstraint constraint = options.getVariableConstraint(Configuration.CONTEXT_VAR_NAME);
    if (constraint != null) {
      SubstitutionHandler handler = result.createSubstitutionHandler(
        Configuration.CONTEXT_VAR_NAME,
        Configuration.CONTEXT_VAR_NAME,
        constraint.isPartOfSearchResults(),
        constraint.getMinCount(),
        constraint.getMaxCount(),
        constraint.isGreedy()
      );

      if (constraint.getWithinConstraint() != null && constraint.getWithinConstraint().length() > 0) {
        MatchPredicate predicate = new WithinPredicate(Configuration.CONTEXT_VAR_NAME, constraint.getWithinConstraint(), project);
        if (constraint.isInvertWithinConstraint()) {
          predicate = new NotPredicate(predicate);
        }
        addPredicate(handler,predicate);
      }

      addScriptConstraint(Configuration.CONTEXT_VAR_NAME, constraint, handler);
    }

    buf.append(text.substring(prevOffset,text.length()));

    PsiElement[] matchStatements;

    try {
      matchStatements = MatcherImplUtil.createTreeFromText(buf.toString(), PatternTreeContext.Block, options.getFileType(),
                                                           options.getDialect(), options.getPatternContext(), project, false);
      if (matchStatements.length==0) throw new MalformedPatternException();
    } catch (IncorrectOperationException e) {
      throw new MalformedPatternException(e.getMessage());
    }

    NodeFilter filter = LexicalNodesFilter.getInstance();

    GlobalCompilingVisitor compilingVisitor = new GlobalCompilingVisitor();
    compilingVisitor.compile(matchStatements,context);
    LinkedList<PsiElement> elements = new LinkedList<PsiElement>();

    for (PsiElement matchStatement : matchStatements) {
      if (!filter.accepts(matchStatement)) {
        elements.add(matchStatement);
      }
    }

    // delete last brace
    ApplicationManager.getApplication().runWriteAction(
      new DeleteNodesAction(compilingVisitor.getLexicalNodes())
    );
    return elements;
  }

  private static void addScriptConstraint(String name, MatchVariableConstraint constraint, SubstitutionHandler handler) {
    MatchPredicate predicate;
    if (constraint.getScriptCodeConstraint()!= null && constraint.getScriptCodeConstraint().length() > 2) {
      final String script = StringUtil.stripQuotesAroundValue(constraint.getScriptCodeConstraint());
      final String s = ScriptSupport.checkValidScript(script);
      if (s != null) throw new MalformedPatternException("Script constraint for " + constraint.getName() + " has problem "+s);
      predicate = new ScriptPredicate(name, script);
      addPredicate(handler,predicate);
    }
  }

  static void addPredicate(SubstitutionHandler handler, MatchPredicate predicate) {
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