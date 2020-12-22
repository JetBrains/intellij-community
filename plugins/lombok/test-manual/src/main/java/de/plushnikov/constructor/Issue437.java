package de.plushnikov.constructor;

import lombok.AllArgsConstructor;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class Issue437 {

	private static final Action<TransmittalActionDocument> TRANSMITTALACTION = Action.of(TransmittalActionDocument.class, MetaDocument.TRANSMITTALS,
			MetaDocument.LAST_TRANSMITTAL, MetaDocument::getTransmittals, MetaDocument::setLastTransmittal);
	private static final Action<CommentActionDocument> COMMENTACTION = Action.of(CommentActionDocument.class, MetaDocument.COMMENTS,
			MetaDocument.LAST_COMMENT,
			MetaDocument::getComments, MetaDocument::setLastComment);

  @AllArgsConstructor(staticName = "of")
	private static class Action<T extends ActionDocument> {

		private final Class<T> clazz;
		private final String collectionProperty;
		private final String lastActionProperty;
		private final Function<MetaDocument, Set<T>> collection;
		private final BiConsumer<MetaDocument, T> lastAction;

//    private Action(Class<T> clazz, String collectionProperty, String lastActionProperty, Function<MetaDocument, Set<T>> collection,
//                   BiConsumer<MetaDocument, T> lastAction) {
//      this.clazz = clazz;
//      this.collectionProperty = collectionProperty;
//      this.lastActionProperty = lastActionProperty;
//      this.collection = collection;
//      this.lastAction = lastAction;
//    }
//
//    static <T extends ActionDocument> Action<T> of(Class<T> clazz, String collectionProperty, String lastActionProperty,
//                                                   Function<MetaDocument, Set<T>> collection, BiConsumer<MetaDocument, T> lastAction) {
//      return new Action<>(clazz, collectionProperty, lastActionProperty, collection, lastAction);
//    }
  }

	private static class MetaDocument {

		static final String TRANSMITTALS = "transmittals";
		static final String COMMENTS = "comments";
		static final String LAST_TRANSMITTAL = "lastTransmittal";
		static final String LAST_COMMENT = "lastComment";

		private Set<TransmittalActionDocument> transmittals;
		private Set<CommentActionDocument> comments;
		private TransmittalActionDocument lastTransmittal;
		private CommentActionDocument lastComment;

		public Set<TransmittalActionDocument> getTransmittals() {
			return transmittals;
		}

		public void setLastTransmittal(TransmittalActionDocument lastAction) {
			lastTransmittal = lastAction;
		}

		public void setLastComment(CommentActionDocument lastComment) {
			this.lastComment = lastComment;
		}

		public Set<CommentActionDocument> getComments() {
			return comments;
		}
	}

	private static abstract class ActionDocument {

	}

	private static class TransmittalActionDocument extends ActionDocument {

	}

	private static class CommentActionDocument extends ActionDocument {

	}
}
