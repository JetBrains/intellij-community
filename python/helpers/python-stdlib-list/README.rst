Python Standard Library List
----------------------------

This package includes lists of all of the standard libraries for Python 2.6, 2.7, 3.2, 3.3, 3.4, 3.5, and 3.6, along with the code for scraping the official Python docs to get said lists.

Listing the modules in the standard library? Wait, why on Earth would you care about that?!
===========================================================================================

Because knowing whether or not a module is part of the standard library will come in handy in `a project of mine <https://github.com/jackmaney/pypt>`_. `And I'm not the only one <http://stackoverflow.com/questions/6463918/how-can-i-get-a-list-of-all-the-python-standard-library-modules>`_ who would find this useful. Or, the TL;DR answer is that it's handy in situations when you're analyzing Python code and would like to find module dependencies.

After googling for a way to generate a list of Python standard libraries (and looking through the answers to the previously-linked Stack Overflow question), I decided that I didn't like the existing solutions. So, I started by writing a scraper for the TOC of the Python Module Index for each of the versions of Python above.

However, web scraping can be a fragile affair. Thanks to `a suggestion <https://github.com/jackmaney/python-stdlib-list/issues/1#issuecomment-86517208>`_ by `@ncoghlan <https://github.com/ncoghlan>`_, and some further help from `@birkenfeld <https://github.com/birkenfeld>`_ and `@epc <https://github.com/epc>`_, the population of the lists is now done by grabbing and parsing the Sphinx object inventory for the official Python docs of each relevant version.

Usage
=====

::

    >>> from stdlib_list import stdlib_list
    >>> libraries = stdlib_list("2.7")
    >>> libraries[:10]
    ['AL', 'BaseHTTPServer', 'Bastion', 'CGIHTTPServer', 'ColorPicker', 'ConfigParser', 'Cookie', 'DEVICE', 'DocXMLRPCServer', 'EasyDialogs']

For more details, check out `the docs <http://python-stdlib-list.readthedocs.org/en/latest/>`_.
