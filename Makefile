.PHONY: docker-build docker-up docker-clean docker-compile docker-update docker-test-perf

docker-build:
	docker-compose build

docker-up:
	docker-compose up -d

docker-clean:
	docker-compose down

# Recompiler le projet dans le conteneur en utilisant le code source monté dans /source
docker-compile:
	docker-compose exec app mvn clean package -f /source/pom.xml

docker-update:
	docker-compose exec app mvn clean package -f /source/pom.xml && \
	docker-compose exec app cp /source/target/GoofyFiles-0.0.1-SNAPSHOT.jar /app/app.jar && \
	docker-compose restart app

# Exécuter les tests de performance
docker-test-perf:
	docker-compose exec app mvn test -f /source/pom.xml -Dtest=ChunkingPerformanceTest
