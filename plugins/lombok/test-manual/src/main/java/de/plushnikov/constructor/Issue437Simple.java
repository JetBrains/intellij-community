package de.plushnikov.constructor;

import lombok.AllArgsConstructor;

import java.util.function.BiConsumer;

public class Issue437Simple {

  private static final Action<TransmittalActionDocument> TRANSMITTALACTION = Action.of(MetaDocument::setLastTransmittal);
  private static final Action<CommentActionDocument> COMMENTACTION = Action.of(MetaDocument::setLastComment);

  @AllArgsConstructor(staticName = "of")
  private static class Action<T extends ActionDocument> {

    private final BiConsumer<MetaDocument, T> lastAction;
  }

  private static abstract class ActionDocument {
  }

  private static class MetaDocument {
    private CommentActionDocument lastComment;
    private TransmittalActionDocument lastTransmittal;

    public void setLastTransmittal(TransmittalActionDocument lastAction) {
      lastTransmittal = lastAction;
    }

    public void setLastComment(CommentActionDocument lastComment) {
      this.lastComment = lastComment;
    }
  }

  private static class TransmittalActionDocument extends ActionDocument {
  }

  private static class CommentActionDocument extends ActionDocument {
  }
}
