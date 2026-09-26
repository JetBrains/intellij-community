// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: @Value makes every field final and private, so it maps onto an immutable data class
package test;

import lombok.Value;

@Value
public class Money {
    String currency;

    long amount;

    private String note;
}
