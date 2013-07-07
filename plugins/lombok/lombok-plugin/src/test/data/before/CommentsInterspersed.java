import lombok.Getter;

public /*bla */ class CommentsInterspersed {
  /**
   * javadoc for field
   */
  private int x;

  private /* bla2 */
  @Getter
  String test = "foo"; //$NON-NLS-1$

  /**
   * Javadoc on method
   */
  public native void gwtTest(); /*-{
		javascript;
	}-*/
} //haha!
//hahaha!

//hahahaha!

