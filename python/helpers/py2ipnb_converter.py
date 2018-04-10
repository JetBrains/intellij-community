import codecs
import nbformat
import sys
from nbformat.v4 import new_code_cell, new_notebook

name = sys.argv[-1][:-3]
source = name + ".py"
target = name + ".ipynb"


def parse_python(file):
    with open(file, "r") as file_descriptor:
        lines = []
        for line in file_descriptor:
            line_stripped = line.strip()
            if line_stripped.startswith('# In[') and line_stripped.endswith(']:') and lines:
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
