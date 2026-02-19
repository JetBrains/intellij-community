from string.templatelib import Template

def html(p: Template):
    ...

variable = 42    
html(t"<span>{variable<caret>}")