// file

/* file */

/** CommentOwners */
public class CommentOwners {

  /** field */
  private final int field = 42;

  /** constructor */
  public CommentOwners(int t) {

  }

  /** method */
  void method(/* fun param before */ int a /* fun param after */) {
    /* method call */
    method(/* call arg before */ 42 /* call arg after */);

    // cycle
    while (true) {
      // break
      break;
    }

    // if
    if (true) {

    }
    else {

    }

    // localValueDefinition
    final int localValueDefinition = 42;
  }

  /** NestedClass */
  class NestedClass {

  }
}

/** enum */
enum MyBooleanEnum {
  /** enum true value */
  TRUE,

  /** enum false value */
  FALSE
}