package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.aspects.lexer.AspectjLexer;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.lang.Language;
import com.intellij.lang.properties.parsing.PropertiesLexer;
import com.intellij.lang.cacheBuilder.WordOccurence;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lexer.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.cache.impl.CacheManagerImpl;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.search.TodoPattern;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.Processor;
import com.intellij.util.text.CharArrayCharSequence;
import gnu.trove.TIntIntHashMap;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IdTableBuilding {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.cache.impl.idCache.IdTableBuilding");

  private static final int FILE_SIZE_LIMIT = 1000000; // ignore files of size > 1Mb

  public static class Result {
    final Runnable runnable;
    final TIntIntHashMap wordsMap;
    final int[] todoCounts;

    private Result(Runnable runnable, TIntIntHashMap wordsMap, int[] todoCounts) {
      this.runnable = runnable;
      this.wordsMap = wordsMap;
      this.todoCounts = todoCounts;
    }
  }

  public interface IdCacheBuilder {
    void build(char[] chars, int length, TIntIntHashMap wordsTable, TodoPattern[] todoPatterns, int[] todoCounts, final PsiManager manager
    );
  }

  public interface ScanWordProcessor {
    void run (char[] chars, int start, int end);
  }

  static class TextIdCacheBuilder implements IdCacheBuilder {
    public void build(char[] chars,
                      int length,
                      TIntIntHashMap wordsTable,
                      TodoPattern[] todoPatterns,
                      int[] todoCounts,
                      final PsiManager manager) {
      scanWords(wordsTable, chars, 0, length, UsageSearchContext.IN_PLAIN_TEXT);

      if (todoCounts != null) {
        for (int index = 0; index < todoPatterns.length; index++) {
          Pattern pattern = todoPatterns[index].getPattern();
          if (pattern != null) {
            CharSequence input = new CharArrayCharSequence(chars, 0, length);
            Matcher matcher = pattern.matcher(input);
            while (matcher.find()) {
              if (matcher.start() != matcher.end()) {
                todoCounts[index]++;
              }
            }
          }
        }
      }
    }
  }

  private static class PropertiesIdCacheBuilder implements IdCacheBuilder {
    public void build(char[] chars,
                      int length,
                      TIntIntHashMap wordsTable,
                      TodoPattern[] todoPatterns,
                      int[] todoCounts,
                      final PsiManager manager) {
      Lexer lexer = new PropertiesFilterLexer(new PropertiesLexer(), wordsTable, todoCounts);
      lexer = new FilterLexer(lexer, new FilterLexer.SetFilter(ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET));
      lexer.start(chars);
      while (lexer.getTokenType() != null) lexer.advance();
    }
  }

  static class JavaIdCacheBuilder implements IdCacheBuilder {
    protected Lexer createLexer() {
      return new JavaLexer(LanguageLevel.JDK_1_3);
    }

    public void build(char[] chars,
                      int length,
                      TIntIntHashMap wordsTable,
                      TodoPattern[] todoPatterns,
                      int[] todoCounts,
                      final PsiManager manager) {
      Lexer lexer = createLexer();
      JavaFilterLexer filterLexer = new JavaFilterLexer(lexer, wordsTable, todoCounts);
      lexer = new FilterLexer(filterLexer, new FilterLexer.SetFilter(ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET));
      lexer.start(chars);
      while (lexer.getTokenType() != null) lexer.advance();
    }
  }


  static class AspectJIdCacheBuilder extends JavaIdCacheBuilder {
    protected Lexer createLexer() {
      return new AspectjLexer(false, false);
    }
  }

  static class XmlIdCacheBuilder implements IdCacheBuilder {
    public void build(char[] chars,
                      int length,
                      TIntIntHashMap wordsTable,
                      TodoPattern[] todoPatterns,
                      int[] todoCounts,
                      final PsiManager manager) {
      BaseFilterLexer filterLexer = createLexer(wordsTable, todoCounts);
      filterLexer.start(chars);
      while (filterLexer.getTokenType() != null) {
        filterLexer.advance();
      }
    }

    protected BaseFilterLexer createLexer(TIntIntHashMap wordsTable, int[] todoCounts) {
      Lexer lexer = new XmlLexer();
      return new XmlFilterLexer(lexer, wordsTable, todoCounts);
    }
  }

  static class HtmlIdCacheBuilder extends XmlIdCacheBuilder {
    protected BaseFilterLexer createLexer(TIntIntHashMap wordsTable, int[] todoCounts) {
      return new XHtmlFilterLexer(new HtmlLexer(), wordsTable, todoCounts);
    }
  }

  static class XHtmlIdCacheBuilder extends XmlIdCacheBuilder {
    protected BaseFilterLexer createLexer(TIntIntHashMap wordsTable, int[] todoCounts) {
      return new XHtmlFilterLexer(new XHtmlLexer(), wordsTable, todoCounts);
    }
  }

  static class JspxIdCacheBuilder implements IdCacheBuilder {
    public void build(char[] chars,
                      int length,
                      TIntIntHashMap wordsTable,
                      TodoPattern[] todoPatterns,
                      int[] todoCounts,
                      final PsiManager manager) {
      Lexer lexer = new JspxHighlightingLexer();
      JspxFilterLexer filterLexer = new JspxFilterLexer(lexer, wordsTable, todoCounts);
      lexer = new FilterLexer(filterLexer, TOKEN_FILTER);
      lexer.start(chars);
      while (lexer.getTokenType() != null) lexer.advance();
    }
  }

  static class FormFileIdCacheBuilder extends TextIdCacheBuilder {
    public void build(char[] chars,
                      int length,
                      final TIntIntHashMap wordsTable,
                      TodoPattern[] todoPatterns,
                      int[] todoCounts,
                      final PsiManager manager) {
      super.build(chars, length, wordsTable, todoPatterns, todoCounts, manager);

      try {
        LwRootContainer container = Utils.getRootContainer(new String(chars),
                                                           null/*no need component classes*/);
        String className = container.getClassToBind();
        if (className != null) {
          addClassAndPackagesNames(className, wordsTable);
        }

        FormEditingUtil.iterate(container,
                                new FormEditingUtil.ComponentVisitor() {
                                  public boolean visit(IComponent iComponent) {
                                    String componentClassName = iComponent.getComponentClassName();
                                    addClassAndPackagesNames(componentClassName, wordsTable);
                                    return true;
                                  }
                                });
      }
      catch (Exception e) {
      }
    }
  }

  private static final HashMap<FileType,IdCacheBuilder> cacheBuilders = new HashMap<FileType, IdCacheBuilder>();

  public static void registerCacheBuilder(FileType fileType,IdCacheBuilder idCacheBuilder) {
    cacheBuilders.put(fileType, idCacheBuilder);
  }

  static {
    registerCacheBuilder(StdFileTypes.JAVA,new JavaIdCacheBuilder());
    registerCacheBuilder(StdFileTypes.ASPECT,new AspectJIdCacheBuilder());
    registerCacheBuilder(StdFileTypes.XML,new XmlIdCacheBuilder());
    registerCacheBuilder(StdFileTypes.DTD,new XmlIdCacheBuilder());

    registerCacheBuilder(StdFileTypes.HTML,new HtmlIdCacheBuilder());
    registerCacheBuilder(StdFileTypes.XHTML,new XHtmlIdCacheBuilder());
    registerCacheBuilder(StdFileTypes.JSPX,new JspxIdCacheBuilder());
    registerCacheBuilder(StdFileTypes.PLAIN_TEXT,new TextIdCacheBuilder());
    registerCacheBuilder(StdFileTypes.GUI_DESIGNER_FORM,new FormFileIdCacheBuilder());
    registerCacheBuilder(StdFileTypes.PROPERTIES, new PropertiesIdCacheBuilder());
  }

  public static IdCacheBuilder getCacheBuilder(FileType fileType) {
    final IdCacheBuilder idCacheBuilder = cacheBuilders.get(fileType);

    if (idCacheBuilder != null) return idCacheBuilder;

    if (fileType instanceof LanguageFileType) {
      final Language lang = ((LanguageFileType)fileType).getLanguage();
      if (lang != null) {
        final FindUsagesProvider findUsagesProvider = lang.getFindUsagesProvider();
        final WordsScanner scanner = findUsagesProvider.getWordsScanner();
        if (scanner != null) {
          return new WordsScannerIdCacheBuilderAdapter(scanner);
        }
      }
    }

    return null;
  }

  private static class WordsScannerIdCacheBuilderAdapter implements IdCacheBuilder {
    private WordsScanner myScanner;

    public WordsScannerIdCacheBuilderAdapter(final WordsScanner scanner) {
      myScanner = scanner;
    }

    public void build(char[] chars,
                      int length,
                      final TIntIntHashMap wordsTable,
                      final TodoPattern[] todoPatterns,
                      final int[] todoCounts,
                      final PsiManager manager) {
      myScanner.processWords(new CharArrayCharSequence(chars, 0, length), new Processor<WordOccurence>() {
        public boolean process(final WordOccurence t) {
          IdCacheUtil.addOccurrence(wordsTable, t.getText(), convertToMask(t.getKind()));

          if (t.getKind() == WordOccurence.Kind.COMMENTS || t.getKind() == null) {
            if (todoCounts != null) {
              for (int index = 0; index < todoPatterns.length; index++) {
                Pattern pattern = todoPatterns[index].getPattern();
                if (pattern != null) {
                  CharSequence input = t.getText();
                  Matcher matcher = pattern.matcher(input);
                  while (matcher.find()) {
                    if (matcher.start() != matcher.end()) {
                      todoCounts[index]++;
                    }
                  }
                }
              }
            }
          }
          return true;
        }

        private int convertToMask(final WordOccurence.Kind kind) {
          if (kind == null) return UsageSearchContext.ANY;
          if (kind == WordOccurence.Kind.CODE) return UsageSearchContext.IN_CODE;
          if (kind == WordOccurence.Kind.COMMENTS) return UsageSearchContext.IN_COMMENTS;
          if (kind == WordOccurence.Kind.LITERALS) return UsageSearchContext.IN_STRINGS;
          return 0;
        }
      });
    }
  }

  public static Result getBuildingRunnable(final PsiManagerImpl manager, FileContent fileContent, final boolean buildTodos) {
    if (LOG.isDebugEnabled()){
      LOG.debug(
        "enter: getBuildingRunnable(file='" + fileContent.getVirtualFile() + "' buildTodos='" + buildTodos + "' )"
      );
      //LOG.debug(new Throwable());
    }

    final VirtualFile virtualFile = fileContent.getVirtualFile();
    LOG.assertTrue(virtualFile.isValid());
    if (virtualFile.getLength() > FILE_SIZE_LIMIT) return null;
    final FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(virtualFile);
    if (fileType.isBinary()) return null;
    if (StdFileTypes.CLASS.equals(fileType)) return null;

    final TIntIntHashMap wordsTable = new TIntIntHashMap();

    final int[] todoCounts;
    final TodoPattern[] todoPatterns = TodoConfiguration.getInstance().getTodoPatterns();
    if (buildTodos && CacheManagerImpl.canContainTodoItems(fileContent.getVirtualFile())){
      int patternCount = todoPatterns.length;
      todoCounts = patternCount > 0 ? new int[patternCount] : null;
    }
    else{
      todoCounts = null;
    }

    final PsiFile psiFile = manager.getFile(fileContent);
    if (psiFile == null) return null;

    final char[] chars = psiFile.textToCharArray();
    final int textLength = psiFile.getTextLength();

    final IdCacheBuilder cacheBuilder = getCacheBuilder(fileType);

    if (cacheBuilder==null) return null;

    Runnable runnable = new Runnable() {
      public void run() {
        synchronized (PsiLock.LOCK) {
          cacheBuilder.build(chars, textLength, wordsTable, todoPatterns, todoCounts, manager);
        }
      }
    };

    return new Result(runnable, wordsTable, todoCounts);
  }

  private static void addClassAndPackagesNames(String qName, final TIntIntHashMap wordsTable) {
    IdCacheUtil.addOccurrence(wordsTable,qName, UsageSearchContext.IN_FOREIGN_LANGUAGES);
    int idx = qName.lastIndexOf('.');
    while (idx > 0) {
      qName = qName.substring(0, idx);
      IdCacheUtil.addOccurrence(wordsTable, qName, UsageSearchContext.IN_FOREIGN_LANGUAGES);
      idx = qName.lastIndexOf('.');
    }
  }

  private static final FilterLexer.Filter TOKEN_FILTER = new FilterLexer.Filter() {
    public boolean reject(IElementType type) {
      return !(type instanceof IJavaElementType) || ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET.isInSet(type);
    }
  };

  public static void scanWords(final ScanWordProcessor processor, final char[] chars, final int startOffset, final int endOffset) {
    int index = startOffset;
    ScanWordsLoop:
      while(true){
        while(true){
          if (index == endOffset) break ScanWordsLoop;
          char c = chars[index];
          if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (Character.isJavaIdentifierStart(c) && c != '$')) break;
          index++;
        }
        int index1 = index;
        while(true){
          index++;
          if (index == endOffset) break;
          char c = chars[index];
          if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
          if (!Character.isJavaIdentifierPart(c) || c == '$') break;
        }
        if (index - index1 > 100) continue; // Strange limit but we should have some!

        processor.run(chars, index1, index);
      }
  }

  public static void scanWords(final TIntIntHashMap table,
                               final char[] chars,
                               final int start,
                               final int end,
                               final int occurrenceMask) {
    scanWords(new ScanWordProcessor(){
      public void run(final char[] chars, final int start, final int end) {
        registerOccurence(chars, start, end, table, occurrenceMask);
      }
    }, chars, start, end);
  }

  private static void registerOccurence(final char[] chars,
                                        final int start,
                                        final int end,
                                        final TIntIntHashMap table,
                                        final int occurrenceMask) {
    IdCacheUtil.addOccurrence(table, chars, start, end, occurrenceMask);
  }
}
