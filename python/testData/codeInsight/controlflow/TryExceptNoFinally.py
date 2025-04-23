try:
    value = 42
except ValueError:
    value = 13
except SomethingElse:
    value = 1342
    raise
else:
    value = 0
    
print(value)