/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.action.termvectors;

import org.opensearch.LegacyESVersion;
import org.opensearch.OpenSearchParseException;
import org.opensearch.Version;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.RealtimeRequest;
import org.opensearch.action.ValidateActions;
import org.opensearch.action.get.MultiGetRequest;
import org.opensearch.action.support.single.shard.SingleShardRequest;
import org.opensearch.common.Nullable;
import org.opensearch.common.lucene.uid.Versions;
import org.opensearch.common.util.set.Sets;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.ParseField;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.VersionType;
import org.opensearch.index.mapper.MapperService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Request returning the term vector (doc frequency, positions, offsets) for a
 * document.
 * <p>
 * Note, the {@link #index()}, and {@link #id(String)} are
 * required.
 *
 * @opensearch.internal
 */
public class TermVectorsRequest extends SingleShardRequest<TermVectorsRequest> implements RealtimeRequest {
    private static final ParseField INDEX = new ParseField("_index");
    private static final ParseField ID = new ParseField("_id");
    private static final ParseField ROUTING = new ParseField("routing");
    private static final ParseField VERSION = new ParseField("version");
    private static final ParseField VERSION_TYPE = new ParseField("version_type");
    private static final ParseField FIELDS = new ParseField("fields");
    private static final ParseField OFFSETS = new ParseField("offsets");
    private static final ParseField POSITIONS = new ParseField("positions");
    private static final ParseField PAYLOADS = new ParseField("payloads");
    private static final ParseField DFS = new ParseField("dfs");
    private static final ParseField FILTER = new ParseField("filter");
    private static final ParseField DOC = new ParseField("doc");

    private String id;

    private BytesReference doc;

    private MediaType mediaType;

    private String routing;

    private VersionType versionType = VersionType.INTERNAL;

    private long version = Versions.MATCH_ANY;

    protected String preference;

    private static final AtomicInteger randomInt = new AtomicInteger(0);

    // TODO: change to String[]
    private Set<String> selectedFields;

    private boolean realtime = true;

    private Map<String, String> perFieldAnalyzer;

    private FilterSettings filterSettings;

    /**
     * Internal filter settings
     *
     * @opensearch.internal
     */
    public static final class FilterSettings {
        public Integer maxNumTerms;
        public Integer minTermFreq;
        public Integer maxTermFreq;
        public Integer minDocFreq;
        public Integer maxDocFreq;
        public Integer minWordLength;
        public Integer maxWordLength;

        public FilterSettings() {

        }

        public FilterSettings(
            @Nullable Integer maxNumTerms,
            @Nullable Integer minTermFreq,
            @Nullable Integer maxTermFreq,
            @Nullable Integer minDocFreq,
            @Nullable Integer maxDocFreq,
            @Nullable Integer minWordLength,
            @Nullable Integer maxWordLength
        ) {
            this.maxNumTerms = maxNumTerms;
            this.minTermFreq = minTermFreq;
            this.maxTermFreq = maxTermFreq;
            this.minDocFreq = minDocFreq;
            this.maxDocFreq = maxDocFreq;
            this.minWordLength = minWordLength;
            this.maxWordLength = maxWordLength;
        }

        public void readFrom(StreamInput in) throws IOException {
            maxNumTerms = in.readOptionalVInt();
            minTermFreq = in.readOptionalVInt();
            maxTermFreq = in.readOptionalVInt();
            minDocFreq = in.readOptionalVInt();
            maxDocFreq = in.readOptionalVInt();
            minWordLength = in.readOptionalVInt();
            maxWordLength = in.readOptionalVInt();
        }

        public void writeTo(StreamOutput out) throws IOException {
            out.writeOptionalVInt(maxNumTerms);
            out.writeOptionalVInt(minTermFreq);
            out.writeOptionalVInt(maxTermFreq);
            out.writeOptionalVInt(minDocFreq);
            out.writeOptionalVInt(maxDocFreq);
            out.writeOptionalVInt(minWordLength);
            out.writeOptionalVInt(maxWordLength);
        }
    }

    private EnumSet<Flag> flagsEnum = EnumSet.of(Flag.Positions, Flag.Offsets, Flag.Payloads, Flag.FieldStatistics);

    public TermVectorsRequest() {}

    TermVectorsRequest(StreamInput in) throws IOException {
        super(in);
        if (in.getVersion().before(Version.V_2_0_0)) {
            // types no longer supported; ignore for BWC
            in.readString();
        }
        id = in.readString();

        if (in.readBoolean()) {
            doc = in.readBytesReference();
            if (in.getVersion().onOrAfter(Version.V_2_10_0)) {
                mediaType = in.readMediaType();
            } else {
                mediaType = in.readEnum(XContentType.class);
            }
        }
        routing = in.readOptionalString();

        if (in.getVersion().before(LegacyESVersion.V_7_0_0)) {
            in.readOptionalString(); // _parent
        }
        preference = in.readOptionalString();
        long flags = in.readVLong();

        flagsEnum.clear();
        for (Flag flag : Flag.values()) {
            if ((flags & (1 << flag.ordinal())) != 0) {
                flagsEnum.add(flag);
            }
        }
        int numSelectedFields = in.readVInt();
        if (numSelectedFields > 0) {
            selectedFields = new HashSet<>();
            for (int i = 0; i < numSelectedFields; i++) {
                selectedFields.add(in.readString());
            }
        }
        if (in.readBoolean()) {
            perFieldAnalyzer = readPerFieldAnalyzer(in.readMap());
        }
        if (in.readBoolean()) {
            filterSettings = new FilterSettings();
            filterSettings.readFrom(in);
        }
        realtime = in.readBoolean();
        versionType = VersionType.fromValue(in.readByte());
        version = in.readLong();
    }

    /**
     * Constructs a new term vector request for a document that will be fetch
     * from the provided index. Use {@link #id(String)} to specify the document to load.
     */
    public TermVectorsRequest(String index, String id) {
        super(index);
        this.id = id;
    }

    /**
     * Constructs a new term vector request for a document that will be fetch
     * from the provided index. Use {@link #id(String)} to specify the document to load.
     */
    public TermVectorsRequest(TermVectorsRequest other) {
        super(other.index());
        this.id = other.id();
        if (other.doc != null) {
            this.doc = new BytesArray(other.doc().toBytesRef(), true);
            this.mediaType = other.mediaType;
        }
        this.flagsEnum = other.getFlags().clone();
        this.preference = other.preference();
        this.routing = other.routing();
        if (other.selectedFields != null) {
            this.selectedFields = new HashSet<>(other.selectedFields);
        }
        if (other.perFieldAnalyzer != null) {
            this.perFieldAnalyzer = new HashMap<>(other.perFieldAnalyzer);
        }
        this.realtime = other.realtime();
        this.version = other.version();
        this.versionType = VersionType.fromValue(other.versionType().getValue());
        this.filterSettings = other.filterSettings();
    }

    public TermVectorsRequest(MultiGetRequest.Item item) {
        super(item.index());
        this.id = item.id();
        this.selectedFields(item.storedFields());
        this.routing(item.routing());
    }

    public EnumSet<Flag> getFlags() {
        return flagsEnum;
    }

    /**
     * Returns the id of document the term vector is requested for.
     */
    public String id() {
        return id;
    }

    /**
     * Sets the id of document the term vector is requested for.
     */
    public TermVectorsRequest id(String id) {
        this.id = id;
        return this;
    }

    /**
     * Returns the artificial document from which term vectors are requested for.
     */
    public BytesReference doc() {
        return doc;
    }

    public MediaType xContentType() {
        return mediaType;
    }

    /**
     * Sets an artificial document from which term vectors are requested for.
     */
    public TermVectorsRequest doc(XContentBuilder documentBuilder) {
        return this.doc(BytesReference.bytes(documentBuilder), true, documentBuilder.contentType());
    }

    /**
     * Sets an artificial document from which term vectors are requested for.
     * @deprecated use {@link #doc(BytesReference, boolean, MediaType)} to avoid content auto detection
     */
    @Deprecated
    public TermVectorsRequest doc(BytesReference doc, boolean generateRandomId) {
        return this.doc(doc, generateRandomId, MediaTypeRegistry.xContentType(doc));
    }

    /**
     * Sets an artificial document from which term vectors are requested for.
     */
    public TermVectorsRequest doc(BytesReference doc, boolean generateRandomId, MediaType mediaType) {
        // assign a random id to this artificial document, for routing
        if (generateRandomId) {
            this.id(String.valueOf(randomInt.getAndAdd(1)));
        }
        this.doc = doc;
        this.mediaType = mediaType;
        return this;
    }

    /**
     * @return The routing for this request.
     */
    public String routing() {
        return routing;
    }

    public TermVectorsRequest routing(String routing) {
        this.routing = routing;
        return this;
    }

    public String preference() {
        return this.preference;
    }

    /**
     * Sets the preference to execute the search. Defaults to randomize across
     * shards. Can be set to {@code _local} to prefer local shards, {@code _primary} to execute only on primary shards,
     * or a custom value, which guarantees that the same order will be used across different
     * requests.
     */
    public TermVectorsRequest preference(String preference) {
        this.preference = preference;
        return this;
    }

    /**
     * Return the start and stop offsets for each term if they were stored or
     * skip offsets.
     */
    public TermVectorsRequest offsets(boolean offsets) {
        setFlag(Flag.Offsets, offsets);
        return this;
    }

    /**
     * @return <code>true</code> if term offsets should be returned. Otherwise
     * <code>false</code>
     */
    public boolean offsets() {
        return flagsEnum.contains(Flag.Offsets);
    }

    /**
     * Return the positions for each term if stored or skip.
     */
    public TermVectorsRequest positions(boolean positions) {
        setFlag(Flag.Positions, positions);
        return this;
    }

    /**
     * @return Returns if the positions for each term should be returned if
     *         stored or skip.
     */
    public boolean positions() {
        return flagsEnum.contains(Flag.Positions);
    }

    /**
     * @return <code>true</code> if term payloads should be returned. Otherwise
     * <code>false</code>
     */
    public boolean payloads() {
        return flagsEnum.contains(Flag.Payloads);
    }

    /**
     * Return the payloads for each term or skip.
     */
    public TermVectorsRequest payloads(boolean payloads) {
        setFlag(Flag.Payloads, payloads);
        return this;
    }

    /**
     * @return <code>true</code> if term statistics should be returned.
     * Otherwise <code>false</code>
     */
    public boolean termStatistics() {
        return flagsEnum.contains(Flag.TermStatistics);
    }

    /**
     * Return the term statistics for each term in the shard or skip.
     */
    public TermVectorsRequest termStatistics(boolean termStatistics) {
        setFlag(Flag.TermStatistics, termStatistics);
        return this;
    }

    /**
     * @return <code>true</code> if field statistics should be returned.
     * Otherwise <code>false</code>
     */
    public boolean fieldStatistics() {
        return flagsEnum.contains(Flag.FieldStatistics);
    }

    /**
     * Return the field statistics for each term in the shard or skip.
     */
    public TermVectorsRequest fieldStatistics(boolean fieldStatistics) {
        setFlag(Flag.FieldStatistics, fieldStatistics);
        return this;
    }

    /**
     * Return only term vectors for special selected fields. Returns for term
     * vectors for all fields if selectedFields == null
     */
    public Set<String> selectedFields() {
        return selectedFields;
    }

    /**
     * Return only term vectors for special selected fields. Returns the term
     * vectors for all fields if selectedFields == null
     */
    public TermVectorsRequest selectedFields(String... fields) {
        selectedFields = fields != null && fields.length != 0 ? Sets.newHashSet(fields) : null;
        return this;
    }

    /**
     * Return whether term vectors should be generated real-time (default to true).
     */
    public boolean realtime() {
        return this.realtime;
    }

    @Override
    public TermVectorsRequest realtime(boolean realtime) {
        this.realtime = realtime;
        return this;
    }

    /**
     * Return the overridden analyzers at each field.
     */
    public Map<String, String> perFieldAnalyzer() {
        return perFieldAnalyzer;
    }

    /**
     * Override the analyzer used at each field when generating term vectors.
     */
    public TermVectorsRequest perFieldAnalyzer(Map<String, String> perFieldAnalyzer) {
        this.perFieldAnalyzer = perFieldAnalyzer != null && perFieldAnalyzer.size() != 0 ? new HashMap<>(perFieldAnalyzer) : null;
        return this;
    }

    /**
     * Return the settings for filtering out terms.
     */
    public FilterSettings filterSettings() {
        return this.filterSettings;
    }

    /**
     * Sets the settings for filtering out terms.
     */
    public TermVectorsRequest filterSettings(FilterSettings settings) {
        this.filterSettings = settings;
        return this;
    }

    public long version() {
        return version;
    }

    public TermVectorsRequest version(long version) {
        this.version = version;
        return this;
    }

    public VersionType versionType() {
        return versionType;
    }

    public TermVectorsRequest versionType(VersionType versionType) {
        this.versionType = versionType;
        return this;
    }

    private void setFlag(Flag flag, boolean set) {
        if (set && !flagsEnum.contains(flag)) {
            flagsEnum.add(flag);
        } else if (!set) {
            flagsEnum.remove(flag);
            assert (!flagsEnum.contains(flag));
        }
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = super.validateNonNullIndex();
        if (id == null && doc == null) {
            validationException = ValidateActions.addValidationError("id or doc is missing", validationException);
        }
        return validationException;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (out.getVersion().before(Version.V_2_0_0)) {
            // types no longer supported; send "_doc" for bwc
            out.writeString(MapperService.SINGLE_MAPPING_NAME);
        }
        out.writeString(id);

        out.writeBoolean(doc != null);
        if (doc != null) {
            out.writeBytesReference(doc);
            if (out.getVersion().onOrAfter(Version.V_2_10_0)) {
                mediaType.writeTo(out);
            } else {
                out.writeEnum((XContentType) mediaType);
            }
        }
        out.writeOptionalString(routing);
        if (out.getVersion().before(LegacyESVersion.V_7_0_0)) {
            out.writeOptionalString(null); // _parent
        }
        out.writeOptionalString(preference);
        long longFlags = 0;
        for (Flag flag : flagsEnum) {
            longFlags |= (1 << flag.ordinal());
        }
        out.writeVLong(longFlags);
        if (selectedFields != null) {
            out.writeStringCollection(selectedFields);
        } else {
            out.writeVInt(0);
        }
        out.writeBoolean(perFieldAnalyzer != null);
        if (perFieldAnalyzer != null) {
            out.writeGenericValue(perFieldAnalyzer);
        }
        out.writeBoolean(filterSettings != null);
        if (filterSettings != null) {
            filterSettings.writeTo(out);
        }
        out.writeBoolean(realtime);
        out.writeByte(versionType.getValue());
        out.writeLong(version);
    }

    /**
     * The flags.
     *
     * @opensearch.internal
     */
    public enum Flag {
        // Do not change the order of these flags we use
        // the ordinal for encoding! Only append to the end!
        Positions,
        Offsets,
        Payloads,
        FieldStatistics,
        TermStatistics
    }

    /**
     * populates a request object (pre-populated with defaults) based on a parser.
     */
    public static void parseRequest(TermVectorsRequest termVectorsRequest, XContentParser parser) throws IOException {
        XContentParser.Token token;
        String currentFieldName = null;
        List<String> fields = new ArrayList<>();
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (currentFieldName != null) {
                if (FIELDS.match(currentFieldName, parser.getDeprecationHandler())) {
                    if (token == XContentParser.Token.START_ARRAY) {
                        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                            fields.add(parser.text());
                        }
                    } else {
                        throw new OpenSearchParseException("failed to parse term vectors request. field [fields] must be an array");
                    }
                } else if (OFFSETS.match(currentFieldName, parser.getDeprecationHandler())) {
                    termVectorsRequest.offsets(parser.booleanValue());
                } else if (POSITIONS.match(currentFieldName, parser.getDeprecationHandler())) {
                    termVectorsRequest.positions(parser.booleanValue());
                } else if (PAYLOADS.match(currentFieldName, parser.getDeprecationHandler())) {
                    termVectorsRequest.payloads(parser.booleanValue());
                } else if (currentFieldName.equals("term_statistics") || currentFieldName.equals("termStatistics")) {
                    termVectorsRequest.termStatistics(parser.booleanValue());
                } else if (currentFieldName.equals("field_statistics") || currentFieldName.equals("fieldStatistics")) {
                    termVectorsRequest.fieldStatistics(parser.booleanValue());
                } else if (DFS.match(currentFieldName, parser.getDeprecationHandler())) {
                    throw new IllegalArgumentException("distributed frequencies is not supported anymore for term vectors");
                } else if (currentFieldName.equals("per_field_analyzer") || currentFieldName.equals("perFieldAnalyzer")) {
                    termVectorsRequest.perFieldAnalyzer(readPerFieldAnalyzer(parser.map()));
                } else if (FILTER.match(currentFieldName, parser.getDeprecationHandler())) {
                    termVectorsRequest.filterSettings(readFilterSettings(parser));
                } else if (INDEX.match(currentFieldName, parser.getDeprecationHandler())) {
                    // the following is important for multi request parsing.
                    termVectorsRequest.index = parser.text();
                } else if (ID.match(currentFieldName, parser.getDeprecationHandler())) {
                    if (termVectorsRequest.doc != null) {
                        throw new OpenSearchParseException(
                            "failed to parse term vectors request. " + "either [id] or [doc] can be specified, but not both!"
                        );
                    }
                    termVectorsRequest.id = parser.text();
                } else if (DOC.match(currentFieldName, parser.getDeprecationHandler())) {
                    if (termVectorsRequest.id != null) {
                        throw new OpenSearchParseException(
                            "failed to parse term vectors request. " + "either [id] or [doc] can be specified, but not both!"
                        );
                    }
                    termVectorsRequest.doc(jsonBuilder().copyCurrentStructure(parser));
                } else if (ROUTING.match(currentFieldName, parser.getDeprecationHandler())) {
                    termVectorsRequest.routing = parser.text();
                } else if (VERSION.match(currentFieldName, parser.getDeprecationHandler())) {
                    termVectorsRequest.version = parser.longValue();
                } else if (VERSION_TYPE.match(currentFieldName, parser.getDeprecationHandler())) {
                    termVectorsRequest.versionType = VersionType.fromString(parser.text());
                } else {
                    throw new OpenSearchParseException("failed to parse term vectors request. unknown field [{}]", currentFieldName);
                }
            }
        }
        if (fields.size() > 0) {
            String[] fieldsAsArray = new String[fields.size()];
            termVectorsRequest.selectedFields(fields.toArray(fieldsAsArray));
        }
    }

    public static Map<String, String> readPerFieldAnalyzer(Map<String, Object> map) {
        Map<String, String> mapStrStr = new HashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getValue() instanceof String) {
                mapStrStr.put(e.getKey(), (String) e.getValue());
            } else {
                throw new OpenSearchParseException(
                    "expecting the analyzer at [{}] to be a String, but found [{}] instead",
                    e.getKey(),
                    e.getValue().getClass()
                );
            }
        }
        return mapStrStr;
    }

    private static FilterSettings readFilterSettings(XContentParser parser) throws IOException {
        FilterSettings settings = new FilterSettings();
        XContentParser.Token token;
        String currentFieldName = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (currentFieldName != null) {
                if (currentFieldName.equals("max_num_terms")) {
                    settings.maxNumTerms = parser.intValue();
                } else if (currentFieldName.equals("min_term_freq")) {
                    settings.minTermFreq = parser.intValue();
                } else if (currentFieldName.equals("max_term_freq")) {
                    settings.maxTermFreq = parser.intValue();
                } else if (currentFieldName.equals("min_doc_freq")) {
                    settings.minDocFreq = parser.intValue();
                } else if (currentFieldName.equals("max_doc_freq")) {
                    settings.maxDocFreq = parser.intValue();
                } else if (currentFieldName.equals("min_word_length")) {
                    settings.minWordLength = parser.intValue();
                } else if (currentFieldName.equals("max_word_length")) {
                    settings.maxWordLength = parser.intValue();
                } else {
                    throw new OpenSearchParseException(
                        "failed to parse term vectors request. "
                            + "the field [{}] is not valid for filter parameter for term vector request",
                        currentFieldName
                    );
                }
            }
        }
        return settings;
    }
}
