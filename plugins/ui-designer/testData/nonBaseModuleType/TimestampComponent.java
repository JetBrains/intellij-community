import javax.swing.*;
import java.sql.Timestamp;

public class TimestampComponent extends JPanel {
  private String label = "";
  private int customValue = 0;

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public int getCustomValue() {
    return customValue;
  }

  public void setCustomValue(int value) {
    this.customValue = value;
  }

  public Timestamp getTimestamp() {
    return null;
  }

  public void setTimestamp(Timestamp ts) {
  }
}
