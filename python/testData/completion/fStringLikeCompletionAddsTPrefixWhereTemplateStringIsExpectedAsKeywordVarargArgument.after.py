from string.templatelib import Template

def html(**chunks: Template):
    ...

variable = 42    
html(query=t"<span>{variable<caret>}")