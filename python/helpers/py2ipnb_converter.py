import sys
import nbformat
from nbformat.v4 import new_code_cell, new_notebook

import codecs

name = sys.argv[-1][:-3]
source = name + ".py"
target = name + ".ipynb"


def parse_python(file):
    with open(file, "r") as file_descriptor:
        lines = []
        for line in file_descriptor:
            line = line.strip()
            if line.startswith('# In[') and line.endswith(']:') and lines:
                yield "".join(lines)
                lines = []
                continue
            lines.append(line)
        if lines:
            yield "".join(lines)

cells = []
for cell_content in parse_python(source):
    cells.append(new_code_cell(source=cell_content))

nb0 = new_notebook(cells=cells, metadata={'language': 'python',})

with codecs.open(target, encoding='utf-8', mode='w') as f:
    nbformat.write(nb0, f, 4)
