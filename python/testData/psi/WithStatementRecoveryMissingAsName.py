with foo() as :
    pass
with (foo() as ):
    pass
with foo() as, foo():
    pass