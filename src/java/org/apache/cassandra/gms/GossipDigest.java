/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.gms;

import java.io.*;

import org.apache.cassandra.db.TypeSizes;
import org.apache.cassandra.io.IVersionedSerializer;
import org.apache.cassandra.io.util.DataInputPlus;
import org.apache.cassandra.io.util.DataOutputPlus;
import org.apache.cassandra.io.util.File;
import org.apache.cassandra.locator.InetAddressAndPort;

import static org.apache.cassandra.locator.InetAddressAndPort.Serializer.inetAddressAndPortSerializer;

/**
 * Contains information about a specified list of Endpoints and the largest version
 * of the state they have generated as known by the local endpoint.
 */
public class GossipDigest implements Comparable<GossipDigest>
{
    public static final IVersionedSerializer<GossipDigest> serializer = new GossipDigestSerializer();

    final InetAddressAndPort endpoint;
    final int generation;
    final int maxVersion;

    GossipDigest(InetAddressAndPort ep, int gen, int version)
    {
        endpoint = ep;
        generation = gen;
        maxVersion = version;
    }

    InetAddressAndPort getEndpoint()
    {
        return endpoint;
    }

    int getGeneration()
    {
        return generation;
    }

    int getMaxVersion()
    {
        return maxVersion;
    }

    public int compareTo(GossipDigest gDigest)
    {
        if (generation != gDigest.generation)
            return (generation - gDigest.generation);
        return (maxVersion - gDigest.maxVersion);
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(endpoint);
        sb.append(":");
        sb.append(generation);
        sb.append(":");
        sb.append(maxVersion);
        return sb.toString();
    }
}

class GossipDigestSerializer implements IVersionedSerializer<GossipDigest>
{
    public void serialize(GossipDigest gDigest, DataOutputPlus out, int version) throws IOException
    {
        inetAddressAndPortSerializer.serialize(gDigest.endpoint, out, version);
        out.writeInt(gDigest.generation);
        out.writeInt(gDigest.maxVersion);
    }

    public GossipDigest deserialize(DataInputPlus in, int version) throws IOException
    {
        InetAddressAndPort endpoint = inetAddressAndPortSerializer.deserialize(in, version);
        int generation = in.readInt();
        int maxVersion = in.readInt();
        return new GossipDigest(endpoint, generation, maxVersion);
    }

    public long serializedSize(GossipDigest gDigest, int version)
    {
        long size = inetAddressAndPortSerializer.serializedSize(gDigest.endpoint, version);
        size += TypeSizes.sizeof(gDigest.generation);
        size += TypeSizes.sizeof(gDigest.maxVersion);
        return size;
    }
}
