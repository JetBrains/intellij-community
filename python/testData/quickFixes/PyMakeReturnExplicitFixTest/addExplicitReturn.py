def func(x, y, z):
    <weak_warning descr="Missing return statement on some paths"><caret>if x:</weak_warning>
        return 42
    elif y:
        <weak_warning descr="'return' without value is inconsistent with other paths">return</weak_warning>
