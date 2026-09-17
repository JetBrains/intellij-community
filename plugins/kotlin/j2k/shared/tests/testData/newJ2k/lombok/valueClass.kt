// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: @Value makes every field final and private, so it maps onto an immutable data class
package test

data class Money(val currency: String?, val amount: Long, val note: String?)
