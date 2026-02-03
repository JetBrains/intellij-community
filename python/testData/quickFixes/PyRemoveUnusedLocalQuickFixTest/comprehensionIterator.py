def func():
    fragments = ' '.join({'{42}' for <caret>unused in range(10)})