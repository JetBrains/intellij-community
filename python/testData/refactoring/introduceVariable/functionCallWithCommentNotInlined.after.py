import subprocess as sp

a = sp.check_output(
    args=['python', '-c', 'print("Spam")'],
    # read errors too
    stderr=sp.STDOUT
)
print(a)