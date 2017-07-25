package training.learn;

/**
 * Created by jetbrains on 31/01/16.
 */


public class MyPair {
  private String status;
  private long timestamp;

  public MyPair() {
    status = "";
    timestamp = 0;
  }

  public MyPair(String status, long timestamp) {
    this.status = status;
    this.timestamp = timestamp;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(int timestamp) {
    this.timestamp = timestamp;
  }
}
