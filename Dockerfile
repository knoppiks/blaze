FROM openjdk:17-oracle

RUN microdnf upgrade && microdnf remove expat fontconfig freetype \
  aajohan-comfortaa-fonts fontpackages-filesystem gzip bzip2 tar libpng \
  binutils && microdnf clean all

RUN mkdir -p /app/data && chown 1001:1001 /app/data
COPY target/blaze-standalone.jar /app/

WORKDIR /app
USER 1001

ENV STORAGE="standalone"
ENV INDEX_DB_DIR="/app/data/index"
ENV TRANSACTION_DB_DIR="/app/data/transaction"
ENV RESOURCE_DB_DIR="/app/data/resource"

CMD ["java", "-jar", "blaze-standalone.jar", "-m", "blaze.core"]
