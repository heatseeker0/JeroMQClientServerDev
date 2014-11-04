package com.catalinionescu.jeromqserver.packets;

import java.io.Serializable;

public abstract class MQPacket implements Serializable {
    private static final long serialVersionUID = 1L;
    private final long packetId;

    public MQPacket(long packetId) {
        this.packetId = packetId;
    }

    public long getPacketId() {
        return packetId;
    }

    @Override
    /**
     * Force packet classes to implement a valid toString instead of Java default one.
     */
    public abstract String toString();
}
