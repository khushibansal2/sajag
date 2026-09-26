.PHONY: test server web dash demo size vectors jvm all
test:    ; cd core && python3 -m unittest discover -s tests -p 'test_*.py' -v
server:  ; cd server && python3 -m unittest discover -s tests -p 'test_*.py' -v
web:     ; node --test web/test/codec.vectors.test.mjs
dash:    ; python3 web/build.py
demo:    ; cd core && python3 cli.py demo
size:    ; cd core && python3 cli.py size
vectors: ; cd core && python3 cli.py vectors 20 > ../jvm-vectors/vectors.json && \
           cd ../jvm-vectors && python3 tsv.py
jvm: vectors
	cd jvm-vectors && javac CodecVectorTest.java && java CodecVectorTest
all: test server jvm web dash
