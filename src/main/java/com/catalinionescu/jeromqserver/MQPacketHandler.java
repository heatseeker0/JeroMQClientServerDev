package com.catalinionescu.jeromqserver;

import com.catalinionescu.jeromqserver.packets.MQPacket;

public abstract class MQPacketHandler {
    private MQServer server;

    public MQPacketHandler(MQServer server) {
        this.server = server;
    }

    protected MQServer getServer() {
        return server;
    }

    public abstract void handle(MQPacket request);
}
