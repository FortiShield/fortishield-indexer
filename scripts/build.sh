#!/bin/bash

# Copyright OpenSearch Contributors
# SPDX-License-Identifier: Apache-2.0
#
# The OpenSearch Contributors require contributions made to
# this file be licensed under the Apache-2.0 license or a
# compatible open source license.

set -ex

function usage() {
    echo "Usage: $0 [args]"
    echo ""
    echo "Arguments:"
    echo -e "-v VERSION\t[Required] OpenSearch version."
    echo -e "-q QUALIFIER\t[Optional] Version qualifier."
    echo -e "-s SNAPSHOT\t[Optional] Build a snapshot, default is 'false'."
    echo -e "-p PLATFORM\t[Optional] Platform, default is 'uname -s'."
    echo -e "-a ARCHITECTURE\t[Optional] Build architecture, default is 'uname -m'."
    echo -e "-d DISTRIBUTION\t[Optional] Distribution, default is 'tar'."
    echo -e "-d REVISION\t[Optional] Package revision, default is '1'."
    echo -e "-o OUTPUT\t[Optional] Output path, default is 'artifacts'."
    echo -e "-h help"
}

while getopts ":h:v:q:s:o:p:a:d:r:b:" arg; do
    case $arg in
    h)
        usage
        exit 1
        ;;
    v)
        VERSION=$OPTARG
        ;;
    q)
        QUALIFIER=$OPTARG
        ;;
    s)
        SNAPSHOT=$OPTARG
        ;;
    o)
        OUTPUT=$OPTARG
        ;;
    p)
        PLATFORM=$OPTARG
        ;;
    a)
        ARCHITECTURE=$OPTARG
        ;;
    d)
        DISTRIBUTION=$OPTARG
        ;;
    r)
        REVISION=$OPTARG
        ;;
    b)
        BRANCH=$OPTARG
        ;;
    :)
        echo "Error: -${OPTARG} requires an argument"
        usage
        exit 1
        ;;
    ?)
        echo "Invalid option: -${arg}"
        exit 1
        ;;
    esac
done

if [ -z "$VERSION" ]; then
    echo "Error: You must specify the OpenSearch version"
    usage
    exit 1
fi

[ -z "$OUTPUT" ] && OUTPUT=artifacts

echo "Creating output directory $OUTPUT/maven/org/opensearch if it doesn't already exist"
mkdir -p "$OUTPUT/maven/org/opensearch"

# Build project and publish to maven local.
echo "Building and publishing OpenSearch project to Maven Local"
./gradlew publishToMavenLocal -Dbuild.snapshot="$SNAPSHOT" -Dbuild.version_qualifier="$QUALIFIER"

# Publish to existing test repo, using this to stage release versions of the artifacts that can be released from the same build.
echo "Publishing OpenSearch to Test Repository"
./gradlew publishNebulaPublicationToTestRepository -Dbuild.snapshot="$SNAPSHOT" -Dbuild.version_qualifier="$QUALIFIER"

# Copy maven publications to be promoted
echo "Copying Maven publications to $OUTPUT/maven/org"
cp -r ./build/local-test-repo/org/opensearch "${OUTPUT}"/maven/org

# Assemble distribution artifact
# see https://github.com/opensearch-project/OpenSearch/blob/main/settings.gradle#L34 for other distribution targets

[ -z "$PLATFORM" ] && PLATFORM=$(uname -s | awk '{print tolower($0)}')
[ -z "$ARCHITECTURE" ] && ARCHITECTURE=$(uname -m)
[ -z "$DISTRIBUTION" ] && DISTRIBUTION="tar"
[ -z "$REVISION" ] && REVISION="1"
[ -z "$BRANCH" ] && BRANCH="master"

# ====
# Function to download the alerts template
# ====
function download_template() {
    echo "Downloading fortishield-template.json"
    local download_url="https://raw.githubusercontent.com/fortishield/fortishield/${BRANCH}/extensions/elasticsearch/7.x/fortishield-template.json"

    if ! curl -s "${download_url}" -o distribution/src/config/fortishield-template.json; then
        echo "Unable to download fortishield-template.json"
        return 1
    fi

    echo "Successfully downloaded fortishield-template.json"
    return 0
}

case $PLATFORM-$DISTRIBUTION-$ARCHITECTURE in
    linux-tar-x64 | darwin-tar-x64)
        PACKAGE="tar"
        EXT="tar.gz"
        TYPE="archives"
        TARGET="$PLATFORM-$PACKAGE"
        SUFFIX="$PLATFORM-x64"
        ;;
    linux-tar-arm64 | darwin-tar-arm64)
        PACKAGE="tar"
        EXT="tar.gz"
        TYPE="archives"
        TARGET="$PLATFORM-arm64-$PACKAGE"
        SUFFIX="$PLATFORM-arm64"
        ;;
    linux-deb-x64)
        PACKAGE="deb"
        EXT="deb"
        TYPE="packages"
        TARGET="deb"
        SUFFIX="amd64"
        ;;
    linux-deb-arm64)
        PACKAGE="deb"
        EXT="deb"
        TYPE="packages"
        TARGET="arm64-deb"
        SUFFIX="arm64"
        ;;
    linux-rpm-x64)
        PACKAGE="rpm"
        EXT="rpm"
        TYPE="packages"
        TARGET="rpm"
        SUFFIX="x86_64"
        ;;
    linux-rpm-arm64)
        PACKAGE="rpm"
        EXT="rpm"
        TYPE="packages"
        TARGET="arm64-rpm"
        SUFFIX="aarch64"
        ;;
    windows-zip-x64)
        PACKAGE="zip"
        EXT="zip"
        TYPE="archives"
        TARGET="$PLATFORM-$PACKAGE"
        SUFFIX="$PLATFORM-x64"
        ;;
    windows-zip-arm64)
        PACKAGE="zip"
        EXT="zip"
        TYPE="archives"
        TARGET="$PLATFORM-arm64-$PACKAGE"
        SUFFIX="$PLATFORM-arm64"
        ;;
    *)
        echo "Unsupported platform-distribution-architecture combination: $PLATFORM-$DISTRIBUTION-$ARCHITECTURE"
        exit 1
        ;;
esac

echo "Building OpenSearch for $PLATFORM-$DISTRIBUTION-$ARCHITECTURE"

if ! download_template; then
    exit 1
fi

./gradlew ":distribution:$TYPE:$TARGET:assemble" -Dbuild.snapshot="$SNAPSHOT" -Dbuild.version_qualifier="$QUALIFIER"

# Copy artifact to dist folder in bundle build output
echo "Copying artifact to ${OUTPUT}/dist"

ARTIFACT_BUILD_NAME=$(ls "distribution/$TYPE/$TARGET/build/distributions/" | grep "fortishield-indexer-min.*$SUFFIX.$EXT")
GIT_COMMIT=$(git rev-parse --short HEAD)
WI_VERSION=$(<VERSION)
ARTIFACT_PACKAGE_NAME=fortishield-indexer-min_"$WI_VERSION"-"$REVISION"_"$SUFFIX"_"$GIT_COMMIT"."$EXT"

# Used by the GH workflow to upload the artifact
echo "$ARTIFACT_PACKAGE_NAME" >"$OUTPUT/artifact_min_name.txt"

mkdir -p "${OUTPUT}/dist"
cp "distribution/$TYPE/$TARGET/build/distributions/$ARTIFACT_BUILD_NAME" "${OUTPUT}/dist/$ARTIFACT_PACKAGE_NAME"
