public class RearrangerTrouble {


  private class DoNotRearrangeMe
    implements Comparable
  {

    public int compareTo(Object o) {
      return 0;
    }
  }
}