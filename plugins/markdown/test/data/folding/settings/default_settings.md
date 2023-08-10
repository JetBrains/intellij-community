<fold text='front matter: ---
Some front matter
---' expand='false'>---
Some front matter
---</fold>


[Some link](<fold text='...' expand='false'>#some-extremely-long-link-that-should-be-folded</fold>)


<fold text='table: | Simple | Table     |...sed |' expand='true'>| Simple | Table     |
|--------|-----------|
| to be  | collapsed |</fold>


<fold text='code fence: ```text
Simple code fe...d
```' expand='true'>```text
Simple code fence
to be collapsed
```</fold>

<fold text='h1: # Some primary header' expand='true'># Some primary header

<fold text='h2: ## Some secondary header' expand='true'>## Some secondary header


<fold text='table of contents' expand='false'><!-- TOC -->
<fold text='unordered list: * [Some primary header...ader)' expand='true'>* [Some primary header](<fold text='...' expand='false'>#some-primary-header</fold>)
  * [Some secondary header](<fold text='...' expand='false'>#some-secondary-header</fold>)</fold>
<!-- TOC --></fold>
</fold></fold>