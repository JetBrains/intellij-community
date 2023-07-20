// "Use lombok @Getter for 'bar'" "true"

public class CommentedClass {
  private int bar;
  private int fieldWithoutGetter;

  /*1*/public /*2*/int /*3*/getBar() /*4*/{
    /*5*/return /*6*/bar<caret>/*7*/;
    /*8*/}
}