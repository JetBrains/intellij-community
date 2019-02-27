.. Python Standard Library List documentation master file, created by
   sphinx-quickstart on Tue Mar 10 02:16:08 2015.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

Python Standard Library List
========================================================

This package includes lists of all of the standard libraries for Python 2.6, 2.7, 3.2, 3.3, and 3.4, along with the code for scraping the official Python docs to get said lists.

Listing the modules in the standard library? Wait, why on Earth would you care about that?!
===========================================================================================

Because knowing whether or not a module is part of the standard library will come in handy in `a project of mine <https://github.com/jackmaney/pypt>`_. `And I'm not the only one <http://stackoverflow.com/questions/6463918/how-can-i-get-a-list-of-all-the-python-standard-library-modules>`_ who would find this useful. Or, the TL;DR answer is that it's handy in situations when you're analyzing Python code and would like to find module dependencies.

After googling for a way to generate a list of Python standard libraries (and looking through the answers to the previously-linked Stack Overflow question), I decided that I didn't like the existing solutions. So, I started by writing a scraper for the TOC of the Python Module Index for each of the versions of Python above.

However, web scraping can be a fragile affair. Thanks to `a suggestion <https://github.com/jackmaney/python-stdlib-list/issues/1#issuecomment-86517208>`_ by `@ncoghlan <https://github.com/ncoghlan>`_, and some further help from `@birkenfeld <https://github.com/birkenfeld>`_ and `@epc <https://github.com/epc>`_, the population of the lists is now done by grabbing and parsing the Sphinx object inventory for the official Python docs of each relevant version.

Contents
========

.. toctree::
   :maxdepth: 2

   install
   usage
   fetch



Indices and tables
==================

* :ref:`genindex`
* :ref:`modindex`
* :ref:`search`
