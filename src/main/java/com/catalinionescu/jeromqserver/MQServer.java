package com.catalinionescu.jeromqserver;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import com.catalinionescu.jeromqserver.packets.MQPacket;
import com.catalinionescu.jeromqserver.util.SerializationUtils;

public class MQServer {
    private final String ip;
    private final int port;
    private ServerTask serverTask;
    private Thread serverThread;
    private final Queue<MQPacket> recvQueue = new ConcurrentLinkedQueue<>();
    private final Queue<MQPacket> sendQueue = new ConcurrentLinkedQueue<>();
    private final Map<Class<? extends MQPacket>, List<MQPacketHandler>> handlers = new HashMap<>();

    public MQServer(final String ip, final int port) {
        this.ip = ip;
        this.port = port;
    }

    public MQServer(final int port) {
        this("*", port);
    }

    public void start() {
        serverTask = new ServerTask();
        serverThread = new Thread(serverTask);
        serverThread.start();
    }

    public void stop() {
        serverTask.terminate();
    }

    public void sendPacket(MQPacket packet) {
        sendQueue.add(packet);
    }

    /**
     * Calls the packet handlers for any packet in the receive queue.
     */
    public void runHandlers() {
        while (!recvQueue.isEmpty()) {
            MQPacket packet = recvQueue.remove();
            for (Class<? extends MQPacket> clazz : handlers.keySet()) {
                if (packet.getClass().equals(clazz)) {
                    for (MQPacketHandler handler : handlers.get(clazz)) {
                        handler.handle(packet);
                    }
                }
            }
        }
    }

    public boolean registerHandler(Class<? extends MQPacket> clazz, MQPacketHandler handler) {
        if (!handlers.containsKey(clazz)) {
            handlers.put(clazz, new LinkedList<MQPacketHandler>());
        }
        return handlers.get(clazz).add(handler);
    }

    public void clearHandlers(Class<? extends MQPacket> clazz) {
        handlers.remove(clazz);
    }

    public void clearHandlers() {
        handlers.clear();
    }

    private class ServerTask implements Runnable {
        private volatile boolean running = true;

        public void terminate() {
            running = false;
        }

        @SuppressWarnings("resource")
        @Override
        public void run() {
            ZContext context = new ZContext();
            Socket socket = context.createSocket(ZMQ.DEALER);
            socket.bind(String.format("tcp://%s:%d", ip, port));

            MQPacket packet = null;
            while (running) {
                try {
                    byte[] msg = socket.recv(ZMQ.DONTWAIT);
                    if (msg != null) {
                        packet = (MQPacket) SerializationUtils.deserialize(msg);
                        if (packet != null) {
                            recvQueue.add(packet);
                        }
                    }
                    while (!sendQueue.isEmpty()) {
                        socket.send(SerializationUtils.serialize(sendQueue.remove()));
                    }
                } catch (Exception e) {
                    // Silence any shutdown warnings about already closed contexts and terminate
                    running = false;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // Silence is golden
                    running = false;
                }
            }
            context.destroy();
            System.out.println("server_worker terminated");
        }
    }
}
