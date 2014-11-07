package com.catalinionescu.jeromqserver.events;

import com.catalinionescu.jeromqserver.MQClient;

public class MQEventSendHeartbeat extends MQEvent {

    public MQEventSendHeartbeat(MQClient client) {
        super(client);
    }

}
