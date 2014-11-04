package com.catalinionescu.jeromqserver.packets;

public class MQPacketHeartbeatResponse extends MQPacket {
    private static final long serialVersionUID = 1L;

    public MQPacketHeartbeatResponse(long packetId) {
        super(packetId);
    }

    @Override
    public String toString() {
        return String.format("[%d] Pong", getPacketId());
    }

}
