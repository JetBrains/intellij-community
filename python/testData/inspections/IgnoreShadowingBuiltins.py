def o<caret>pen():
    pass

# Sentinel definition to make sure that other warnings are still present
def <weak_warning descr="Shadows built-in name 'input'">input</weak_warning>():
    pass
