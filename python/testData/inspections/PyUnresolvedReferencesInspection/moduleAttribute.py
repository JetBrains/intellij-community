class OldStyle:
    pass

class NewStyle(object):
    pass

x = OldStyle()
y = NewStyle()
print(OldStyle.__module__)
print(NewStyle.__module__)
print(x.__module__)
print(y.__module__)
