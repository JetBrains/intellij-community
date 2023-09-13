class Dialog:
    def validate(self): pass

class B(Dialog):

    def validate(self):
        <selection>super().validate()</selection>