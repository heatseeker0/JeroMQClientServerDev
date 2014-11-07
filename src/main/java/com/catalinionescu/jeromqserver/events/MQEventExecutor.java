package com.catalinionescu.jeromqserver.events;

import com.catalinionescu.jeromqserver.MQClient;

/**
 * Executor for a single event type. Executors are dynamically created by {@link MQClient#registerHandlers}, one for each implemented event handler.
 * 
 * @author Catalin Ionescu
 * 
 */
public interface MQEventExecutor {
    public void execute(MQEvent event);
}
