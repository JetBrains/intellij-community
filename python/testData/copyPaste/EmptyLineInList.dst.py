# original function  # this is line 1 of the code.
def foo():
    print 'f00'
    def bar(num):
        for _ in range(num):
            print 'bar'

    bar(7)

<caret>    <selection></selection>
