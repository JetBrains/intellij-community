def foo(f):
  for line in f:
    print(line)

with open("file", "r") as as_target:
  foo(<caret>)