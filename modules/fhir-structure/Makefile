lint:
	clj-kondo --lint src test deps.edn

build:
	clojure -T:build compile

test: build
	clojure -M:test:kaocha --profile :ci

test-coverage: build
	clojure -M:test:coverage

deps-tree:
	clojure -X:deps tree

deps-list:
	clojure -X:deps list

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: lint build test test-coverage deps-tree deps-list clean
