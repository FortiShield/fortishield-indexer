FROM gradle:jdk17-alpine AS builder
USER gradle
WORKDIR /home/fortishield-indexer
COPY --chown=gradle:gradle . /home/fortishield-indexer
RUN gradle clean


FROM eclipse-temurin:17-jdk-alpine
RUN apk add git && \
		apk add curl && \
    addgroup -g 1000 fortishield-indexer && \
    adduser -u 1000 -G fortishield-indexer -D -h /home/fortishield-indexer fortishield-indexer && \
    chmod 0775 /home/fortishield-indexer && \
    chown -R 1000:0 /home/fortishield-indexer
USER fortishield-indexer
COPY --from=builder --chown=1000:0 /home/fortishield-indexer /home/fortishield-indexer
WORKDIR /home/fortishield-indexer
RUN git config --global --add safe.directory /home/fortishield-indexer
EXPOSE 9200 9300
