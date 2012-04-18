package com.jetbrains.typoscript.lang;

import com.intellij.lang.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.LimitedPool;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

/**
* @author gregsh
*/
@SuppressWarnings("StringEquality")
public class GeneratedParserUtilBase {

  private static final Logger LOG = Logger.getInstance("org.intellij.grammar.parser.GeneratedParserUtilBase");

  public static final IElementType DUMMY_BLOCK = new DummyBlockElementType();

  public interface Parser {
    boolean parse(PsiBuilder builder, int level);
  }

  public static final Parser TOKEN_ADVANCER = new Parser() {
    @Override
    public boolean parse(PsiBuilder builder, int level) {
      if (builder.eof()) return false;
      builder.advanceLexer();
      return true;
    }
  };

  public static final Parser TRUE_CONDITION = new Parser() {
    @Override
    public boolean parse(PsiBuilder builder, int level) {
      return true;
    }
  };

  public static boolean recursion_guard_(PsiBuilder builder_, int level_, String funcName_) {
    if (level_ > 1000) {
      builder_.error("Maximum recursion level (" + 1000 + ") reached in " + funcName_);
      return false;
    }
    return true;
  }

  public static void empty_element_parsed_guard_(PsiBuilder builder_, int offset_, String funcName_) {
    builder_.error("Empty element parsed in " + funcName_ +" at offset " + offset_);
  }

  public static boolean invalid_left_marker_guard_(PsiBuilder builder_, PsiBuilder.Marker marker_, String funcName_) {
    //builder_.error("Invalid left marker encountered in " + funcName_ +" at offset " + builder_.getCurrentOffset());
    boolean goodMarker = marker_ != null && ((LighterASTNode)marker_).getTokenType() != TokenType.ERROR_ELEMENT;
    if (!goodMarker) return false;
    ErrorState state = ErrorState.get(builder_);

    Frame frame = state.levelCheck.isEmpty() ? null : state.levelCheck.getLast();
    return frame == null || frame.errorReportedAt <= builder_.getCurrentOffset();
  }

  public static boolean consumeToken(PsiBuilder builder_, IElementType token) {
    if (nextTokenIsInner(builder_, token, true)) {
      builder_.advanceLexer();
      return true;
    }
    return false;
  }

  public static boolean nextTokenIs(PsiBuilder builder_, IElementType token) {
    return nextTokenIsInner(builder_, token, false);
  }

  public static boolean nextTokenIsInner(PsiBuilder builder_, IElementType token, boolean force) {
    ErrorState state = ErrorState.get(builder_);
    if (state.completionState != null && !force) return true;
    IElementType tokenType = builder_.getTokenType();
    if (!state.suppressErrors && state.predicateCount < 2) {
      addVariant(state, builder_, token);
    }
    return token == tokenType;
  }

  public static void addVariant(PsiBuilder builder_, String text) {
    addVariant(ErrorState.get(builder_), builder_, text);
  }

  private static void addVariant(ErrorState state, PsiBuilder builder_, Object o) {
    final int offset = builder_.getCurrentOffset();
    Variant variant = state.VARIANTS.alloc();
    variant.init(offset, o, state.predicateSign);
    state.variants.add(variant);
    if (variant.expected && state.lastExpectedVariantOffset < variant.offset) {
      state.lastExpectedVariantOffset = variant.offset;
    }

    CompletionState completionState = state.completionState;
    if (completionState != null && state.predicateSign) {
      addCompletionVariant(state, completionState, builder_, o, offset);
    }
  }

  public static boolean consumeToken(PsiBuilder builder_, String text) {
    ErrorState state = ErrorState.get(builder_);
    if (!state.suppressErrors && state.predicateCount < 2) {
      addVariant(state, builder_, text);
    }
    return consumeTokenInner(builder_, text, state.caseSensitive);
  }

  public static boolean consumeTokenInner(PsiBuilder builder_, String text, boolean caseSensitive) {
    final CharSequence sequence = builder_.getOriginalText();
    final int offset = builder_.getCurrentOffset();
    final int endOffset = offset + text.length();
    CharSequence tokenText = sequence.subSequence(offset, Math.min(endOffset, sequence.length()));

    if (Comparing.equal(text, tokenText, caseSensitive)) {
      int count = 0;
      while (true) {
        final int nextOffset = builder_.rawTokenTypeStart(++ count);
        if (nextOffset > endOffset) {
          return false;
        }
        else if (nextOffset == endOffset) {
          break;
        }
      }
      while (count-- > 0) builder_.advanceLexer();
      return true;
    }
    return false;
  }

  private static void addCompletionVariant(ErrorState state,
                                           CompletionState completionState,
                                           PsiBuilder builder_,
                                           Object o,
                                           int offset) {
    boolean add = false;
    int diff = completionState.offset - offset;
    String text = completionState.convertItem(o);
    int length = text == null? 0 : text.length();
    if (length == 0) return;
    if (diff == 0) {
      add = true;
    }
    else if (diff > 0 && diff <= length) {
      CharSequence fragment = builder_.getOriginalText().subSequence(offset, completionState.offset);
      add = StringUtil.startsWithIgnoreCase(text, fragment.toString());
    }
    else if (diff < 0) {
      for (int i=-1; ; i--) {
        IElementType type = builder_.rawLookup(i);
        int tokenStart = builder_.rawTokenTypeStart(i);
        if (state.whitespaceTokens.contains(type) || state.commentTokens.contains(type)) {
          diff = completionState.offset - tokenStart;
        }
        else if (type != null && tokenStart < completionState.offset) {
          CharSequence fragment = builder_.getOriginalText().subSequence(tokenStart, completionState.offset);
          if (StringUtil.startsWithIgnoreCase(text, fragment.toString())) {
            diff = completionState.offset - tokenStart;
          }
          break;
        }
        else break;
      }
      add = diff >= 0 && diff < length;
    }
    add = add && length > 1 && !(text.charAt(0) == '<' && text.charAt(length - 1) == '>') &&
          !(text.charAt(0) == '\'' && text.charAt(length - 1) == '\'' && length < 5);
    if (add) {
      completionState.items.add(text);
    }
  }


  public static final String _SECTION_NOT_ = "_SECTION_NOT_";
  public static final String _SECTION_AND_ = "_SECTION_AND_";
  public static final String _SECTION_RECOVER_ = "_SECTION_RECOVER_";
  public static final String _SECTION_GENERAL_ = "_SECTION_GENERAL_";

  public static void enterErrorRecordingSection(PsiBuilder builder_, int key, @NotNull String sectionType) {
    ErrorState state = ErrorState.get(builder_);
    state.levelCheck.add(new Frame(builder_.getCurrentOffset(), key, sectionType));
    if (sectionType == _SECTION_AND_) {
      if (state.predicateCount == 0 && !state.predicateSign) {
        throw new AssertionError("Incorrect false predicate sign");
      }
      state.predicateCount++;
    }
    else if (sectionType == _SECTION_NOT_) {
      if (state.predicateCount == 0) {
        state.predicateSign = false;
      }
      else {
        state.predicateSign = !state.predicateSign;
      }
      state.predicateCount++;
    }
  }

  public static boolean exitErrorRecordingSection(PsiBuilder builder_,
                                                  boolean result,
                                                  int key,
                                                  boolean pinned,
                                                  @NotNull String sectionType,
                                                  @Nullable Parser eatMore) {
    ErrorState state = ErrorState.get(builder_);

    Frame frame = null;
    int initialOffset = builder_.getCurrentOffset();
    if (state.levelCheck.isEmpty() || key != (frame = state.levelCheck.removeLast()).level || !sectionType.equals(frame.section)) {
      LOG.error("Unbalanced error section: got " + new Frame(initialOffset, key, sectionType) + ", expected " + frame);
      return result;
    }
    if (sectionType == _SECTION_AND_ || sectionType == _SECTION_NOT_) {
      state.predicateCount--;
      if (sectionType == _SECTION_NOT_) state.predicateSign = !state.predicateSign;
      return result;
    }
    if (sectionType == _SECTION_RECOVER_ && !state.suppressErrors && eatMore != null) {
      state.suppressErrors = true;
      final boolean eatMoreFlagOnce = !builder_.eof() && eatMore.parse(builder_, frame.level + 1);
      final int lastErrorPos = getLastVariantOffset(state, initialOffset);
      boolean eatMoreFlag = eatMoreFlagOnce || frame.offset == initialOffset && lastErrorPos > frame.offset;

      final LighterASTNode latestDoneMarker =
        (pinned || result) && lastErrorPos > initialOffset && eatMoreFlagOnce ? builder_.getLatestDoneMarker() : null;
      PsiBuilder.Marker extensionMarker = null;
      IElementType extensionTokenType = null;
      if (latestDoneMarker instanceof PsiBuilder.Marker) {
        extensionMarker = ((PsiBuilder.Marker)latestDoneMarker).precede();
        extensionTokenType = latestDoneMarker.getTokenType();
        ((PsiBuilder.Marker)latestDoneMarker).drop();
      }
      // advance to the last error pos
      // skip tokens until lastErrorPos. parseAsTree might look better here...
      int parenCount = 0;
      while (eatMoreFlag && builder_.getCurrentOffset() < lastErrorPos) {
        if (state.braces != null) {
          if (builder_.getTokenType() == state.braces[0].getLeftBraceType()) parenCount ++;
          else if (builder_.getTokenType() == state.braces[0].getRightBraceType()) parenCount --;
        }
        builder_.advanceLexer();
        eatMoreFlag = parenCount != 0 || eatMore.parse(builder_, frame.level + 1);
      }
      boolean errorReported = frame.errorReportedAt == initialOffset;
      if (errorReported) {
        if (eatMoreFlag) {
          builder_.advanceLexer();
          parseAsTree(state, builder_, frame.level + 1, DUMMY_BLOCK, true, TOKEN_ADVANCER, eatMore);
        }
      }
      else if (eatMoreFlag) {
        String tokenText = builder_.getTokenText();
        String expectedText = state.getExpectedText(builder_);
        PsiBuilder.Marker mark = builder_.mark();
        builder_.advanceLexer();
        final String gotText = !expectedText.isEmpty() ? "got '" + tokenText + "'" : "'" + tokenText + "' unexpected";
        mark.error(expectedText + gotText);
        parseAsTree(state, builder_, frame.level + 1, DUMMY_BLOCK, true, TOKEN_ADVANCER, eatMore);
        errorReported = true;
      }
      else if (eatMoreFlagOnce || (!result && frame.offset != builder_.getCurrentOffset())) {
        reportError(state, builder_, true);
        errorReported = true;
      }
      if (extensionMarker != null) {
        extensionMarker.done(extensionTokenType);
      }
      state.suppressErrors = false;
      if (errorReported || result) {
        state.clearExpectedVariants();
      }
      if (!result && eatMoreFlagOnce && frame.offset != builder_.getCurrentOffset()) result = true;
    }
    else if (!result && pinned && frame.errorReportedAt < 0) {
      // do not report if there're errors after current offset
      if (getLastVariantOffset(state, initialOffset) == initialOffset) {
        // do not force, inner recoverRoot might have skipped some tokens
        if (reportError(state, builder_, false)) {
          frame.errorReportedAt = initialOffset;
        }
      }
    }
    // propagate errorReportedAt up the stack to avoid duplicate reporting
    Frame prevFrame = state.levelCheck.isEmpty() ? null : state.levelCheck.getLast();
    if (prevFrame != null && prevFrame.errorReportedAt < frame.errorReportedAt) prevFrame.errorReportedAt = frame.errorReportedAt;
    return result;
  }

  public static boolean report_error_(PsiBuilder builder_, boolean current_) {
    if (!current_) report_error_(builder_);
    return current_;
  }

  public static void report_error_(PsiBuilder builder_) {
    ErrorState state = ErrorState.get(builder_);

    Frame frame = state.levelCheck.isEmpty()? null : state.levelCheck.getLast();
    if (frame == null) {
      LOG.error("Unbalanced error section: got null , expected " + frame);
      return;
    }
    int offset = builder_.getCurrentOffset();
    if (frame.errorReportedAt < offset && getLastVariantOffset(state, builder_.getCurrentOffset()) <= offset) {
      if (reportError(state, builder_, true)) {
        frame.errorReportedAt = offset;
      }
    }
  }

  static int getLastVariantOffset(ErrorState state, int defValue) {
    return state.lastExpectedVariantOffset < 0? defValue : state.lastExpectedVariantOffset;
  }

  private static boolean reportError(ErrorState state, PsiBuilder builder_, boolean force) {
    String expectedText = state.getExpectedText(builder_);
    boolean notEmpty = StringUtil.isNotEmpty(expectedText);
    if (force || notEmpty) {
      final String gotText = builder_.eof()? "unexpected end of file" :
                             notEmpty? "got '" + builder_.getTokenText() +"'" :
                             "'" + builder_.getTokenText() +"' unexpected";
      builder_.error(expectedText + gotText);
      return true;
    }
    return false;
  }


  private static final Key<ErrorState> ERROR_STATE_KEY = Key.create("ERROR_STATE_KEY");
  public static final Key<CompletionState> COMPLETION_STATE_KEY = Key.create("COMPLETION_STATE_KEY");

  public static class CompletionState {
    public final int offset;
    public final Collection<String> items = new THashSet<String>();

    public CompletionState(int offset) {
      this.offset = offset;
    }

    @Nullable
    public String convertItem(Object o) {
      return o.toString();
    }
  }

  public static class ErrorState {
    int predicateCount;
    boolean predicateSign = true;
    boolean suppressErrors;
    final LinkedList<Frame> levelCheck = new LinkedList<Frame>();
    CompletionState completionState;

    private boolean caseSensitive;
    private BracePair[] braces;
    private TokenSet whitespaceTokens = TokenSet.EMPTY;
    private TokenSet commentTokens = TokenSet.EMPTY;

    private int lastExpectedVariantOffset = -1;
    ArrayList<Variant> variants = new ArrayList<Variant>();
    final LimitedPool<Variant> VARIANTS = new LimitedPool<Variant>(5000, new LimitedPool.ObjectFactory<Variant>() {
      public Variant create() {
        return new Variant();
      }

      public void cleanup(final Variant v) {
      }
    });


    public static ErrorState get(PsiBuilder builder) {
      ErrorState state = builder.getUserDataUnprotected(ERROR_STATE_KEY);
      if (state == null) {
        builder.putUserDataUnprotected(ERROR_STATE_KEY, state = new ErrorState());
        PsiFile file = builder.getUserDataUnprotected(FileContextUtil.CONTAINING_FILE_KEY);
        state.completionState = file == null? null: file.getUserData(COMPLETION_STATE_KEY);
        if (file != null) {
          Language language = file.getLanguage();
          state.caseSensitive = language.isCaseSensitive();
          PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(language);
          state.braces = matcher == null? null : matcher.getPairs();
          if (state.braces != null && state.braces.length == 0) state.braces = null;
          ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
          if (parserDefinition != null) {
            state.commentTokens = parserDefinition.getCommentTokens();
            state.whitespaceTokens = parserDefinition.getWhitespaceTokens();
          }
        }
      }
      return state;
    }

    public String getExpectedText(PsiBuilder builder_) {
      int offset = builder_.getCurrentOffset();
      StringBuilder sb = new StringBuilder();
      if (addExpected(sb, offset, true)) {
        sb.append(" expected, ");
      }
      else if (addExpected(sb, offset, false)) sb.append(" unexpected, ");
      return sb.toString();
    }

    private static final int MAX_VARIANTS_TO_DISPLAY = Integer.MAX_VALUE;
    private boolean addExpected(StringBuilder sb, int offset, boolean expected) {
      String[] strings = new String[variants.size()];
      long[] hashes = new long[strings.length];
      Arrays.fill(strings, "");
      int count = 0;
      loop: for (Variant variant : variants) {
        if (offset == variant.offset) {
          if (variant.expected != expected) continue;
          String text = variant.object.toString();
          long hash = StringHash.calc(text);
          for (int i=0; i<count; i++) {
            if (hashes[i] == hash) continue loop;
          }
          hashes[count] = hash;
          strings[count] = text;
          count++;
        }
      }
      Arrays.sort(strings);
      count = 0;
      for (String s : strings) {
        if (s == "") continue;
        if (count++ > 0) {
          if (count > MAX_VARIANTS_TO_DISPLAY) {
            sb.append(" and ...");
            break;
          }
          else {
            sb.append(", ");
          }
        }
        char c = s.charAt(0);
        String displayText = c == '<' || StringUtil.isJavaIdentifierStart(c) ? s : '\'' + s + '\'';
        sb.append(displayText);
      }
      if (count > 1 && count < MAX_VARIANTS_TO_DISPLAY) {
        int idx = sb.lastIndexOf(", ");
        sb.replace(idx, idx + 1, " or");
      }
      return count > 0;
    }

    void clearExpectedVariants() {
      lastExpectedVariantOffset = -1;
      for (Variant v : variants) {
        VARIANTS.recycle(v);
      }
      variants.clear();
    }
  }

  public static class Frame {
    int offset;
    int level;
    String section;
    int errorReportedAt = -1;

    public Frame(int offset, int level, String section) {
      this.offset = offset;
      this.level = level;
      this.section = section;
    }

    @Override
    public String toString() {
      return "<"+offset+", "+section+", "+level+">";
    }
  }


  public static class Variant {
    int offset;
    Object object;
    boolean expected;

    public void init(int offset, Object text, boolean expected) {
      this.offset = offset;
      this.object = text;
      this.expected = expected;
    }

    @Override
    public String toString() {
      return "<" + offset + ", " + expected + ", " + object + ">";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Variant variant = (Variant)o;

      if (expected != variant.expected) return false;
      if (offset != variant.offset) return false;
      if (!this.object.equals(variant.object)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = offset;
      result = 31 * result + object.hashCode();
      result = 31 * result + (expected ? 1 : 0);
      return result;
    }
  }

  @Nullable
  private static IElementType getClosingBracket(ErrorState state, IElementType type) {
    if (state.braces == null) return null;
    for (BracePair pair : state.braces) {
      if (type == pair.getLeftBraceType()) return pair.getRightBraceType();
    }
    return null;
  }


  private static final int MAX_CHILDREN_IN_TREE = 10;
  public static boolean parseAsTree(ErrorState state, final PsiBuilder builder_, int level, final IElementType chunkType,
                                    boolean checkBraces, final Parser parser, final Parser eatMoreCondition) {
    final LinkedList<Pair<PsiBuilder.Marker, PsiBuilder.Marker>> parenList = new LinkedList<Pair<PsiBuilder.Marker, PsiBuilder.Marker>>();
    final LinkedList<Pair<PsiBuilder.Marker, Integer>> siblingList = new LinkedList<Pair<PsiBuilder.Marker, Integer>>();
    PsiBuilder.Marker marker = null;

    final Runnable checkSiblingsRunnable = new Runnable() {
      public void run() {
        main:
        while (!siblingList.isEmpty()) {
          final Pair<PsiBuilder.Marker, PsiBuilder.Marker> parenPair = parenList.peek();
          final int rating = siblingList.getFirst().second;
          int count = 0;
          for (Pair<PsiBuilder.Marker, Integer> pair : siblingList) {
            if (pair.second != rating || parenPair != null && pair.first == parenPair.second) break main;
            if (++count >= MAX_CHILDREN_IN_TREE) {
              final PsiBuilder.Marker parentMarker = pair.first.precede();
              while (count-- > 0) {
                siblingList.removeFirst();
              }
              parentMarker.done(chunkType);
              siblingList.addFirst(Pair.create(parentMarker, rating + 1));
              continue main;
            }
          }
          break;
        }
      }
    };
    boolean checkParens = state.braces != null && checkBraces;
    int totalCount = 0;
    int tokenCount = 0;
    if (checkParens && builder_.rawLookup(-1) == state.braces[0].getLeftBraceType()) {
      LighterASTNode doneMarker = builder_.getLatestDoneMarker();
      if (doneMarker != null && doneMarker.getStartOffset() == builder_.rawTokenTypeStart(-1) && doneMarker.getTokenType() == TokenType.ERROR_ELEMENT) {
        parenList.add(Pair.create(((PsiBuilder.Marker)doneMarker).precede(), (PsiBuilder.Marker)null));
      }
    }
    while (true) {
      final IElementType tokenType = builder_.getTokenType();
      if (checkParens && (tokenType == state.braces[0].getLeftBraceType() || tokenType == state.braces[0].getRightBraceType() && !parenList.isEmpty())) {
        if (marker != null) {
          marker.done(chunkType);
          siblingList.addFirst(Pair.create(marker, 1));
          marker = null;
          tokenCount = 0;
        }
        if (tokenType == state.braces[0].getLeftBraceType()) {
          final Pair<PsiBuilder.Marker, Integer> prev = siblingList.peek();
          parenList.addFirst(Pair.create(builder_.mark(), prev == null ? null : prev.first));
        }
        checkSiblingsRunnable.run();
        builder_.advanceLexer();
        if (tokenType == state.braces[0].getRightBraceType()) {
          final Pair<PsiBuilder.Marker, PsiBuilder.Marker> pair = parenList.removeFirst();
          pair.first.done(chunkType);
          // drop all markers inside parens
          while (!siblingList.isEmpty() && siblingList.getFirst().first != pair.second) {
            siblingList.removeFirst();
          }
          siblingList.addFirst(Pair.create(pair.first, 1));
          checkSiblingsRunnable.run();
        }
      }
      else {
        if (marker == null) {
          marker = builder_.mark();
        }
        final boolean result = eatMoreCondition.parse(builder_, level + 1) && parser.parse(builder_, level + 1);
        if (result) {
          tokenCount++;
          totalCount++;
        }
        if (!result) {
          break;
        }
      }

      if (tokenCount >= MAX_CHILDREN_IN_TREE && marker != null) {
        marker.done(chunkType);
        siblingList.addFirst(Pair.create(marker, 1));
        checkSiblingsRunnable.run();
        marker = null;
        tokenCount = 0;
      }
    }
    if (marker != null) {
      marker.drop();
    }
    for (Pair<PsiBuilder.Marker, PsiBuilder.Marker> pair : parenList) {
      pair.first.drop();
    }
    return totalCount != 0;
  }

  private static class DummyBlockElementType extends IElementType implements ICompositeElementType{
    DummyBlockElementType() {
      super("DUMMY_BLOCK", Language.ANY);
    }

    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new DummyBlock();
    }
  }

  public static class DummyBlock extends CompositePsiElement {
    DummyBlock() {
      super(DUMMY_BLOCK);
    }

    @Override
    public PsiReference[] getReferences() {
      return PsiReference.EMPTY_ARRAY;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public boolean canNavigate() {
      return false;
    }

    @NotNull
    @Override
    public Language getLanguage() {
      return getParent().getLanguage();
    }
  }
}