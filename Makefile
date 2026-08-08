.PHONY: setup run test benchmark quality integration-test seed-local clean-local

setup:
	docker compose up -d --wait

run:
	mvn spring-boot:run

test:
	mvn test

benchmark:
	pwsh -File scripts/benchmark-replay.ps1

quality:
	mvn -Pquality verify

integration-test:
	mvn verify

seed-local:
	pwsh -File scripts/seed-workflows.ps1

clean-local:
	mvn clean
	docker compose down
