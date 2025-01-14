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

package org.apache.cassandra.streaming.async;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

import com.google.common.net.InetAddresses;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.io.util.DataInputBuffer;
import org.apache.cassandra.io.util.DataInputPlus;
import org.apache.cassandra.io.util.DataOutputBuffer;
import org.apache.cassandra.io.util.DataOutputPlus;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.net.MessagingService;
import org.apache.cassandra.net.AsyncStreamingInputPlus;
import org.apache.cassandra.net.TestChannel;
import org.apache.cassandra.schema.TableId;
import org.apache.cassandra.streaming.PreviewKind;
import org.apache.cassandra.streaming.StreamDeserializingTask;
import org.apache.cassandra.streaming.StreamManager;
import org.apache.cassandra.streaming.StreamOperation;
import org.apache.cassandra.streaming.StreamResultFuture;
import org.apache.cassandra.streaming.StreamSession;
import org.apache.cassandra.streaming.StreamingChannel;
import org.apache.cassandra.streaming.messages.CompleteMessage;
import org.apache.cassandra.streaming.messages.IncomingStreamMessage;
import org.apache.cassandra.streaming.messages.StreamInitMessage;
import org.apache.cassandra.streaming.messages.StreamMessageHeader;

import static org.apache.cassandra.net.TestChannel.REMOTE_ADDR;

public class StreamingInboundHandlerTest
{
    private static final int VERSION = MessagingService.current_version;

    private NettyStreamingChannel streamingChannel;
    private EmbeddedChannel channel;
    private ByteBuf buf;

    @BeforeClass
    public static void before()
    {
        DatabaseDescriptor.daemonInitialization();
    }

    @Before
    public void setup()
    {
        channel = new TestChannel();
        streamingChannel = new NettyStreamingChannel(VERSION, channel, StreamingChannel.Kind.CONTROL);
        channel.pipeline().addLast("stream", streamingChannel);
    }

    @After
    public void tearDown()
    {
        if (buf != null)
        {
            while (buf.refCnt() > 0)
                buf.release();
        }

        channel.close();
    }

    @Test
    public void channelRead_WrongObject()
    {
        channel.writeInbound("homer");
        Assert.assertEquals(0, streamingChannel.in.unsafeAvailable());
        Assert.assertFalse(channel.releaseInbound());
    }

    @Test
    public void StreamDeserializingTask_deriveSession_StreamInitMessage()
    {
        StreamInitMessage msg = new StreamInitMessage(REMOTE_ADDR, 0, UUID.randomUUID(), StreamOperation.REPAIR, UUID.randomUUID(), PreviewKind.ALL);
        StreamDeserializingTask task = new StreamDeserializingTask(null, streamingChannel, streamingChannel.messagingVersion);
        StreamSession session = task.deriveSession(msg);
        Assert.assertNotNull(session);
    }

    @Test (expected = UnsupportedOperationException.class)
    public void StreamDeserializingTask_deriveSession_NoSession()
    {
        CompleteMessage msg = new CompleteMessage();
        StreamDeserializingTask task = new StreamDeserializingTask(null, streamingChannel, streamingChannel.messagingVersion);
        task.deriveSession(msg);
    }

    @Test (expected = IllegalStateException.class)
    public void StreamDeserializingTask_deserialize_ISM_NoSession() throws IOException
    {
        StreamMessageHeader header = new StreamMessageHeader(TableId.generate(), REMOTE_ADDR, UUID.randomUUID(), true,
                                                             0, 0, 0, UUID.randomUUID());

        ByteBuffer temp = ByteBuffer.allocate(1024);
        DataOutputPlus out = new DataOutputBuffer(temp);
        StreamMessageHeader.serializer.serialize(header, out, MessagingService.current_version);

        temp.flip();
        DataInputPlus in = new DataInputBuffer(temp, false);
        // session not found
        IncomingStreamMessage.serializer.deserialize(in, MessagingService.current_version);
    }

    @Test
    public void StreamDeserializingTask_deserialize_ISM_HasSession()
    {
        UUID planId = UUID.randomUUID();
        StreamResultFuture future = StreamResultFuture.createFollower(0, planId, StreamOperation.REPAIR, REMOTE_ADDR, streamingChannel, streamingChannel.messagingVersion, UUID.randomUUID(), PreviewKind.ALL);
        StreamManager.instance.registerFollower(future);
        StreamMessageHeader header = new StreamMessageHeader(TableId.generate(), REMOTE_ADDR, planId, false,
                                                             0, 0, 0, UUID.randomUUID());

        // IncomingStreamMessage.serializer.deserialize
        StreamSession session = StreamManager.instance.findSession(header.sender, header.planId, header.sessionIndex, header.sendByFollower);
        Assert.assertNotNull(session);

        session = StreamManager.instance.findSession(header.sender, header.planId, header.sessionIndex, !header.sendByFollower);
        Assert.assertNull(session);
    }
}
