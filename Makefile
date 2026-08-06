.PHONY: setup run test integration-test clean-local

setup:
	docker compose up -d --wait

run:
	mvn spring-boot:run

test:
	mvn test

integration-test:
	mvn verify

clean-local:
	mvn clean
	docker compose down
