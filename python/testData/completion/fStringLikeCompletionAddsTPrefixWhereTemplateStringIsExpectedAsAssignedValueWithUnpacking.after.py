from string.templatelib import Template

variable = 42    
s: Template
_, s = (42, t"<span>{variable<caret>}")