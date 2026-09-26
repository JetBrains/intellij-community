def func():
    with open('file1.txt') as <caret>unused, open('file2.txt') as used:
        print(used)