package com.catalinionescu.jeromqserver.packets;

public class MQPacketHeartbeatRequest extends MQPacket {
    private static final long serialVersionUID = 1L;

    public MQPacketHeartbeatRequest(long packetId) {
        super(packetId);
    }

    @Override
    public String toString() {
        return String.format("[%d] Ping", getPacketId());
    }

}
