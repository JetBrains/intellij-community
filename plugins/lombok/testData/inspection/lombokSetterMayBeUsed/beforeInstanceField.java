// "Use lombok @Setter for 'bar'" "true"

public class CommentedClass {
  private int bar;
  private int fieldWithoutSetter;

  /*1*/public /*2*/void /*3*/setBar(int param) /*4*/{
    /*5*/bar<caret>/*6*/ = /*7*/param;
    /*8*/}
}