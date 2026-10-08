# syntax=docker/dockerfile:1
#
# Imagens do SPP. Alvos:
#   tooling  JDK 25 + Maven (do wrapper do projeto). Usado pelo serviço "tests" do docker-compose.
#   builder  compila e empacota os três serviços (uber-jars).
#   service  runtime enxuto de UM serviço:  --build-arg MODULE=coordinator|reserve|merchant
#
# Exemplo:  docker build --target service --build-arg MODULE=reserve -t spp-reserve .

# ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS tooling
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw ./
# mvnw pode chegar com CRLF (checkout no Windows): normaliza e baixa o Maven pinado no wrapper.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
 && ./mvnw -v \
 && MVN_BIN="$(find /root/.m2/wrapper -type f -name mvn -path '*/bin/*' | head -n 1)" \
 && cp -r "$(dirname "$(dirname "$MVN_BIN")")" /opt/maven \
 && ln -s /opt/maven/bin/mvn /usr/local/bin/mvn \
 && rm -rf /root/.m2

# ---------------------------------------------------------------------------------------------
FROM tooling AS builder
COPY pom.xml ./
COPY testing-support ./testing-support
COPY coordinator ./coordinator
COPY reserve ./reserve
COPY merchant ./merchant
COPY contracts ./contracts
# O cache do ~/.m2 evita baixar o mundo a cada build (requer BuildKit, padrão no Docker atual).
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS service
ARG MODULE
ARG VERSION=0.1
RUN useradd --system --create-home --shell /usr/sbin/nologin spp
WORKDIR /app
COPY --from=builder --chown=spp:spp /workspace/${MODULE}/target/${MODULE}-${VERSION}.jar app.jar
USER spp
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
