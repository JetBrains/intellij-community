__author__ = 'catherine'

import urllib2
import datetime
from BeautifulSoup import BeautifulSoup
from xml.dom import minidom

exclude = ['StringIO', 'cStringIO']
exclude_builtin = ['coerce', 'bytearray', 'apply', 'bin', 'bytes', 'format', 'buffer']

def get_address(version):
  if version == 3.2:
    return "http://docs.python.org/py3k/py-modindex.html"
  return "http://docs.python.org/release/" + str(version) + "/modindex.html"

def get_builtin_address(version):
  if version == 3.2:
    return "http://docs.python.org/py3k/library/functions.html"
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
    if tt is not None and str(tt.text) not in exclude_builtin:
      functions_set.add(str(tt.text))
  page.close()

  if version < 3.0:
    functions_set.add('coerce')
    functions_set.add('apply')
    functions_set.add('buffer')
  if version > 2.5:
    functions_set.add('bytearray')
    functions_set.add('bin')
    functions_set.add('bytes')
    functions_set.add('format')
  return functions_set


def get_modules(version):
  page = urllib2.urlopen(get_address(version))
  soup = BeautifulSoup(page)

  modules = dict()
  if version < 2.6:
    for dl in soup('dt'):
      a = dl.findChild('a')
      tt = dl.findChild('tt')
      if tt is not None:
        if a is not None:
          modules[str(tt.text)] = a['href']
        else:
          modules[str(tt.text)] = None
  else:
    for dl in soup('td'):
      a = dl.findChild('a')
      tt = dl.findChild('tt')
      if tt is not None:
        if a is not None:
          modules[str(tt.text)] = a['href']
        else:
          modules[str(tt.text)] = None
  page.close()
  return modules

all_modules = set()


def get_functions_from_page(link, module):
  functions = set()
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
      if func in exclude or (module[0] + "." + func) in exclude:
        continue
      if func.startswith(module[0] + "."):
        functions.add(func)
      else:
        functions.add(module[0] + "." + func)
  page.close()
  return functions

def get_subsections(link):
  links = list()
  prefix = link[:link.rfind('/')+1]
  page = urllib2.urlopen(link)
  soup = BeautifulSoup(page)
  child_link = soup.find("ul", {'class':"ChildLinks"})
  if child_link:
    children = child_link.findChildren('a')
    for a in children:
      links.append(prefix+a['href'])
    page.close()

  return links

def get_supported_functions(version):
  global all_modules
  modules = get_modules(version)
  all_modules = all_modules.union(modules.keys())

  supported = set()

  for module in modules.items():
    if module[1] == None:
      continue
    link = "http://docs.python.org/release/" + str(version) + "/" + module[1]
    functions = get_functions_from_page(link, module)
    supported = supported.union(functions)

    links = get_subsections(link)
    for l in links:
      functions = get_functions_from_page(l, module)
      supported = supported.union(functions)
  return supported


doc = minidom.Document()
all_versions = (2.4, 2.5, 2.6, 2.7, 3.0, 3.1, 3.2)

all_functions = set()
root_elem = doc.createElement("root")

print ("Start searching for all functions")
for version in all_versions:
  print ("version " + str(version) + " started at " + str(datetime.datetime.now()))
  supported = get_supported_functions(version)
  all_functions = all_functions.union(supported)
  builtins = get_builtins(version)
  all_functions = all_functions.union(builtins)

print ("Start searching unsupported functions")
for version in all_versions:
  print ("version " + str(version) + " started at " + str(datetime.datetime.now()))
  version_elem = doc.createElement("python")
  version_elem.setAttribute('version', str(version))
  supported = get_supported_functions(version)
  builtins = get_builtins(version)
  supported = supported.union(builtins)
  unsupported = all_functions.difference(supported)

  unsupported_mods = all_modules.difference(get_modules(version))

  for function in unsupported:
    elem = doc.createElement("func")
    text = doc.createTextNode(function)
    elem.appendChild(text)
    version_elem.appendChild(elem)

  for module in unsupported_mods:
    elem = doc.createElement("module")
    text = doc.createTextNode(module)
    elem.appendChild(text)
    version_elem.appendChild(elem)

  root_elem.appendChild(version_elem)

doc.appendChild(root_elem)
print ("Versions processing finished at " + str(datetime.datetime.now()))
file_handler = open("versions.xml", 'w')
doc.writexml(file_handler)
file_handler.close()
