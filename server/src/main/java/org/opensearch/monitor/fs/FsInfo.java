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
 *    http://www.apache.org/licenses/LICENSE-2.0
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

package org.opensearch.monitor.fs;

import org.opensearch.LegacyESVersion;
import org.opensearch.Version;
import org.opensearch.cluster.DiskUsage;
import org.opensearch.common.Nullable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.common.unit.ByteSizeValue;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * FileSystem information
 *
 * @opensearch.internal
 */
public class FsInfo implements Iterable<FsInfo.Path>, Writeable, ToXContentFragment {

    /**
     * Path for the file system
     *
     * @opensearch.internal
     */
    public static class Path implements Writeable, ToXContentObject {

        String path;
        @Nullable
        String mount;
        /** File system type from {@code java.nio.file.FileStore type()}, if available. */
        @Nullable
        String type;
        long total = -1;
        long free = -1;
        long available = -1;
        long fileCacheReserved = -1;
        long fileCacheUtilized = 0;

        public Path() {}

        public Path(String path, @Nullable String mount, long total, long free, long available) {
            this.path = path;
            this.mount = mount;
            this.total = total;
            this.free = free;
            this.available = available;
        }

        /**
         * Read from a stream.
         */
        public Path(StreamInput in) throws IOException {
            path = in.readOptionalString();
            mount = in.readOptionalString();
            type = in.readOptionalString();
            total = in.readLong();
            free = in.readLong();
            available = in.readLong();
            if (in.getVersion().onOrAfter(Version.V_2_7_0)) {
                fileCacheReserved = in.readLong();
                fileCacheUtilized = in.readLong();
            }
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeOptionalString(path); // total aggregates do not have a path
            out.writeOptionalString(mount);
            out.writeOptionalString(type);
            out.writeLong(total);
            out.writeLong(free);
            out.writeLong(available);
            if (out.getVersion().onOrAfter(Version.V_2_7_0)) {
                out.writeLong(fileCacheReserved);
                out.writeLong(fileCacheUtilized);
            }
        }

        public String getPath() {
            return path;
        }

        public String getMount() {
            return mount;
        }

        public String getType() {
            return type;
        }

        public ByteSizeValue getTotal() {
            return new ByteSizeValue(total);
        }

        public ByteSizeValue getFree() {
            return new ByteSizeValue(free);
        }

        public ByteSizeValue getAvailable() {
            return new ByteSizeValue(available);
        }

        public ByteSizeValue getFileCacheReserved() {
            return new ByteSizeValue(fileCacheReserved);
        }

        public ByteSizeValue getFileCacheUtilized() {
            return new ByteSizeValue(fileCacheUtilized);
        }

        private long addLong(long current, long other) {
            if (current == -1 && other == -1) {
                return 0;
            }
            if (other == -1) {
                return current;
            }
            if (current == -1) {
                return other;
            }
            return current + other;
        }

        public void add(Path path) {
            total = FsProbe.adjustForHugeFilesystems(addLong(total, path.total));
            free = FsProbe.adjustForHugeFilesystems(addLong(free, path.free));
            fileCacheReserved = FsProbe.adjustForHugeFilesystems(addLong(fileCacheReserved, path.fileCacheReserved));
            fileCacheUtilized = FsProbe.adjustForHugeFilesystems(addLong(fileCacheUtilized, path.fileCacheUtilized));
            available = FsProbe.adjustForHugeFilesystems(addLong(available, path.available));
        }

        static final class Fields {
            static final String PATH = "path";
            static final String MOUNT = "mount";
            static final String TYPE = "type";
            static final String TOTAL = "total";
            static final String TOTAL_IN_BYTES = "total_in_bytes";
            static final String FREE = "free";
            static final String FREE_IN_BYTES = "free_in_bytes";
            static final String AVAILABLE = "available";
            static final String AVAILABLE_IN_BYTES = "available_in_bytes";
            static final String CACHE_RESERVED = "cache_reserved";
            static final String CACHE_RESERVED_IN_BYTES = "cache_reserved_in_bytes";
            static final String CACHE_UTILIZED = "cache_utilized";
            static final String CACHE_UTILIZED_IN_BYTES = "cache_utilized_in_bytes";
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            if (path != null) {
                builder.field(Fields.PATH, path);
            }
            if (mount != null) {
                builder.field(Fields.MOUNT, mount);
            }
            if (type != null) {
                builder.field(Fields.TYPE, type);
            }

            if (total != -1) {
                builder.humanReadableField(Fields.TOTAL_IN_BYTES, Fields.TOTAL, getTotal());
            }
            if (free != -1) {
                builder.humanReadableField(Fields.FREE_IN_BYTES, Fields.FREE, getFree());
            }
            if (available != -1) {
                builder.humanReadableField(Fields.AVAILABLE_IN_BYTES, Fields.AVAILABLE, getAvailable());
            }
            if (fileCacheReserved != -1) {
                builder.humanReadableField(Fields.CACHE_RESERVED_IN_BYTES, Fields.CACHE_RESERVED, getFileCacheReserved());
            }
            if (fileCacheReserved != 0) {
                builder.humanReadableField(Fields.CACHE_UTILIZED, Fields.CACHE_UTILIZED_IN_BYTES, getFileCacheUtilized());
            }

            builder.endObject();
            return builder;
        }
    }

    /**
     * The device status.
     *
     * @opensearch.internal
     */
    public static class DeviceStats implements Writeable, ToXContentFragment {

        final int majorDeviceNumber;
        final int minorDeviceNumber;
        final String deviceName;
        final long currentReadsCompleted;
        final long previousReadsCompleted;
        final long currentSectorsRead;
        final long previousSectorsRead;
        final long currentWritesCompleted;
        final long previousWritesCompleted;
        final long currentSectorsWritten;
        final long previousSectorsWritten;

        public DeviceStats(
            final int majorDeviceNumber,
            final int minorDeviceNumber,
            final String deviceName,
            final long currentReadsCompleted,
            final long currentSectorsRead,
            final long currentWritesCompleted,
            final long currentSectorsWritten,
            final DeviceStats previousDeviceStats
        ) {
            this(
                majorDeviceNumber,
                minorDeviceNumber,
                deviceName,
                currentReadsCompleted,
                previousDeviceStats != null ? previousDeviceStats.currentReadsCompleted : -1,
                currentSectorsWritten,
                previousDeviceStats != null ? previousDeviceStats.currentSectorsWritten : -1,
                currentSectorsRead,
                previousDeviceStats != null ? previousDeviceStats.currentSectorsRead : -1,
                currentWritesCompleted,
                previousDeviceStats != null ? previousDeviceStats.currentWritesCompleted : -1
            );
        }

        private DeviceStats(
            final int majorDeviceNumber,
            final int minorDeviceNumber,
            final String deviceName,
            final long currentReadsCompleted,
            final long previousReadsCompleted,
            final long currentSectorsWritten,
            final long previousSectorsWritten,
            final long currentSectorsRead,
            final long previousSectorsRead,
            final long currentWritesCompleted,
            final long previousWritesCompleted
        ) {
            this.majorDeviceNumber = majorDeviceNumber;
            this.minorDeviceNumber = minorDeviceNumber;
            this.deviceName = deviceName;
            this.currentReadsCompleted = currentReadsCompleted;
            this.previousReadsCompleted = previousReadsCompleted;
            this.currentWritesCompleted = currentWritesCompleted;
            this.previousWritesCompleted = previousWritesCompleted;
            this.currentSectorsRead = currentSectorsRead;
            this.previousSectorsRead = previousSectorsRead;
            this.currentSectorsWritten = currentSectorsWritten;
            this.previousSectorsWritten = previousSectorsWritten;
        }

        public DeviceStats(StreamInput in) throws IOException {
            majorDeviceNumber = in.readVInt();
            minorDeviceNumber = in.readVInt();
            deviceName = in.readString();
            currentReadsCompleted = in.readLong();
            previousReadsCompleted = in.readLong();
            currentWritesCompleted = in.readLong();
            previousWritesCompleted = in.readLong();
            currentSectorsRead = in.readLong();
            previousSectorsRead = in.readLong();
            currentSectorsWritten = in.readLong();
            previousSectorsWritten = in.readLong();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(majorDeviceNumber);
            out.writeVInt(minorDeviceNumber);
            out.writeString(deviceName);
            out.writeLong(currentReadsCompleted);
            out.writeLong(previousReadsCompleted);
            out.writeLong(currentWritesCompleted);
            out.writeLong(previousWritesCompleted);
            out.writeLong(currentSectorsRead);
            out.writeLong(previousSectorsRead);
            out.writeLong(currentSectorsWritten);
            out.writeLong(previousSectorsWritten);
        }

        public long operations() {
            if (previousReadsCompleted == -1 || previousWritesCompleted == -1) return -1;

            return (currentReadsCompleted - previousReadsCompleted) + (currentWritesCompleted - previousWritesCompleted);
        }

        public long readOperations() {
            if (previousReadsCompleted == -1) return -1;

            return (currentReadsCompleted - previousReadsCompleted);
        }

        public long writeOperations() {
            if (previousWritesCompleted == -1) return -1;

            return (currentWritesCompleted - previousWritesCompleted);
        }

        public long readKilobytes() {
            if (previousSectorsRead == -1) return -1;

            return (currentSectorsRead - previousSectorsRead) / 2;
        }

        public long writeKilobytes() {
            if (previousSectorsWritten == -1) return -1;

            return (currentSectorsWritten - previousSectorsWritten) / 2;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.field("device_name", deviceName);
            builder.field(IoStats.OPERATIONS, operations());
            builder.field(IoStats.READ_OPERATIONS, readOperations());
            builder.field(IoStats.WRITE_OPERATIONS, writeOperations());
            builder.field(IoStats.READ_KILOBYTES, readKilobytes());
            builder.field(IoStats.WRITE_KILOBYTES, writeKilobytes());
            return builder;
        }

    }

    /**
     * The I/O statistics.
     *
     * @opensearch.internal
     */
    public static class IoStats implements Writeable, ToXContentFragment {

        private static final String OPERATIONS = "operations";
        private static final String READ_OPERATIONS = "read_operations";
        private static final String WRITE_OPERATIONS = "write_operations";
        private static final String READ_KILOBYTES = "read_kilobytes";
        private static final String WRITE_KILOBYTES = "write_kilobytes";

        final DeviceStats[] devicesStats;
        final long totalOperations;
        final long totalReadOperations;
        final long totalWriteOperations;
        final long totalReadKilobytes;
        final long totalWriteKilobytes;

        public IoStats(final DeviceStats[] devicesStats) {
            this.devicesStats = devicesStats;
            long totalOperations = 0;
            long totalReadOperations = 0;
            long totalWriteOperations = 0;
            long totalReadKilobytes = 0;
            long totalWriteKilobytes = 0;
            for (DeviceStats deviceStats : devicesStats) {
                totalOperations += deviceStats.operations() != -1 ? deviceStats.operations() : 0;
                totalReadOperations += deviceStats.readOperations() != -1 ? deviceStats.readOperations() : 0;
                totalWriteOperations += deviceStats.writeOperations() != -1 ? deviceStats.writeOperations() : 0;
                totalReadKilobytes += deviceStats.readKilobytes() != -1 ? deviceStats.readKilobytes() : 0;
                totalWriteKilobytes += deviceStats.writeKilobytes() != -1 ? deviceStats.writeKilobytes() : 0;
            }
            this.totalOperations = totalOperations;
            this.totalReadOperations = totalReadOperations;
            this.totalWriteOperations = totalWriteOperations;
            this.totalReadKilobytes = totalReadKilobytes;
            this.totalWriteKilobytes = totalWriteKilobytes;
        }

        public IoStats(StreamInput in) throws IOException {
            final int length = in.readVInt();
            final DeviceStats[] devicesStats = new DeviceStats[length];
            for (int i = 0; i < length; i++) {
                devicesStats[i] = new DeviceStats(in);
            }
            this.devicesStats = devicesStats;
            this.totalOperations = in.readLong();
            this.totalReadOperations = in.readLong();
            this.totalWriteOperations = in.readLong();
            this.totalReadKilobytes = in.readLong();
            this.totalWriteKilobytes = in.readLong();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(devicesStats.length);
            for (int i = 0; i < devicesStats.length; i++) {
                devicesStats[i].writeTo(out);
            }
            out.writeLong(totalOperations);
            out.writeLong(totalReadOperations);
            out.writeLong(totalWriteOperations);
            out.writeLong(totalReadKilobytes);
            out.writeLong(totalWriteKilobytes);
        }

        public DeviceStats[] getDevicesStats() {
            return devicesStats;
        }

        public long getTotalOperations() {
            return totalOperations;
        }

        public long getTotalReadOperations() {
            return totalReadOperations;
        }

        public long getTotalWriteOperations() {
            return totalWriteOperations;
        }

        public long getTotalReadKilobytes() {
            return totalReadKilobytes;
        }

        public long getTotalWriteKilobytes() {
            return totalWriteKilobytes;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            if (devicesStats.length > 0) {
                builder.startArray("devices");
                for (DeviceStats deviceStats : devicesStats) {
                    builder.startObject();
                    deviceStats.toXContent(builder, params);
                    builder.endObject();
                }
                builder.endArray();

                builder.startObject("total");
                builder.field(OPERATIONS, totalOperations);
                builder.field(READ_OPERATIONS, totalReadOperations);
                builder.field(WRITE_OPERATIONS, totalWriteOperations);
                builder.field(READ_KILOBYTES, totalReadKilobytes);
                builder.field(WRITE_KILOBYTES, totalWriteKilobytes);
                builder.endObject();
            }
            return builder;
        }

    }

    private final long timestamp;
    private final Path[] paths;
    private final IoStats ioStats;
    private final Path total;

    public FsInfo(long timestamp, IoStats ioStats, Path[] paths) {
        this.timestamp = timestamp;
        this.ioStats = ioStats;
        this.paths = paths;
        this.total = total();
    }

    /**
     * Read from a stream.
     */
    public FsInfo(StreamInput in) throws IOException {
        timestamp = in.readVLong();
        ioStats = in.readOptionalWriteable(IoStats::new);
        paths = new Path[in.readVInt()];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = new Path(in);
        }
        this.total = total();
        if (in.getVersion().before(LegacyESVersion.V_7_10_0)) {
            in.readOptionalWriteable(DiskUsage::new); // previously leastDiskEstimate
            in.readOptionalWriteable(DiskUsage::new); // previously mostDiskEstimate
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVLong(timestamp);
        out.writeOptionalWriteable(ioStats);
        out.writeVInt(paths.length);
        for (Path path : paths) {
            path.writeTo(out);
        }
        if (out.getVersion().before(LegacyESVersion.V_7_10_0)) {
            out.writeOptionalWriteable(null); // previously leastDiskEstimate
            out.writeOptionalWriteable(null); // previously mostDiskEstimate
        }
    }

    public Path getTotal() {
        return total;
    }

    private Path total() {
        Path res = new Path();
        Set<String> seenDevices = new HashSet<>(paths.length);
        for (Path subPath : paths) {
            if (subPath.path != null) {
                if (!seenDevices.add(subPath.path)) {
                    continue; // already added numbers for this device;
                }
            }
            res.add(subPath);
        }
        return res;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public IoStats getIoStats() {
        return ioStats;
    }

    @Override
    public Iterator<Path> iterator() {
        return Arrays.stream(paths).iterator();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(Fields.FS);
        builder.field(Fields.TIMESTAMP, timestamp);
        builder.field(Fields.TOTAL);
        total().toXContent(builder, params);

        builder.startArray(Fields.DATA);
        for (Path path : paths) {
            path.toXContent(builder, params);
        }
        builder.endArray();
        if (ioStats != null) {
            builder.startObject(Fields.IO_STATS);
            ioStats.toXContent(builder, params);
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }

    static final class Fields {
        static final String FS = "fs";
        static final String TIMESTAMP = "timestamp";
        static final String DATA = "data";
        static final String TOTAL = "total";
        static final String IO_STATS = "io_stats";
    }
}
