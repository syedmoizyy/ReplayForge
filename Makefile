.PHONY: setup run test integration-test seed-local clean-local

setup:
	docker compose up -d --wait

run:
	mvn spring-boot:run

test:
	mvn test

integration-test:
	mvn verify

seed-local:
	pwsh -File scripts/seed-workflows.ps1

clean-local:
	mvn clean
	docker compose down
