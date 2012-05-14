import java.util.List;
import java.util.Vector;

public class RearrangementTest46 {
  protected final int     sqlType;
  protected final String  dbColumnName;
  protected final String  table;
  protected       boolean isInvalid;
  protected       boolean isMandatory;
  protected List<Integer> validator = new Vector<Integer>();
  protected Object value;
  protected String invalidText;

  RearrangementTest46() {
    dbColumnName = "column";
    table = "table";
    sqlType = 0;
  }
}
