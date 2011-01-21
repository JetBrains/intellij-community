__author__ = 'catherine'

import urllib2
from BeautifulSoup import BeautifulSoup
from xml.dom import minidom

def get_address(version):
  return "http://docs.python.org/release/" + str(version) + "/modindex.html"

def get_builtin_address(version):
  if version > 2.5:
    return 'http://docs.python.org/release/' + str(version) + '/library/functions.html'
  else:
    return 'http://docs.python.org/release/' + str(version) + '/lib/built-in-funcs.html'

def get_builtins(version):
  address = get_builtin_address(version)
  page = urllib2.urlopen(address)
  soup = BeautifulSoup(page)
  functions_set = set()

  for dt in soup('dt'):
    tt = dt.findChild('tt')
    if tt is not None:
      functions_set.add(str(tt.text))
  page.close()
  return functions_set


def get_supported_functions(version):
  page = urllib2.urlopen(get_address(version))
  soup = BeautifulSoup(page)

  modules = dict()

  if version < 2.6:
    for dl in soup('dt'):
      a = dl.findChild('a')
      tt = dl.findChild('tt')
      if tt is not None and a is not None:
        modules[str(tt.text)] = a['href']
  else:
    for dl in soup('td'):
      a = dl.findChild('a')
      tt = dl.findChild('tt')
      if tt is not None and a is not None:
        modules[str(tt.text)] = a['href']

  unsupported = set()

  page.close()
  for module in modules.items():
    link = "http://docs.python.org/release/" + str(version) + "/" + module[1]
    page = urllib2.urlopen(link)
    soup = BeautifulSoup(page)
    for dl in soup('dl'):
      if version < 2.6:
        child = dl.findChild('tt')
        if child and child.get('class') == 'function':
          tt = child
        else:
          tt = None
      else:
        if dl.get('class') == 'function':
          tt = dl.findChild('tt', {'class':"descname"})
        else:
          tt = None
      if tt is not None:
        func = str(tt.text)
        if func.startswith(module[0] + "."):
          unsupported.add(func)
        else:
          unsupported.add(module[0] + "." + func)
    page.close()
  return unsupported
    
doc = minidom.Document()
all_versions = (2.4, 2.5, 2.6, 2.7, 3.0, 3.1)

all_functions = set()
root_elem = doc.createElement("root")

for version in all_versions:
  supported = get_supported_functions(version)
  all_functions = all_functions.union(supported)
  builtins = get_builtins(version)
  all_functions = all_functions.union(builtins)

for version in all_versions:
  version_elem = doc.createElement("python")
  version_elem.setAttribute('version', str(version))
  supported = get_supported_functions(version)
  builtins = get_builtins(version)
  supported = supported.union(builtins)
  unsupported = all_functions.difference(supported)

  for function in unsupported:
    elem = doc.createElement("func")
    text = doc.createTextNode(function)
    elem.appendChild(text)
    version_elem.appendChild(elem)
  root_elem.appendChild(version_elem)
doc.appendChild(root_elem)

file_handler = open("versions.xml", 'w')
doc.writexml(file_handler)
file_handler.close()
