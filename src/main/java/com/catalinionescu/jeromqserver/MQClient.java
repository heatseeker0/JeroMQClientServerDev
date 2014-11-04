package com.catalinionescu.jeromqserver;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import com.catalinionescu.jeromqserver.packets.MQPacket;
import com.catalinionescu.jeromqserver.packets.MQPacketHeartbeatRequest;
import com.catalinionescu.jeromqserver.packets.MQPacketHeartbeatResponse;
import com.catalinionescu.jeromqserver.util.SerializationUtils;

public class MQClient {
    private final String host;
    private final int port;
    private ClientTask clientTask;
    private Thread clientThread;
    private Integer missedHeartbeats = 0;

    final long HEARTBEAT_DELAY = 1000;
    final int INITIAL_MAX_MISSED_HEARTBEATS = 10;

    public MQClient(final String host, final int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        clientTask = new ClientTask();
        clientThread = new Thread(clientTask);
        clientThread.start();
    }

    public void stop() {
        clientTask.terminate();
    }

    public int getMissedHeartbeats() {
        synchronized (missedHeartbeats) {
            return missedHeartbeats;
        }
    }

    private class ClientTask implements Runnable {
        private volatile boolean running = true;
        private volatile int maxMissedHeartbeats = INITIAL_MAX_MISSED_HEARTBEATS;

        public void terminate() {
            running = false;
        }

        private Socket getClientSocket(ZContext context) {
            Socket socket = context.createSocket(ZMQ.DEALER);
            socket.connect(String.format("tcp://%s:%d", host, port));
            socket.setLinger(1000);

            return socket;
        }

        @SuppressWarnings("resource")
        @Override
        public void run() {
            ZContext context = new ZContext();
            Socket socket = getClientSocket(context);
            /**
             * Set to true if we missed a large number of heartbeats and we attempted at least 1 reconnect.
             */
            boolean reconnected = false;

            int request = 0;
            long currentTime = System.currentTimeMillis();
            long lastHeartbeatSent = currentTime + HEARTBEAT_DELAY;
            MQPacket packet = null;
            while (running) {
                // Receive any messages sent by provider
                byte[] msg = socket.recv(ZMQ.DONTWAIT);
                if (msg != null) {
                    try {
                        packet = (MQPacket) SerializationUtils.deserialize(msg);
                        if (packet instanceof MQPacketHeartbeatResponse) {
                            synchronized (missedHeartbeats) {
                                if (reconnected) {
                                    missedHeartbeats = 0;
                                } else if (missedHeartbeats > 0) {
                                    missedHeartbeats--;
                                }
                            }
                            if (reconnected) {
                                maxMissedHeartbeats = INITIAL_MAX_MISSED_HEARTBEATS;
                                System.out.println("Client: Reestablished communication with server.");
                                reconnected = false;
                            }
                        } else {
                            System.out.println("Client response: " + (packet != null ? packet.toString() : "null"));
                        }
                    } catch (ClassNotFoundException | IllegalArgumentException e) {
                        /*
                         * IllegalArgumentException is never thrown in this since we're making sure msg is not null.
                         * ClassNotFoundException is a programming bug since both client and server should use same packet API.
                         */
                        e.printStackTrace();
                    }
                }

                // Periodically send a heartbeat message
                currentTime = System.currentTimeMillis();
                if (currentTime > lastHeartbeatSent) {
                    lastHeartbeatSent = currentTime + HEARTBEAT_DELAY;
                    packet = new MQPacketHeartbeatRequest(request++);
                    socket.send(SerializationUtils.serialize(packet));
                    synchronized (missedHeartbeats) {
                        missedHeartbeats++;
                    }
                }
                // Check we're still connected and reconnect if needed
                int localMissedHeartbeats = 0;
                synchronized (missedHeartbeats) {
                    localMissedHeartbeats = missedHeartbeats;
                }
                if (localMissedHeartbeats > maxMissedHeartbeats) {
                    System.out.println(String.format("Client: Missed %d heartbeats with server. Reconnecting...", maxMissedHeartbeats));
                    context.destroySocket(socket);
                    socket = getClientSocket(context);
                    // Increase the max number of failures
                    maxMissedHeartbeats *= 2;
                    synchronized (missedHeartbeats) {
                        missedHeartbeats = 0;
                    }
                    reconnected = true;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // silence is golden
                    running = false;
                }
            }
            context.destroy();
            System.out.println("client_task terminated");
        }
    }
}
