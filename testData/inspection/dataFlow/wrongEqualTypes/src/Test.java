import java.util.Calendar;

public class Test {
  public void foo(Object c) {
    if (c instanceof Calendar) return;    
    if (c == Calendar.getInstance()) {}
  }
}