.PHONY: build status capture-home check install run use restore memory brightness-permission test
build:
	./build.sh
status:
	./tv.sh status
capture-home:
	./tv.sh capture-home
check:
	./tv.sh check
install: build
	./tv.sh install
run:
	./tv.sh run
use:
	./tv.sh use
restore:
	./tv.sh restore
memory:
	./tv.sh memory
brightness-permission:
	./tv.sh brightness-permission
test:
	python3 -m unittest discover -s tests -v
