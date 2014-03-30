class A:
    def __init__(self, first=True):
        pass

class B(A):
 def __init__(self, *args, **kwargs):
        super(B, self).__init__(*args, **kwargs)

b = B(fir<caret>)