################################################################################
# This Dockerfile was generated from the template at distribution/src/docker/Dockerfile
#
# Beginning of multi stage Dockerfile
################################################################################
<% /*
  This file is passed through Groovy's SimpleTemplateEngine, so dollars and backslashes
  have to be escaped in order for them to appear in the final Dockerfile. You
  can also comment out blocks, like this one. See:

  https://docs.groovy-lang.org/latest/html/api/groovy/text/SimpleTemplateEngine.html

  We use control-flow tags in this file to conditionally render the content. The
  layout/presentation here has been adjusted so that it looks reasonable when rendered,
  at the slight expense of how it looks here.
*/ %>
################################################################################
# Build stage 0 `builder`:
# Extract OpenSearch artifact
################################################################################

FROM ${base_image} AS builder
# `tini` is a tiny but valid init for containers. This is used to cleanly
# control how OpenSearch and any child processes are shut down.
#
# The tini GitHub page gives instructions for verifying the binary using
# gpg, but the keyservers are slow to return the key and this can fail the
# build. Instead, we check the binary against the published checksum.
RUN set -eux ; \\
    tini_bin="" ; \\
    case "\$(arch)" in \\
        aarch64) tini_bin='tini-arm64' ;; \\
        x86_64)  tini_bin='tini-amd64' ;; \\
        *) echo >&2 ; echo >&2 "Unsupported architecture \$(arch)" ; echo >&2 ; exit 1 ;; \\
    esac ; \\
    curl --retry 8 -S -L -O https://github.com/krallin/tini/releases/download/v0.19.0/\${tini_bin} ; \\
    curl --retry 8 -S -L -O https://github.com/krallin/tini/releases/download/v0.19.0/\${tini_bin}.sha256sum ; \\
    sha256sum -c \${tini_bin}.sha256sum ; \\
    rm \${tini_bin}.sha256sum ; \\
    mv \${tini_bin} /tini ; \\
    chmod +x /tini

RUN mkdir /usr/share/fortishield-indexer
WORKDIR /usr/share/fortishield-indexer

${source_opensearch}

RUN tar zxf /opt/opensearch.tar.gz --strip-components=1
RUN sed -i -e 's/OPENSEARCH_DISTRIBUTION_TYPE=tar/OPENSEARCH_DISTRIBUTION_TYPE=docker/' /usr/share/fortishield-indexer/bin/opensearch-env
RUN mkdir -p config config/jvm.options.d data logs
RUN chmod 0775 config config/jvm.options.d data logs
COPY config/opensearch.yml config/log4j2.properties config/
RUN chmod 0660 config/opensearch.yml config/log4j2.properties

################################################################################
# Build stage 1 (the actual OpenSearch image):
#
# Copy opensearch from stage 0
# Add entrypoint
################################################################################

FROM ${base_image}

ENV OPENSEARCH_CONTAINER true

RUN  sed -i 's/mirrorlist/#mirrorlist/g' /etc/yum.repos.d/CentOS-Linux-* && \\
     sed -i 's|#baseurl=http://mirror.centos.org|baseurl=http://vault.epel.cloud|g' /etc/yum.repos.d/CentOS-Linux-* && \\
     for iter in {1..10}; do \\
      ${package_manager} update --setopt=tsflags=nodocs -y && \\
      ${package_manager} install --setopt=tsflags=nodocs -y \\
        nc shadow-utils zip unzip && \\
      ${package_manager} clean all && exit_code=0 && break || exit_code=\$? && echo "${package_manager} error: retry \$iter in 10s" && \\
      sleep 10; \\
    done; \\
    (exit \$exit_code)

RUN groupadd -g 1000 fortishield-indexer && \\
    adduser -u 1000 -g 1000 -G 0 -d /usr/share/fortishield-indexer fortishield-indexer && \\
    chmod 0775 /usr/share/fortishield-indexer && \\
    chown -R 1000:0 /usr/share/fortishield-indexer

WORKDIR /usr/share/fortishield-indexer
COPY --from=builder --chown=1000:0 /usr/share/fortishield-indexer /usr/share/fortishield-indexer
COPY --from=builder --chown=0:0 /tini /tini

# Replace OpenJDK's built-in CA certificate keystore with the one from the OS
# vendor. The latter is superior in several ways.
# REF: https://github.com/elastic/elasticsearch-docker/issues/171
RUN ln -sf /etc/pki/ca-trust/extracted/java/cacerts /usr/share/fortishield-indexer/jdk/lib/security/cacerts

ENV PATH /usr/share/fortishield-indexer/bin:\$PATH

COPY bin/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

# The JDK's directories' permissions don't allow `java` to be executed under a different
# group to the default. Fix this.
RUN find /usr/share/fortishield-indexer/jdk -type d -exec chmod 0755 '{}' \\; && \\
    chmod g=u /etc/passwd && \\
    chmod 0775 /usr/local/bin/docker-entrypoint.sh

# Ensure that there are no files with setuid or setgid, in order to mitigate "stackclash" attacks.
RUN find / -xdev -perm -4000 -exec chmod ug-s {} +

EXPOSE 9200 9300

LABEL org.label-schema.build-date="${build_date}" \\
  org.label-schema.license="${license}" \\
  org.label-schema.name="OpenSearch" \\
  org.label-schema.schema-version="1.0" \\
  org.label-schema.url="https://www.opensearch.org" \\
  org.label-schema.usage="https://www.opensearch.org/guide/en/opensearch/reference/index.html" \\
  org.label-schema.vcs-ref="${git_revision}" \\
  org.label-schema.vcs-url="https://github.com/opensearch-project/OpenSearch" \\
  org.label-schema.vendor="OpenSearch" \\
  org.label-schema.version="${version}" \\
  org.opencontainers.image.created="${build_date}" \\
  org.opencontainers.image.documentation="https://www.opensearch.org/guide/en/opensearch/reference/index.html" \\
  org.opencontainers.image.licenses="${license}" \\
  org.opencontainers.image.revision="${git_revision}" \\
  org.opencontainers.image.source="https://github.com/opensearch-project/OpenSearch" \\
  org.opencontainers.image.title="OpenSearch" \\
  org.opencontainers.image.url="https://www.opensearch.org" \\
  org.opencontainers.image.vendor="OpenSearch" \\
  org.opencontainers.image.version="${version}"

ENTRYPOINT ["/tini", "--", "/usr/local/bin/docker-entrypoint.sh"]
# Dummy overridable parameter parsed by entrypoint
CMD ["opensearchwrapper"]

################################################################################
# End of multi-stage Dockerfile
################################################################################
