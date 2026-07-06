SHELL := /bin/bash

.PHONY: all build package external package-external clean clean-loader typecheck node loader jar verify help

all: package

build: node loader

node:
	npm run build:node-runtime

typecheck:
	npm run typecheck

loader:
	./gradlew :loader-java:build

jar: loader

package: package-external

external: package-external

package-external:
	npm run package:external

verify: typecheck loader package-external

clean: clean-loader
	rm -rf dist/external-launcher

clean-loader:
	./gradlew :loader-java:clean

help:
	@printf '%s\n' \
	  'AkivCraft make targets:' \
	  '  make              Build node runtime, loader jar, package external launcher, upload jar to Filebin' \
	  '  make build        Build node runtime and Java loader' \
	  '  make package      Generate dist/external-launcher and upload loader jar to Filebin' \
	  '  make typecheck    Run node-runtime TypeScript typecheck' \
	  '  make loader       Build loader-java' \
	  '  make clean        Clean loader build and dist/external-launcher' \
	  '  make verify       Typecheck, build loader, package external launcher'
