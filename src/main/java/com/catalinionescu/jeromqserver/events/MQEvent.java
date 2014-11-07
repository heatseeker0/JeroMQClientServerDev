package com.catalinionescu.jeromqserver.events;

import com.catalinionescu.jeromqserver.MQClient;

/**
 * Base class for all client side events.
 * 
 * @author Catalin Ionescu
 * 
 */
public abstract class MQEvent {
    private final MQClient client;

    public MQEvent(MQClient client) {
        this.client = client;
    }

    /**
     * Retrieves the client associated with this event.
     * 
     * @return Client associated with this event
     */
    public MQClient getClient() {
        return client;
    }
}
