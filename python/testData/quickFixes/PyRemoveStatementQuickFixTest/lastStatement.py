class B(object):
    def __init__(self): # error
        print ""
        <warning descr="Cannot return a value from __init__">return <caret>1</warning>
