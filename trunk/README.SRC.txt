COMPILING JEDIT FROM SOURCE

To compile jEdit, you will need:

- Jakarta Ant. I use version 1.5; older versions might or might not
  work. Get it from <http://jakarta.apache.org>.

- A Java compiler, such as Sun's javac or IBM's jikes.

- (Optional) To build the HTML version of the documentation:

  - DocBook-XML 4.1.2 DTD and DocBook-XSL 1.53 (or later) stylesheets
    (<http://docbook.sourceforge.net>).
  - An XSLT processor, such as Xalan (<http://xml.apache.org>) or
    xsltproc (<http://xmlsoft.org/XSLT/>).

- (Optional) To build the PDF version of the documentation:

  - DocBook-XML 4.1.2 DTD and DocBook-DSSSL 1.76 (or later) stylesheets
    (<http://docbook.sourceforge.net>).

  - OpenJade 1.3 and OpenSP 1.3.4 (or later)
    (<http://openjade.sourceforge.net>).

  - A TeX implementation that includes PDF output capability.

Once you have all the necessary tools installed, run the 'dist' target
in the build.xml file to compile jEdit. If you want to build the docs,
first edit the `build.properties' file to specify the path to where the
DocBook XSL stylesheets are installed, then run the 'docs-html-xalan' or
'docs-html-xsltproc' target.

* A note about JDK versions

The Jikes compiler from IBM seems to have a problem where code compiled
against JDK 1.4 does not work under JDK 1.3. Sun's javac does not have
this problem.

If plan on running jEdit under both 1.3 and 1.4, I recommend you compile
it using javac.
