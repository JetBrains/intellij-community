def __init__(self):
    for base in self__class__.__bases__:
        <selection>try: base.__init__(self)
        except AttributeError: pass</selection>
