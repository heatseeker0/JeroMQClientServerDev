package com.catalinionescu.jeromqserver.events;

import com.catalinionescu.jeromqserver.MQClient;

/**
 * Event fired when server connection drops
 * 
 * @author Catalin Ionescu
 * 
 */
public class MQEventClientDisconnect extends MQEvent {

    public MQEventClientDisconnect(MQClient client) {
        super(client);
    }

}
