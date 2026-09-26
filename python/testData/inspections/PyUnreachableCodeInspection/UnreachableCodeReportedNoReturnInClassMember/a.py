from util import panic
from util import Alarmist


def foo():
    x = Alarmist()
    x.panic("Error!")
    <warning descr="This code is unreachable">print("Should be reported as unreachable")</warning>