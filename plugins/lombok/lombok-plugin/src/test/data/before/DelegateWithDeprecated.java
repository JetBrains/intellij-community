import lombok.Delegate;

class DelegateWithDeprecated {
	@Delegate private Bar bar;

	private interface Bar {
		@Deprecated
		void deprecatedAnnotation();
		/** @deprecated */
		void deprecatedComment();
		void notDeprecated();
	}
}