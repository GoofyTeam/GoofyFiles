.PHONY: docker-test-perf build up clean compile update test test-duplicate

build:
	docker-compose build

up:
	docker-compose up -d

clean:
	docker-compose down

# Recompiler le projet dans le conteneur en utilisant le code source monté dans /source
compile:
	docker-compose exec app mvn clean package -f /source/pom.xml

update:
	docker-compose exec app mvn clean package -DskipTests -f /source/pom.xml && \
	docker-compose exec app cp /source/target/GoofyFiles-0.0.1-SNAPSHOT.jar /app/app.jar && \
	docker-compose restart app

# Exécuter les tests de performance
docker-test-perf:
	docker-compose exec app mvn test -f /source/pom.xml -Dtest=ChunkingPerformanceTest

test-duplicate:
	docker-compose exec app mvn test -f /source/pom.xml -Dtest=DuplicationPerformanceTest

test:
	docker-compose exec app mvn test -f /source/pom.xml