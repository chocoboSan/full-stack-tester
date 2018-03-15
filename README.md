# full-stack-tester
arender - fullstack tester
Allows to test ARender configuration at high scale using a full stack test. Documents are queried from the back end server.

## Usage

Needs to be run on the rendition server side (as it loads documents from file system).

java -jar <jar\_with\_dependencies> http://pathToARenderFrontEnd/ \<fileName or directory containing files\> \<optionnal boolean to dump white pages to a folder\> 