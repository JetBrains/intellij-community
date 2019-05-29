<hh1>Test Markdown document
======================</hh1>

<hh2>Text
----</hh2>

Here is a paragraph with bold text. <bold><boldm>**</boldm>This is some bold text.<boldm>**</boldm></bold> Here is a
paragraph with bold text. <bold><boldm>__</boldm>This is also some bold text.<boldm>__</boldm></bold>

Here is another one with italic text. <italic><italicm>*</italicm>This is some italic text.<italicm>*</italicm></italic> Here is
another one with italic text. <italic><italicm>_</italicm>This is some italic text.<italicm>_</italicm></italic>

Here is another one with struckout text. <strike>~~This is some struckout text.~~</strike>


<hh2>Links
-----</hh2>

Autolink: <alink><http://example.com></alink>

Link: <link_text>[Example]</link_text>(<link_dest>http://example.com</link_dest>)

Reference style <link_text>[link]</link_text><link_label>[1]</link_label>.

<link_def><link_label>[1]</link_label>: <link_dest>http://example.com</link_dest>  <link_title>"Example"</link_title></link_def>


<hh2>Images
------</hh2>

Image: <link_img>!<link_text>[My image]</link_text>(<link_dest>http://www.foo.bar/image.png</link_dest>)</link_img>

<hh2>Headers
-------</hh2>

<hh1># First level title</hh1>
<hh2>## Second level title</hh2>
<hh3>### Third level title</hh3>
<hh4>#### Fourth level title</hh4>
<hh5>##### Fifth level title</hh5>
<hh6>###### Sixth level title</hh6>

<hh3>### Title with [link](http://localhost)</hh3>
<hh3>### Title with ![image](http://localhost)</hh3>

<hh2>Code
----</hh2>

<code_fence>```
This
  is
    code
      fence
```</code_fence>

Inline <code_span>`code span in a`</code_span> paragraph.

This is a code block:

    <code_block>/**
     * Sorts the specified array into ascending numerical order.
     *
     * <p>Implementation note: The sorting algorithm is a Dual-Pivot Quicksort
     * by Vladimir Yaroslavskiy, Jon Bentley, and Joshua Bloch. This algorithm
     * offers O(n log(n)) performance on many data sets that cause other
     * quicksorts to degrade to quadratic performance, and is typically
     * faster than traditional (one-pivot) Quicksort implementations.
     *
     * @param a the array to be sorted
     */
    public static void sort(byte[] a) {
        DualPivotQuicksort.sort(a);
    }</code_block>

<hh2>Quotes
------</hh2>

<quote>> This is the first level of quoting.
>
> > This is nested blockquote.
>
> Back to the first level.
</quote>

<quote>> A list within a blockquote:
>
> *	asterisk 1
> *	asterisk 2
> *	asterisk 3
</quote>

<quote>> Formatting within a blockquote:
>
> ### header
> Link: [Example](http://example.com)
</quote>


<hh2>Html
-------</hh2>

This is inline <span>html</html>.
And this is an html block.

<table>
  <tr>
    <th>Column 1</th>
    <th>Column 2</th>
  </tr>
  <tr>
    <td>Row 1 Cell 1</td>
    <td>Row 1 Cell 2</td>
  </tr>
  <tr>
    <td>Row 2 Cell 1</td>
    <td>Row 2 Cell 2</td>
  </tr>
</table>

<hh2>Horizontal rules
----------------</hh2>

---

___


***


<hh2>Lists
-----</hh2>

Unordered list:

<ul>*	asterisk 1
*	asterisk 2
*	asterisk 3
</ul>

Ordered list:

<ol>1.	First
2.	Second
3.	Third
</ol>

Mixed:

<ol>1. First
2. Second:
	<ul>* Fee
	* Fie
	* Foe</ul>
3. Third
</ol>

Tables:

| Header 1 | Header 2 |
| -------- | -------- |
| Data 1   | Data 2   |