from util import panic

def foo():
    panic("Error!")
    <warning descr="This code is unreachable">print("Should be reported as unreachable")</warning>