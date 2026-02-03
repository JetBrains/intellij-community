package com.example

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE_PARAMETER,
        AnnotationTarget.VALUE_PARAMETER)
annotation class MyComposable

@MyComposable
fun sourceFunction() {
    <selection>print(true)</selection>
}