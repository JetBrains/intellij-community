from string.templatelib import Template

def html(**chunks: Template):
    ...

variable = 42    
html(query="<span>{vari<caret>")