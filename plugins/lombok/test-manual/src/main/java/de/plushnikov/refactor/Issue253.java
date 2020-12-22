package de.plushnikov.refactor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "payment")
public class Issue253 {

  private String paymentId;

  /**
   * Getter for paymentId
   *
   * @return current value of paymentId
   */
  @Id
  @Column(name = "payment_Id", unique = true, nullable = false, length = 64)
  public String getPaymentId() {
    return paymentId;
  }

  /**
   * Setter for paymentId
   * @param paymentId new value for paymentId
   */
  public void setPaymentId(String paymentId) {
    this.paymentId = paymentId;
  }
}
