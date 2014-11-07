package com.catalinionescu.jeromqserver.events;

import com.catalinionescu.jeromqserver.MQClient;

public class MQEventClientConnect extends MQEvent {

    public MQEventClientConnect(MQClient client) {
        super(client);
    }

}
