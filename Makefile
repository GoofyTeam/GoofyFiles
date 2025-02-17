.PHONY: build up clean compile update

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
	docker-compose exec app mvn clean package -f /source/pom.xml && \
	docker-compose exec app cp /source/target/GoofyFiles-0.0.1-SNAPSHOT.jar /app/app.jar && \
	docker-compose restart app
