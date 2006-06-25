package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.MergingCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author max
 */
public class LayeredLexerEditorHighlighter extends LexerEditorHighlighter {
  private Map<IElementType, LayerDescriptor> myTokensToLayer = new HashMap<IElementType, LayerDescriptor>();
  private Map<LayerDescriptor, Mapper> myLayerBuffers = new HashMap<LayerDescriptor, Mapper>();
  private MappingSegments mySegments = new MappingSegments();
  private CharSequence myText;

  public LayeredLexerEditorHighlighter(SyntaxHighlighter highlighter, EditorColorsScheme scheme) {
    super(highlighter, scheme);
    setSegmentStorage(mySegments);
  }

  public void registerLayer(IElementType tokenType, LayerDescriptor layerHighlighter) {
    myTokensToLayer.put(tokenType, layerHighlighter);
    mySegments.removeAll();
  }

  public void unregisterLayer(IElementType tokenType) {
    final LayerDescriptor layer = myTokensToLayer.remove(tokenType);
    if (layer != null) {
      myLayerBuffers.remove(layer);
      mySegments.removeAll();
    }
  }

  public void setText(final CharSequence text) {
    myText = text;
    updateLayers();

    super.setText(text);
  }

  protected void updateLayers() {}

  public synchronized void documentChanged(DocumentEvent e) {
    myText = e.getDocument().getCharsSequence();
    updateLayers();

    super.documentChanged(e);
  }

  public HighlighterIterator createIterator(int startOffset) {
    return new LayeredHighlighterIterator(startOffset);
  }

  private class MappingSegments extends SegmentArrayWithData {
    MappedRange[] myRanges = new MappedRange[INITIAL_SIZE];

    public void removeAll() {
      super.removeAll();
      Arrays.fill(myRanges, null);
    }

    public void setElementAt(int i, int startOffset, int endOffset, int data) {
      super.setElementAt(i, startOffset, endOffset, data);
      myRanges = relocateArray(myRanges, i+1);
      final MappedRange range = myRanges[i];
      if (range != null) {
        range.mapper.removeMapping(range);
        myRanges[i] = null;
      }

      updateMappingForToken(i);
    }

    public void remove(int startIndex, int endIndex) {
      for (int i = startIndex; i < endIndex; i++) {
        final MappedRange range = myRanges[i];
        if (range != null) {
          range.mapper.removeMapping(range);
        }
      }

      myRanges = remove(myRanges, startIndex, endIndex);
      super.remove(startIndex, endIndex);
    }

    public void replace(int startOffset, SegmentArrayWithData data, int len) {
      super.replace(startOffset, data, len);
      for (int i = startOffset; i < startOffset + len; i++) {
        updateMappingForToken(i);
      }
    }

    public void insert(SegmentArrayWithData segmentArray, int startIndex) {
      super.insert(segmentArray, startIndex);

      final int newCount = segmentArray.getSegmentCount();
      final MappedRange[] newRanges = new MappedRange[newCount];

      myRanges = insert(myRanges, newRanges, startIndex, newCount);

      int endIndex = startIndex + segmentArray.getSegmentCount();

      for (int i = startIndex; i < endIndex; i++) {
        updateMappingForToken(i);
      }
    }

    private void updateMappingForToken(final int i) {
      final short data = getSegmentData(i);
      final IElementType token = unpackToken(data);
      final Mapper mapper = getMappingDocument(token);
      final MappedRange oldMapping = myRanges[i];
      if (mapper != null) {
        if (oldMapping != null) {
          if (oldMapping.mapper == mapper && oldMapping.outerToken == token) {
            mapper.updateMapping(i, oldMapping);
          }
          else {
            oldMapping.mapper.removeMapping(oldMapping);
            myRanges[i] = mapper.insertMapping(i, token);
          }
        }
        else {
          myRanges[i] = mapper.insertMapping(i, token);
        }
      }
      else {
        if (oldMapping != null) {
          oldMapping.mapper.removeMapping(oldMapping);
          myRanges[i] = null;
        }
      }
    }
  }

  private class Mapper implements HighlighterClient {
    private final DocumentImpl doc;
    private final LexerEditorHighlighter highlighter;
    private final String mySeparator;
    private final Map<IElementType, TextAttributes> myAttributesMap = new HashMap<IElementType, TextAttributes>();
    private final SyntaxHighlighter mySyntaxHighlighter;
    private final TextAttributesKey myBackground;


    public Mapper(LayerDescriptor descriptor) {
      doc = new DocumentImpl("");
      doc.dontAssertWriteAccess();
      
      mySyntaxHighlighter = descriptor.getLayerHighlighter();
      myBackground = descriptor.getBackgroundKey();
      highlighter = new LexerEditorHighlighter(mySyntaxHighlighter, getScheme());
      mySeparator = descriptor.getTokenSeparator();
      highlighter.setEditor(this);
      doc.addDocumentListener(highlighter);
    }

    public TextAttributes getAttributes(IElementType tokenType) {
      TextAttributes attrs = myAttributesMap.get(tokenType);
      if (attrs == null) {
        attrs = convertAttributes(SyntaxHighlighterBase.pack(mySyntaxHighlighter.getTokenHighlights(tokenType), myBackground));
        myAttributesMap.put(tokenType, attrs);
      }
      return attrs;
    }

    public HighlighterIterator createIterator(MappedRange mapper, int shift) {
      final int rangeStart = mapper.range.getStartOffset();
      final int rangeEnd = mapper.range.getEndOffset();
      return new LimitedRangeHighlighterIterator(highlighter.createIterator(rangeStart + shift), rangeStart, rangeEnd);
    }

    public Project getProject() {
      return getClient().getProject();
    }

    public void repaint(int start, int end) {
      // TODO: map ranges to outer document
    }

    public Document getDocument() {
      return LayeredLexerEditorHighlighter.this.getDocument();
    }

    public void updateMapping(final int tokenIndex, final MappedRange oldMapping) {
      CharSequence tokenText = getTokenText(tokenIndex);

      final int start = oldMapping.range.getStartOffset();
      final int end = oldMapping.range.getEndOffset();
      doc.replaceString(start, end, tokenText);

      oldMapping.range = doc.createRangeMarker(start, start + tokenText.length());
    }

    public MappedRange insertMapping(int tokenIndex, IElementType outerToken) {
      CharSequence tokenText = getTokenText(tokenIndex);

      final int length = tokenText.length();

      MappedRange predecessor = findPredecessor(tokenIndex);

      int insertOffset = predecessor != null ? predecessor.range.getEndOffset() : 0;
      doc.insertString(insertOffset, new MergingCharSequence(mySeparator, tokenText));
      insertOffset += mySeparator.length();

      MappedRange newRange = new MappedRange();
      newRange.mapper = this;
      newRange.range = doc.createRangeMarker(insertOffset, insertOffset + length);
      newRange.outerToken = outerToken;

      return newRange;
    }

    private CharSequence getTokenText(final int tokenIndex) {
      return myText.subSequence(mySegments.getSegmentStart(tokenIndex),
                                mySegments.getSegmentEnd(tokenIndex));
    }

    @Nullable
    private MappedRange findPredecessor(int token) {
      token--;
      while (token >= 0) {
        final MappedRange mappedRange = mySegments.myRanges[token];
        if (mappedRange != null && mappedRange.mapper == this) return mappedRange;
        token--;
      }

      return null;
    }

    public void removeMapping(MappedRange mapping) {
      final int start = mapping.range.getStartOffset();
      final int end = mapping.range.getEndOffset();

      doc.deleteString(start - mySeparator.length(), end);
    }

  }

  private static class MappedRange {
    public RangeMarker range;
    public Mapper mapper;
    public IElementType outerToken;
  }

  @Nullable
  private Mapper getMappingDocument(IElementType token) {
    final LayerDescriptor descriptor = myTokensToLayer.get(token);
    if (descriptor == null) return null;

    Mapper mapper = myLayerBuffers.get(descriptor);
    if (mapper == null) {
      mapper = new Mapper(descriptor);
      myLayerBuffers.put(descriptor, mapper);
    }

    return mapper;
  }

  private class LayeredHighlighterIterator implements HighlighterIterator {
    private final HighlighterIterator myBaseIterator;
    private HighlighterIterator myLayerIterator;
    private int myLayerStartOffset = 0;
    private LayeredLexerEditorHighlighter.Mapper myCurrentMapper;

    public LayeredHighlighterIterator(int offset) {
      myBaseIterator = LayeredLexerEditorHighlighter.super.createIterator(offset);
      if (!myBaseIterator.atEnd()) {
        int shift = offset - myBaseIterator.getStart();
        initLayer(shift);
      }
    }

    private void initLayer(final int shiftInToken) {
      if (myBaseIterator.atEnd()) {
        myLayerIterator = null;
        myCurrentMapper = null;
        return;
      }

      MappedRange mapping = mySegments.myRanges[((HighlighterIteratorImpl)myBaseIterator).currentIndex()];
      if (mapping != null) {
        myCurrentMapper = mapping.mapper;
        myLayerIterator = myCurrentMapper.createIterator(mapping, shiftInToken);
        myLayerStartOffset = myBaseIterator.getStart() - mapping.range.getStartOffset();
      }
      else {
        myCurrentMapper = null;
        myLayerIterator = null;
      }
    }

    public TextAttributes getTextAttributes() {
      if (myCurrentMapper != null) {
        return myCurrentMapper.getAttributes(getTokenType());
      }

      return myBaseIterator.getTextAttributes();
    }

    public int getStart() {
      if (myLayerIterator != null) {
        return myLayerIterator.getStart() + myLayerStartOffset;
      }
      return myBaseIterator.getStart();
    }

    public int getEnd() {
      if (myLayerIterator != null) {
        return myLayerIterator.getEnd() + myLayerStartOffset;
      }
      return myBaseIterator.getEnd();
    }

    public IElementType getTokenType() {
      return myLayerIterator != null ? myLayerIterator.getTokenType() : myBaseIterator.getTokenType();
    }

    public void advance() {
      if (myLayerIterator != null) {
        myLayerIterator.advance();
        if (!myLayerIterator.atEnd()) return;
      }
      myBaseIterator.advance();
      initLayer(0);
    }

    public void retreat() {
      if (myLayerIterator != null) {
        myLayerIterator.retreat();
        if (!myLayerIterator.atEnd()) return;
      }

      myBaseIterator.retreat();
      initLayer(myBaseIterator.atEnd() ? 0 : myBaseIterator.getEnd() - myBaseIterator.getStart());
    }

    public boolean atEnd() {
      return myBaseIterator.atEnd();
    }
  }
}
